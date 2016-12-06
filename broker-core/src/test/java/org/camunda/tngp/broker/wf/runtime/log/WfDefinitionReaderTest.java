package org.camunda.tngp.broker.wf.runtime.log;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.wf.WfDefinitionReader;
import org.junit.Test;

public class WfDefinitionReaderTest
{
    protected static final byte[] PAYLOAD = new byte[] {0, 0, 0, 1, 2, 3, 4, 0};
    protected static final byte[] TYPE = new byte[] {5, 6};

    @Test
    public void testReading()
    {
        final WfDefinitionReader reader = new WfDefinitionReader();
        final WfDefinitionWriter writer = new WfDefinitionWriter();

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[512]);

        writer
            .id(1)
            .wfDefinitionKey(TYPE)
            .resource(new UnsafeBuffer(PAYLOAD), 3, 4)
            .get(buffer, 0);

        reader.wrap(buffer, 0, 512);

        assertThat(reader.id()).isEqualTo(1);

        final byte[] typeKey = new byte[2];
        reader.getTypeKey().getBytes(0, typeKey);
        assertThat(typeKey).containsExactly((byte) 5, (byte) 6);

        final byte[] payload = new byte[4];
        reader.getResource().getBytes(0, payload);
        assertThat(payload).containsExactly((byte) 1, (byte) 2, (byte) 3, (byte) 4);
    }

}
