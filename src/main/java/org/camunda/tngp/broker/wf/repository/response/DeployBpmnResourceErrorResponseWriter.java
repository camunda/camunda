package org.camunda.tngp.broker.wf.repository.response;

import org.camunda.tngp.dispatcher.FragmentWriter;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceNackEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class DeployBpmnResourceErrorResponseWriter implements FragmentWriter
{
    protected final DeployBpmnResourceNackEncoder encoder = new DeployBpmnResourceNackEncoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    protected byte[] errorMessage;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH
                + DeployBpmnResourceNackEncoder.BLOCK_LENGTH
                + DeployBpmnResourceNackEncoder.errorMessageHeaderLength()
                + errorMessage.length;
    }

    @Override
    public void write(final MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(encoder.sbeBlockLength())
            .templateId(encoder.sbeTemplateId())
            .schemaId(encoder.sbeSchemaId())
            .version(encoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        encoder.wrap(buffer, offset)
            .putErrorMessage(errorMessage, 0, errorMessage.length);
    }

    public DeployBpmnResourceErrorResponseWriter errorMessage(byte[] errorMessageBytes)
    {
        this.errorMessage = errorMessageBytes;
        return this;
    }

}
