package org.camunda.tngp.broker.wf.repository.log;

import org.junit.Test;
import static org.assertj.core.api.Assertions.*;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class WfTypeReaderTest
{
    protected static final byte[] PAYLOAD = new byte[] {0, 0, 0, 1, 2, 3, 4, 0};
    protected static final byte[] TYPE = new byte[] {5, 6};

    @Test
    public void testReading()
    {
        final WfTypeReader reader = new WfTypeReader();
        final WfTypeWriter writer = new WfTypeWriter();

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);

        writer.resourceId(42)
            .shardId(53)
            .id(1)
            .version(2)
            .prevVersionPosition(3)
            .wfTypeKey(TYPE)
            .resource(new UnsafeBuffer(PAYLOAD), 3, 4)
            .write(buffer, 0);

        reader.wrap(buffer, 0, 512);

        assertThat(reader.resourceId()).isEqualTo(42);
        assertThat(reader.shardId()).isEqualTo(53);
        assertThat(reader.id()).isEqualTo(1);
        assertThat(reader.version()).isEqualTo(2);
        assertThat(reader.prevVersionPosition()).isEqualTo(3);

        final byte[] typeKey = new byte[2];
        reader.getTypeKey().getBytes(0, typeKey);
        assertThat(typeKey).containsExactly((byte) 5, (byte) 6);

        final byte[] payload = new byte[4];
        reader.getResource().getBytes(0, payload);
        assertThat(payload).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

}
