package org.camunda.tngp.broker.wf.runtime;

import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.taskqueue.data.FlowElementExecutionEventDecoder;

public class ContinuationHandler
{
    protected final ExecutionEventReader executionEventReader = new ExecutionEventReader();

    protected final Log log;

    protected  LogReader logReader;

    public ContinuationHandler(WfRuntimeContext context)
    {
        this.log = context.getLog();
    }

    public void poll()
    {
//        int workCount = logReader.read(1);
//
//        if (workCount == 1)
//        {
//            handleEvent();
//        }
//
//        return workCount;
    }

    protected void handleEvent()
    {
        final FlowElementExecutionEventDecoder decoder = executionEventReader.getDecoder();
    }
}
