package org.camunda.tngp.broker.clustering.raft.message;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.clustering.raft.ConfigureResponseDecoder;
import org.camunda.tngp.clustering.raft.ConfigureResponseEncoder;
import org.camunda.tngp.clustering.raft.MessageHeaderDecoder;
import org.camunda.tngp.clustering.raft.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.BufferReader;
import org.camunda.tngp.util.buffer.BufferWriter;

public class ConfigureResponse implements BufferReader, BufferWriter
{
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final ConfigureResponseEncoder bodyEncoder = new ConfigureResponseEncoder();

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final ConfigureResponseDecoder bodyDecoder = new ConfigureResponseDecoder();

    private int id;
    private int term;

    public int id()
    {
        return id;
    }

    public ConfigureResponse id(final int id)
    {
        this.id = id;
        return this;
    }

    public int term()
    {
        return term;
    }

    public ConfigureResponse term(final int term)
    {
        this.term = term;
        return this;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
                bodyEncoder.sbeBlockLength();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .id(id)
            .term(term);
    }

    @Override
    public void wrap(DirectBuffer buffer, int offset, int length)
    {
        headerDecoder.wrap(buffer, offset);

        offset += headerDecoder.encodedLength();

        bodyDecoder.wrap(buffer, offset, headerDecoder.blockLength(), headerDecoder.version());
        id = bodyDecoder.id();
        term = bodyDecoder.term();
    }

    public void reset()
    {
        id = -1;
        term = -1;
    }

}
