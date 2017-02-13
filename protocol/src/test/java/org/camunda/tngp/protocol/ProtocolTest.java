package org.camunda.tngp.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteOrder;

import org.junit.Test;

public class ProtocolTest
{

    @Test
    public void testEndiannessConstant()
    {
        assertThat(Protocol.ENDIANNESS).isEqualTo(ByteOrder.LITTLE_ENDIAN);
    }

}
