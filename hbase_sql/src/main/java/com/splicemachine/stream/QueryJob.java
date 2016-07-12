package com.splicemachine.stream;

import com.splicemachine.EngineDriver;
import com.splicemachine.access.HConfiguration;
import com.splicemachine.concurrent.Clock;
import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.iapi.sql.olap.OlapStatus;
import com.splicemachine.derby.impl.sql.execute.operations.LocatedRow;
import com.splicemachine.derby.jdbc.SpliceTransactionResourceImpl;
import com.splicemachine.derby.stream.ActivationHolder;
import com.splicemachine.derby.stream.iapi.DataSet;
import com.splicemachine.derby.stream.iapi.DistributedDataSetProcessor;
import com.splicemachine.derby.stream.iapi.OperationContext;
import com.splicemachine.derby.stream.spark.SparkDataSet;
import org.apache.log4j.Logger;

import java.util.UUID;
import java.util.concurrent.*;

/**
 * @author Scott Fines
 *         Date: 4/1/16
 */
public class QueryJob implements Callable<Void>{

    private static final Logger LOG = Logger.getLogger(QueryJob.class);

    private final OlapStatus status;
    private final RemoteQueryJob queryRequest;

    public QueryJob(RemoteQueryJob queryRequest,
                    OlapStatus jobStatus) {
        this.status = jobStatus;
        this.queryRequest = queryRequest;
    }

    @Override
    public Void call() throws Exception {
        if(!status.markRunning()){
            //the client has already cancelled us or has died before we could get started, so stop now
            return null;
        }

        ActivationHolder ah = queryRequest.ah;
        SpliceOperation root = ah.getOperationsMap().get(queryRequest.rootResultSetNumber);
        DistributedDataSetProcessor dsp = EngineDriver.driver().processorFactory().distributedProcessor();
        DataSet<LocatedRow> dataset;
        OperationContext<SpliceOperation> context;
        try {
            ah.reinitialize(null);
            Activation activation = ah.getActivation();
            root.setActivation(activation);
            if (!(activation.isMaterialized()))
                activation.materialize();
            long txnId = root.getCurrentTransaction().getTxnId();

            String sql = queryRequest.sql;
            String userId = queryRequest.userId;
            String jobName = userId + " <" + txnId + ">";
            dsp.setJobGroup(jobName, sql);
            dsp.clearBroadcastedOperation();
            dataset = root.getDataSet(dsp);
            context = dsp.createOperationContext(root);
        } finally {
            ah.close();
        }
        SparkDataSet<LocatedRow> sparkDataSet = (SparkDataSet<LocatedRow>) dataset;
        String clientHost = queryRequest.host;
        int clientPort = queryRequest.port;
        UUID uuid = queryRequest.uuid;
        int numPartitions = sparkDataSet.rdd.getNumPartitions();

        StreamableRDD streamableRDD = new StreamableRDD<>(sparkDataSet.rdd, context, uuid, clientHost, clientPort,
                queryRequest.streamingBatches, queryRequest.streamingBatchSize);
        streamableRDD.submit();

        status.markCompleted(new QueryResult(numPartitions));

        return null;
    }
}