package org.camunda.tngp.broker.wf.repository.response;

import org.camunda.tngp.dispatcher.FragmentWriter;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceAckEncoder;
import org.camunda.tngp.taskqueue.data.MessageHeaderEncoder;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class DeployBpmnResourceAckResponse implements FragmentWriter
{
    protected final DeployBpmnResourceAckEncoder encoder = new DeployBpmnResourceAckEncoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    private final static int encodedLength = MessageHeaderEncoder.ENCODED_LENGTH + DeployBpmnResourceAckEncoder.BLOCK_LENGTH;

    protected long wfTypeId;

    @Override
    public int getLength()
    {
        return encodedLength;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(encoder.sbeBlockLength())
            .templateId(encoder.sbeTemplateId())
            .schemaId(encoder.sbeSchemaId())
            .version(encoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        encoder.wrap(buffer, offset)
            .wfTypeId(wfTypeId);
    }

    public DeployBpmnResourceAckResponse wfTypeId(long value)
    {
        this.wfTypeId = value;
        return this;
    }


}
