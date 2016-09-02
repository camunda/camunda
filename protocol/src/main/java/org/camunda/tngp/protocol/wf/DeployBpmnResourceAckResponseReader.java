package org.camunda.tngp.protocol.wf;

import org.camunda.tngp.util.buffer.BufferReader;

import org.agrona.DirectBuffer;

public class DeployBpmnResourceAckResponseReader implements BufferReader
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final DeployBpmnResourceAckDecoder bodyDecoder = new DeployBpmnResourceAckDecoder();


    public long wfDefinitionId()
    {
        return bodyDecoder.wfDefinitionId();
    }

    @Override
    public void wrap(final DirectBuffer buffer, int offset, final int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());

    }

    public int getSchemaId()
    {
        return DeployBpmnResourceAckDecoder.SCHEMA_ID;
    }

    public int getTemplateId()
    {
        return DeployBpmnResourceAckDecoder.TEMPLATE_ID;
    }

}
