package org.camunda.tngp.broker.wf.repository.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.protocol.wf.MessageHeaderDecoder;
import org.camunda.tngp.taskqueue.data.WfTypeDecoder;
import org.camunda.tngp.taskqueue.data.WfTypeEncoder;
import org.junit.Before;
import org.junit.Test;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfTypeLogEntryWriterTest
{

    protected static final byte[] PAYLOAD = new byte[] {0, 0, 0, 1, 2, 3, 4, 0};
    protected static final byte[] TYPE = new byte[] {0, 0, 0, 0, 0, 0, 0, 5, 6, 0};


    @Test
    public void testWriting()
    {
        // given
        UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        WfTypeLogEntryWriter writer = new WfTypeLogEntryWriter();

        writer.wrap(buffer, 0);

        // when
        writer
            .resourceId(42)
            .shardId(53)
            .id(1)
            .version(2)
            .prevVersionPosition(3)
            .wfType(new UnsafeBuffer(TYPE), 7, 2)
            .payload(new UnsafeBuffer(PAYLOAD), 3, 4)
            .flush();

        // then
        MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 0);

        assertThat(headerDecoder.blockLength()).isEqualTo(WfTypeEncoder.BLOCK_LENGTH);
        assertThat(headerDecoder.templateId()).isEqualTo(WfTypeEncoder.TEMPLATE_ID);
        assertThat(headerDecoder.schemaId()).isEqualTo(WfTypeEncoder.SCHEMA_ID);
        assertThat(headerDecoder.version()).isEqualTo(WfTypeEncoder.SCHEMA_VERSION);
        assertThat(headerDecoder.resourceId()).isEqualTo(42);
        assertThat(headerDecoder.shardId()).isEqualTo(53);

        WfTypeDecoder decoder = new WfTypeDecoder();
        decoder.wrap(buffer, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(decoder.id()).isEqualTo(1);
        assertThat(decoder.version()).isEqualTo(2);
        assertThat(decoder.prevVersionPosition()).isEqualTo(3);

        byte[] typeKey = new byte[2];
        decoder.getTypeKey(typeKey, 0, 2);
        assertThat(typeKey).containsExactly((byte) 5, (byte) 6);

        byte[] payload = new byte[4];
        decoder.getResource(payload, 0, 4);
        assertThat(payload).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    @Test
    public void testWriteVariableLengthFieldsInReverseOrder() {
        // given
        UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);
        WfTypeLogEntryWriter writer = new WfTypeLogEntryWriter();

        writer.wrap(buffer, 0);

        // when
        writer
            .payload(new UnsafeBuffer(PAYLOAD), 3, 4)
            .wfType(new UnsafeBuffer(TYPE), 7, 2)
            .flush();

        // then
        MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 0);

        WfTypeDecoder decoder = new WfTypeDecoder();
        decoder.wrap(buffer, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        byte[] typeKey = new byte[2];
        decoder.getTypeKey(typeKey, 0, 2);
        assertThat(typeKey).containsExactly((byte) 5, (byte) 6);

        byte[] payload = new byte[4];
        decoder.getResource(payload, 0, 4);
        assertThat(payload).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

    // TODO: test when not all properties are written
}
