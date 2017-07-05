/*
 * Copyright 2012 - 2016 Splice Machine, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.splicemachine.hbase;

import com.google.protobuf.Service;
import com.splicemachine.access.HConfiguration;
import com.splicemachine.backup.BackupRestoreConstants;
import com.splicemachine.coprocessor.SpliceMessage;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.Coprocessor;
import org.apache.hadoop.hbase.CoprocessorEnvironment;
import org.apache.hadoop.hbase.coprocessor.CoprocessorService;
import org.apache.hadoop.hbase.coprocessor.ObserverContext;
import org.apache.hadoop.hbase.coprocessor.RegionCoprocessorEnvironment;
import org.apache.hadoop.hbase.regionserver.*;
import org.apache.hadoop.hbase.util.FSUtils;
import org.apache.log4j.Logger;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by jyuan on 2/18/16.
 */
public class BackupEndpointObserver extends BackupBaseRegionObserver implements CoprocessorService,Coprocessor {
    private static final Logger LOG=Logger.getLogger(BackupEndpointObserver.class);

    private AtomicBoolean isSplitting;
    private AtomicBoolean isFlushing;
    private AtomicBoolean isCompacting;
    private HRegion region;
    private String namespace;
    private String tableName;
    private String regionName;
    private String path;
    private Path backupDir;
    private Configuration conf;
    private FileSystem fs;
    Path rootDir;
    private AtomicBoolean preparing;

    @Override
    public void start(CoprocessorEnvironment e) throws IOException {
        region = (HRegion)((RegionCoprocessorEnvironment) e).getRegion();
        String[] name = region.getTableDesc().getNameAsString().split(":");
        if (name.length == 2) {
            namespace = name[0];
            tableName = name[1];
        }
        else {
            tableName = name[0];
        }
        regionName = region.getRegionInfo().getEncodedName();

        path = HConfiguration.getConfiguration().getBackupPath() + "/" + tableName + "/" + regionName;
        conf = HConfiguration.unwrapDelegate();
        rootDir = FSUtils.getRootDir(conf);
        fs = FSUtils.getCurrentFileSystem(conf);
        backupDir = new Path(rootDir, BackupRestoreConstants.BACKUP_DIR + "/data/splice/" + tableName + "/" + regionName);
        preparing = new AtomicBoolean(false);
        isFlushing = new AtomicBoolean(false);
        isCompacting = new AtomicBoolean(false);
        isSplitting = new AtomicBoolean(false);
    }

    @Override
    public Service getService(){
        return this;
    }


    @Override
    public void prepareBackup(
            com.google.protobuf.RpcController controller,
            SpliceMessage.PrepareBackupRequest request,
            com.google.protobuf.RpcCallback<SpliceMessage.PrepareBackupResponse> done) {
        try {
            preparing.set(true);
            SpliceMessage.PrepareBackupResponse.Builder responseBuilder =
                    prepare(request);

            assert responseBuilder.hasReadyForBackup();
            done.run(responseBuilder.build());
        } catch (Exception e) {
            controller.setFailed(e.getMessage());
        } finally {
            preparing.set(false);
        }

    }

