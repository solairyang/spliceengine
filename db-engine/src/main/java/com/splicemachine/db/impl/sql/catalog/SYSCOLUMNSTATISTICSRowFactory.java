/*
 * Apache Derby is a subproject of the Apache DB project, and is licensed under
 * the Apache License, Version 2.0 (the "License"); you may not use these files
 * except in compliance with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * Splice Machine, Inc. has modified this file.
 *
 * All Splice Machine modifications are Copyright 2012 - 2016 Splice Machine, Inc.,
 * and are licensed to you under the License; you may not use this file except in
 * compliance with the License.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 */

package com.splicemachine.db.impl.sql.catalog;

import com.splicemachine.db.catalog.UUID;
import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.uuid.UUIDFactory;
import com.splicemachine.db.iapi.sql.dictionary.*;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.db.iapi.sql.execute.ExecutionFactory;
import com.splicemachine.db.iapi.stats.ColumnStatisticsImpl;
import com.splicemachine.db.iapi.stats.ItemStatistics;
import com.splicemachine.db.iapi.types.*;
import com.splicemachine.db.impl.sql.execute.ValueRow;
import java.sql.Types;

/**
 *
 * Row Factory for column level statistics.  Column 0 represents
 * the key (ExecRow) and Column 1 - N represent the columns in the table (DataValueDescriptor).
 *
 * @author Scott Fines
 *         Date: 2/25/15
 */
public class SYSCOLUMNSTATISTICSRowFactory extends CatalogRowFactory {
    public static final String TABLENAME_STRING = "SYSCOLUMNSTATS";
    public static final int SYSCOLUMNSTATISTICS_COLUMN_COUNT = 4;
    public static final int CONGLOMID      = 1;
    public static final int PARTITIONID    = 2;
    public static final int COLUMNID       = 3;
    public static final int DATA           = 4;

    protected static final int SYSCOLUMNSTATISTICS_INDEX1_ID = 0;
    protected static final int SYSCOLUMNSTATISTICS_INDEX2_ID = 1;
    protected static final int SYSCOLUMNSTATISTICS_INDEX3_ID = 2;

    private	static	final	boolean[]	uniqueness = {
            true,
            false,
            false
    };

    private String[] uuids = {
            "08264012-014b-c29c-0d2e-000003009390",
            "0826401a-014b-c29c-0d2e-000003009390",
            "08264014-014b-c29c-0d2e-000003009390",
            "08264016-014b-c29c-0d2e-000003009390",
            "08264018-014b-c29c-0d2e-000003009390",
    };

    private static final int[][] indexColumnPositions = {
                    {CONGLOMID,PARTITIONID,COLUMNID},
                    {CONGLOMID,PARTITIONID},
                    {CONGLOMID}
    };

    public SYSCOLUMNSTATISTICSRowFactory(UUIDFactory uuidFactory, ExecutionFactory exFactory, DataValueFactory dvf) {
        super(uuidFactory,exFactory,dvf);
        initInfo(SYSCOLUMNSTATISTICS_COLUMN_COUNT,TABLENAME_STRING,indexColumnPositions,uniqueness,uuids);
    }

    @Override
    public ExecRow makeRow(TupleDescriptor td, TupleDescriptor parent) throws StandardException {
        ColumnStatisticsDescriptor cd = (ColumnStatisticsDescriptor) td;
        ExecRow row = new ValueRow(SYSCOLUMNSTATISTICS_COLUMN_COUNT);
        if (cd != null) {
            row.setColumn(CONGLOMID, new SQLLongint(cd.getConglomerateId()));
            row.setColumn(PARTITIONID, new SQLVarchar(cd.getPartitionId()));
            row.setColumn(COLUMNID, new SQLInteger(cd.getColumnId()));
            row.setColumn(DATA, new UserType(cd.getStats()));
        } else {
            row.setColumn(CONGLOMID,new SQLLongint());
            row.setColumn(PARTITIONID,new SQLVarchar());
            row.setColumn(COLUMNID,new SQLInteger());
            row.setColumn(DATA,new UserType());
        }
        return row;
    }

