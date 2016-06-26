package org.camunda.tngp.protocol.error;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.util.buffer.BufferWriter;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public class ErrorWriter implements BufferWriter
{
    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected ErrorEncoder bodyEncoder = new ErrorEncoder();

    protected int componentCode;
    protected int detailCode;
    protected byte[] errorMessage;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                ErrorEncoder.BLOCK_LENGTH +
                ErrorEncoder.errorMessageHeaderLength() +
                errorMessage.length;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset);
        headerEncoder
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .componentCode(componentCode)
            .detailCode(detailCode)
            .putErrorMessage(errorMessage, 0, errorMessage.length);

    }

    public ErrorWriter errorMessage(String message)
    {
        this.errorMessage = message.getBytes(StandardCharsets.UTF_8);
        return this;
    }

    public ErrorWriter componentCode(int componentCode)
    {
        this.componentCode = componentCode;
        return this;
    }

    public ErrorWriter detailCode(int detailCode)
    {
        this.detailCode = detailCode;
        return this;
    }
}