    public SpliceMessage.PrepareBackupResponse.Builder prepare(SpliceMessage.PrepareBackupRequest request) throws Exception{

        SpliceMessage.PrepareBackupResponse.Builder responseBuilder = SpliceMessage.PrepareBackupResponse.newBuilder();
        responseBuilder.setReadyForBackup(false);

        if (BackupUtils.shouldIgnore(request, region)) {
            return responseBuilder;
        }

        boolean canceled = false;

        if (isSplitting.get() || isFlushing.get() || isCompacting.get()) {
            if (LOG.isDebugEnabled()) {
                SpliceLogUtils.debug(LOG, "%s:%s is being split before trying to prepare for backup", tableName, regionName);
            }
            // return false to client if the region is being split
            responseBuilder.setReadyForBackup(false);
        } else {
            if (LOG.isDebugEnabled()) {
                SpliceLogUtils.debug(LOG, "%s:%s waits for flush and compaction to complete", tableName, regionName);
            }

            // A region might have been in backup. This is unlikely to happen unless the previous response ws lost
            // and the client is retrying
            if (!BackupUtils.regionIsBeingBackup(tableName, regionName, path)) {
                // Flush memsotore and Wait for flush and compaction to be done
                HBasePlatformUtils.flush(region);
                region.waitForFlushesAndCompactions();

                canceled = BackupUtils.backupCanceled();
                if (!canceled) {
                    // Create a ZNode to indicate that the region is being copied
                    ZkUtils.recursiveSafeCreate(path, HConfiguration.BACKUP_IN_PROGRESS, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                    if (LOG.isDebugEnabled()) {
                        SpliceLogUtils.debug(LOG,"create node %s to mark backup in progress", path);
                    }

                    if (isFlushing.get() || isCompacting.get() || isSplitting.get()) {
                        if (LOG.isDebugEnabled()) {
                            SpliceLogUtils.debug(LOG, "table %s region %s is not ready for backup: isSplitting=%s, isCompacting=%s, isFlushing=%s",
                                    isSplitting.get(), isCompacting.get(), isFlushing.get());
                        }
                        ZkUtils.recursiveDelete(path);
                    }
                    else {
                        responseBuilder.setReadyForBackup(true);
                        if (LOG.isDebugEnabled()) {
                            SpliceLogUtils.debug(LOG, "%s:%s is ready for backup", tableName, regionName);
                        }
                    }
                }
            }
            else
                responseBuilder.setReadyForBackup(true);
        }
        return responseBuilder;
    }

    @Override
    public void preSplit(ObserverContext<RegionCoprocessorEnvironment> e) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "BackupEndpointObserver.preSplit(): %s", regionName);

        BackupUtils.waitForBackupToComplete(tableName, regionName, path);
        isSplitting.set(true);
        super.preSplit(e);
    }

    @Override
    public void postRollBackSplit(ObserverContext<RegionCoprocessorEnvironment> ctx) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "BackupEndpointObserver.postRollBackSplit(): %s", regionName);
        super.postRollBackSplit(ctx);
        isSplitting.set(false);

    }

    @Override
    public void postCompleteSplit(ObserverContext<RegionCoprocessorEnvironment> ctx) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "BackupEndpointObserver.postCompleteSplit(): %s", regionName);
        super.postCompleteSplit(ctx);
        isSplitting.set(false);
    }

    @Override
    public InternalScanner preCompact(ObserverContext<RegionCoprocessorEnvironment> e, Store store, InternalScanner scanner, ScanType scanType) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "BackupEndpointObserver.preCompact()");

        BackupUtils.waitForBackupToComplete(tableName, regionName, path);
        isCompacting.set(true);
        return super.preCompact(e, store, scanner, scanType);
    }

    @Override
    public void postCompact(ObserverContext<RegionCoprocessorEnvironment> e, Store store, StoreFile resultFile) throws IOException {
        super.postCompact(e, store, resultFile);
        isCompacting.set(false);
        if (LOG.isDebugEnabled()) {
            String filePath =  resultFile != null?resultFile.getFileInfo().getFileStatus().getPath().toString():null;
            SpliceLogUtils.debug(LOG, "Compaction result file %s", filePath);
        }
    }

    @Override
    public void preFlush(ObserverContext<RegionCoprocessorEnvironment> e) throws IOException {
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "BackupEndpointObserver.preFlush(): %s", regionName);
        if (!BackupUtils.isSpliceTable(namespace, tableName))
            return;
        BackupUtils.waitForBackupToComplete(tableName, regionName, path);
        isFlushing.set(true); // Mark beginning of flush
        super.preFlush(e);
    }

    @Override
    public void postFlush(ObserverContext<RegionCoprocessorEnvironment> e, Store store, StoreFile resultFile) throws IOException {
        // Register HFiles for incremental backup
        String filePath =  resultFile != null?resultFile.getFileInfo().getFileStatus().getPath().toString():null;
        SpliceLogUtils.info(LOG, "Flushing store file %s", filePath);
        try {
            if (!BackupUtils.isSpliceTable(namespace, tableName))
                return;
            BackupUtils.captureIncrementalChanges(conf, region, path, fs, rootDir, backupDir,
                    tableName, resultFile.getPath().getName(), preparing.get());
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    @Override
    public void postFlush(ObserverContext<RegionCoprocessorEnvironment> e) throws IOException {
        SpliceLogUtils.info(LOG, "Flushing region %s.%s", tableName, regionName);
        if (!BackupUtils.isSpliceTable(namespace, tableName))
            return;
        super.postFlush(e);
        isFlushing.set(false); // end of flush
    }

    @Override
    public void postSplit(ObserverContext<RegionCoprocessorEnvironment> e ,Region l, Region r) throws IOException{
    }
}
