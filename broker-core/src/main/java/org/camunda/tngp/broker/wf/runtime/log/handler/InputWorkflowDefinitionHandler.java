package org.camunda.tngp.broker.wf.runtime.log.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.log.LogEntryTypeHandler;
import org.camunda.tngp.broker.log.LogWriters;
import org.camunda.tngp.broker.log.ResponseControl;
import org.camunda.tngp.broker.wf.repository.log.WfDefinitionReader;
import org.camunda.tngp.broker.wf.runtime.log.WfDefinitionRuntimeRequestWriter;
import org.camunda.tngp.taskqueue.data.WfDefinitionRequestType;

public class InputWorkflowDefinitionHandler implements LogEntryTypeHandler<WfDefinitionReader>
{

    protected WfDefinitionRuntimeRequestWriter logRequestWriter = new WfDefinitionRuntimeRequestWriter();

    @Override
    public void handle(WfDefinitionReader reader, ResponseControl responseControl, LogWriters logWriters)
    {
        final DirectBuffer keyBuffer = reader.getTypeKey();
        final DirectBuffer resourceBuffer = reader.getResource();

        logRequestWriter
            .id(reader.id())
            .type(WfDefinitionRequestType.NEW)
            .key(keyBuffer, 0, keyBuffer.capacity())
            .resource(resourceBuffer, 0, resourceBuffer.capacity());

        logWriters.writeToAllLogs(logRequestWriter);


    }

}
