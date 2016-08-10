package org.camunda.tngp.broker.wf.runtime.log.handler;

import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.taskqueue.TaskInstanceReader;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.log.ActivityInstanceRequestWriter;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;

public class InputTaskHandler implements LogEntryTypeHandler<TaskInstanceReader>
{

    protected ResourceContextProvider<WfRuntimeContext> wfRuntimeContextProvider;

    protected ActivityInstanceRequestWriter logRequestWriter = new ActivityInstanceRequestWriter();

    public InputTaskHandler(ResourceContextProvider<WfRuntimeContext> wfRuntimeContextProvider)
    {
        this.wfRuntimeContextProvider = wfRuntimeContextProvider;
    }

    @Override
    public void handle(TaskInstanceReader reader, ResponseControl responseControl)
    {
        if (reader.state() == TaskInstanceState.COMPLETED && reader.wfActivityInstanceEventKey() != TaskInstanceDecoder.wfActivityInstanceEventKeyNullValue())
        {
            final int wfRuntimeLogId = reader.wfRuntimeResourceId();

            final WfRuntimeContext wfRuntimeContext = wfRuntimeContextProvider.getContextForResource(wfRuntimeLogId);

            final LogWriter logWriter = wfRuntimeContext.getLogWriter();

            logRequestWriter
                .source(EventSource.EXTERNAL_LOG)
                .key(reader.wfActivityInstanceEventKey());

            logWriter.write(logRequestWriter);
        }

    }

}
