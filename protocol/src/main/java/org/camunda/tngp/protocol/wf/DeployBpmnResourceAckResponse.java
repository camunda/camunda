package org.camunda.tngp.protocol.wf;

import org.camunda.tngp.protocol.wf.repository.DeployBpmnResourceAckEncoder;
import org.camunda.tngp.protocol.wf.repository.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class DeployBpmnResourceAckResponse implements BufferWriter
{
    protected final DeployBpmnResourceAckEncoder encoder = new DeployBpmnResourceAckEncoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();

    private static final int ENCODED_LENGTH = MessageHeaderEncoder.ENCODED_LENGTH + DeployBpmnResourceAckEncoder.BLOCK_LENGTH;

    protected long wfTypeId;

    @Override
    public int getLength()
    {
        return ENCODED_LENGTH;
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
