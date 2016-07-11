package org.camunda.tngp.broker.wf;

import org.camunda.tngp.broker.log.LogEntryHandler;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceReader;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

public class WorkflowTaskEventHandler implements LogEntryHandler<TaskInstanceReader>
{

    protected final ResourceContextProvider<WfRuntimeContext> wfRuntimeContextProvider;

    public WorkflowTaskEventHandler(ResourceContextProvider<WfRuntimeContext> wfRuntimeContextProvider)
    {
        this.wfRuntimeContextProvider = wfRuntimeContextProvider;
    }

    @Override
    public void handle(long position, TaskInstanceReader reader)
    {
        if (TaskInstanceState.COMPLETED == reader.state())
        {
            final int wfRuntimeResourceId = reader.wfRuntimeResourceId();

            final WfRuntimeContext wfRuntimeContext = wfRuntimeContextProvider.getContextForResource(wfRuntimeResourceId);
            // TODO: runtime context with id may not exist

            wfRuntimeContext.getTaskEventHandler().onComplete(reader);
        }

    }
}
