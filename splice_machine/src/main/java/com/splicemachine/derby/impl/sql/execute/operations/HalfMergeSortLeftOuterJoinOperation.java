package com.splicemachine.derby.impl.sql.execute.operations;

import com.splicemachine.db.iapi.error.StandardException;
import com.splicemachine.db.iapi.services.loader.GeneratedMethod;
import com.splicemachine.db.iapi.sql.Activation;
import com.splicemachine.db.iapi.sql.execute.ExecRow;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperation;
import com.splicemachine.derby.iapi.sql.execute.SpliceOperationContext;
import com.splicemachine.derby.impl.SpliceMethod;
import com.splicemachine.pipeline.Exceptions;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * @author P Trolard
 *         Date: 10/04/2014
 */
public class HalfMergeSortLeftOuterJoinOperation extends HalfMergeSortJoinOperation {
    private static Logger LOG = Logger.getLogger(HalfMergeSortLeftOuterJoinOperation.class);

    protected SpliceMethod<ExecRow> emptyRowFun;
    protected ExecRow emptyRow;

    {
        isOuterJoin = true;
    }

    public HalfMergeSortLeftOuterJoinOperation() {
        super();
    }

    public HalfMergeSortLeftOuterJoinOperation(SpliceOperation leftResultSet,
                                               int leftNumCols,
                                               SpliceOperation rightResultSet,
                                               int rightNumCols,
                                               int leftHashKeyItem,
                                               int rightHashKeyItem,
                                               Activation activation,
                                               GeneratedMethod restriction,
                                               int resultSetNumber,
                                               GeneratedMethod emptyRowFun,
                                               boolean wasRightOuterJoin,
                                               boolean oneRowRightSide,
                                               boolean notExistsRightSide,
                                               double optimizerEstimatedRowCount,
                                               double optimizerEstimatedCost,
                                               String userSuppliedOptimizerOverrides) throws StandardException {
        super(leftResultSet, leftNumCols, rightResultSet, rightNumCols, leftHashKeyItem, rightHashKeyItem,
                activation, restriction, resultSetNumber, oneRowRightSide, notExistsRightSide,
                optimizerEstimatedRowCount, optimizerEstimatedCost, userSuppliedOptimizerOverrides);
        SpliceLogUtils.trace(LOG, "instantiate");
        emptyRowFunMethodName = (emptyRowFun == null) ? null : emptyRowFun.getMethodName();
        this.wasRightOuterJoin = wasRightOuterJoin;
        init();
    }

    @Override
    public void init(SpliceOperationContext context) throws StandardException, IOException {
        super.init(context);
        emptyRowFun = (emptyRowFunMethodName == null) ? null :
                new SpliceMethod<ExecRow>(emptyRowFunMethodName, activation);
    }

    @Override
    public ExecRow getEmptyRow() throws StandardException {
        if (emptyRow == null) {
            emptyRow = emptyRowFun.invoke();
        }
        return emptyRow;
    }

    @Override
    public String prettyPrint(int indentLevel) {
        return "LeftOuter" + super.prettyPrint(indentLevel);
    }


}