    @Override
    public TupleDescriptor buildDescriptor(ExecRow row, TupleDescriptor parentTuple, DataDictionary dataDictionary) throws StandardException {
        DataValueDescriptor col = row.getColumn(CONGLOMID);
        long conglomId = col.getLong();
        col = row.getColumn(PARTITIONID);
        String partitionId = col.getString();
        col = row.getColumn(COLUMNID);
        int colNum = col.getInt();
        col = row.getColumn(DATA);
        ItemStatistics colStats = (ItemStatistics)col.getObject();

        return new ColumnStatisticsDescriptor(conglomId,
                partitionId,
                colNum,
                colStats);
    }

    @Override
    public SystemColumn[] buildColumnList() throws StandardException {
        return new SystemColumn[]{
                SystemColumnImpl.getColumn("CONGLOM_ID", Types.BIGINT, false),
                SystemColumnImpl.getColumn("PARTITION_ID",Types.VARCHAR,false),
                SystemColumnImpl.getColumn("COLUMN_ID",Types.INTEGER,false),
                SystemColumnImpl.getJavaColumn("DATA",
                        ColumnStatisticsImpl.class.getCanonicalName(),false)
        };
    }

    public static ColumnDescriptor[] getViewColumns(TableDescriptor view,UUID viewId) throws StandardException{
        DataTypeDescriptor varcharType = DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.VARCHAR);
        DataTypeDescriptor longType = DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.BIGINT);
        DataTypeDescriptor floatType = DataTypeDescriptor.getBuiltInDataTypeDescriptor(Types.REAL);
        return new ColumnDescriptor[]{
                new ColumnDescriptor("SCHEMANAME"   ,1,1,varcharType,null,null,view,viewId,0,0,0),
                new ColumnDescriptor("TABLENAME"    ,2,2,varcharType,null,null,view,viewId,0,0,1),
                new ColumnDescriptor("COLUMNNAME"   ,3,3,varcharType,null,null,view,viewId,0,0,2),
                new ColumnDescriptor("CARDINALITY"  ,4,4,longType,null,null,view,viewId,0,0,3),
                new ColumnDescriptor("NULL_COUNT"   ,5,5,longType,null,null,view,viewId,0,0,4),
                new ColumnDescriptor("NULL_FRACTION",6,6,floatType,null,null,view,viewId,0,0,5),
                new ColumnDescriptor("MIN_VALUE"    ,7,7,varcharType,null,null,view,viewId,0,0,6),
                new ColumnDescriptor("MAX_VALUE"    ,8,8,varcharType,null,null,view,viewId,0,0,7),
                new ColumnDescriptor("QUANTILES"        ,9,9,varcharType,null,null,view,viewId,0,0,8),
                new ColumnDescriptor("FREQUENCIES"        ,9,9,varcharType,null,null,view,viewId,0,0,8),
                new ColumnDescriptor("THETA"        ,9,9,varcharType,null,null,view,viewId,0,0,8),

        };
    }

    public static final String STATS_VIEW_SQL = "create view syscolumnstatistics as select " +
            "s.schemaname" +
            ",t.tablename" +
            ",co.columnname" +
            ",STATS_CARDINALITY(STATS_MERGE(data)) as CARDINALITY" +
            ",STATS_NULL_COUNT(STATS_MERGE(data)) as NULL_COUNT" +
            ",STATS_NULL_FRACTION(STATS_MERGE(data)) as NULL_FRACTION" +
            ",STATS_MIN(STATS_MERGE(data)) as MIN_VALUE" +
            ",STATS_MAX(STATS_MERGE(data)) as MAX_VALUE" +
            ",STATS_QUANTILES(STATS_MERGE(data)) as QUANTILES " +
            ",STATS_FREQUENCIES(STATS_MERGE(data)) as FREQUENCIES " +
            ",STATS_THETA(STATS_MERGE(data)) as THETA " +
            "from " +
            "sys.syscolumnstats cs" +
            ",sys.sysschemas s " +
            ",sys.systables t " +
            ",sys.sysconglomerates c" +
            ",sys.syscolumns co" +
            " where " +
            "t.tableid = c.tableid " +
            "and t.schemaid = s.schemaid " +
            "and c.conglomeratenumber = cs.conglom_id " +
            "and c.isindex = false " + //strip out index column data
            "and co.referenceid = t.tableid " +
            "and co.columnnumber = cs.column_id " +
            "group by " +
            "s.schemaname" +
            ",t.tablename" +
            ",co.columnname";



    public	int	getPrimaryKeyIndexNumber() {
        return SYSCOLUMNSTATISTICS_INDEX1_ID;
    }

}
