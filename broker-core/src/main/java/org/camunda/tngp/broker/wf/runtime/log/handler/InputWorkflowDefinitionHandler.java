package org.camunda.tngp.broker.wf.runtime.log.handler;

import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.transport.worker.spi.ResourceContextProvider;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.broker.wf.runtime.WfRuntimeContext;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionRuntimeRequestWriter;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;

import uk.co.real_logic.agrona.DirectBuffer;

public class InputWorkflowDefinitionHandler implements LogEntryTypeHandler<WfDefinitionReader>
{

    protected ResourceContextProvider<WfRuntimeContext> wfRuntimeContextProvider;

    protected WfDefinitionRuntimeRequestWriter logRequestWriter = new WfDefinitionRuntimeRequestWriter();

    public InputWorkflowDefinitionHandler(ResourceContextProvider<WfRuntimeContext> wfRuntimeContextProvider)
    {
        this.wfRuntimeContextProvider = wfRuntimeContextProvider;
    }

    @Override
    public void handle(WfDefinitionReader reader, ResponseControl responseControl)
    {
        final WfRuntimeContext[] wfRuntimeContexts = wfRuntimeContextProvider.getContexts();

        final DirectBuffer keyBuffer = reader.getTypeKey();
        final DirectBuffer resourceBuffer = reader.getResource();

        logRequestWriter
            .id(reader.id())
            .type(WfDefinitionRequestType.NEW)
            .key(keyBuffer, 0, keyBuffer.capacity())
            .resource(resourceBuffer, 0, resourceBuffer.capacity());

        for (int i = 0; i < wfRuntimeContexts.length; i++)
        {
            final LogWriter logWriter = wfRuntimeContexts[i].getLogWriter();
            logWriter.write(logRequestWriter);
        }

    }

}
