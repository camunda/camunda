package org.camunda.tngp.broker.wf.repository.log;

import static org.assertj.core.api.Assertions.*;

import org.camunda.tngp.taskqueue.data.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionDecoder;
import org.camunda.tngp.taskqueue.data.WfDefinitionEncoder;
import org.junit.Test;

import org.agrona.concurrent.UnsafeBuffer;

public class WfDefinitionWriterTest
{

    protected static final byte[] PAYLOAD = new byte[] {0, 0, 0, 1, 2, 3, 4, 0};
    protected static final byte[] TYPE = new byte[] {5, 6};

    @Test
    public void testWriting()
    {
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);

        // given
        final WfDefinitionWriter writer = new WfDefinitionWriter()
            .id(1)
            .wfDefinitionKey(TYPE)
            .resource(new UnsafeBuffer(PAYLOAD), 3, 4);

        // when
        writer.write(buffer, 0);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final WfDefinitionDecoder decoder = new WfDefinitionDecoder();

        int readOffset = 0;

        headerDecoder.wrap(buffer, readOffset);

        assertThat(headerDecoder.blockLength()).isEqualTo(WfDefinitionEncoder.BLOCK_LENGTH);
        assertThat(headerDecoder.templateId()).isEqualTo(WfDefinitionEncoder.TEMPLATE_ID);
        assertThat(headerDecoder.schemaId()).isEqualTo(WfDefinitionEncoder.SCHEMA_ID);
        assertThat(headerDecoder.version()).isEqualTo(WfDefinitionEncoder.SCHEMA_VERSION);

        readOffset += headerDecoder.encodedLength();

        decoder.wrap(buffer, readOffset, headerDecoder.blockLength(), headerDecoder.version());

        assertThat(decoder.id()).isEqualTo(1);

        final byte[] typeKey = new byte[2];
        decoder.getKey(typeKey, 0, 2);
        assertThat(typeKey).containsExactly((byte) 5, (byte) 6);

        final byte[] payload = new byte[4];
        decoder.getResource(payload, 0, 4);
        assertThat(payload).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    @Test
    public void testWriteVariableLengthFieldsInReverseOrder()
    {
        // given
        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        final WfDefinitionWriter writer = new WfDefinitionWriter()
                .resource(new UnsafeBuffer(PAYLOAD), 3, 4)
                .wfDefinitionKey(TYPE);

        // when
        writer.write(buffer, 0);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 0);

        final WfDefinitionDecoder decoder = new WfDefinitionDecoder();
        decoder.wrap(buffer, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final byte[] typeKey = new byte[2];
        decoder.getKey(typeKey, 0, 2);
        assertThat(typeKey).containsExactly((byte) 5, (byte) 6);

        final byte[] payload = new byte[4];
        decoder.getResource(payload, 0, 4);
        assertThat(payload).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

}
