package org.camunda.tngp.list;

import static org.agrona.BitUtil.SIZE_OF_INT;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;

import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;


public class CompactListIntegrationTest
{
    CompactList compactList;

    UnsafeBuffer writeBuffer = new UnsafeBuffer(0, 0);
    UnsafeBuffer readBuffer = new UnsafeBuffer(0, 0);

    @Before
    public void setup()
    {
        compactList = new CompactList(SIZE_OF_INT, 16, (bufferCapacity) -> ByteBuffer.allocateDirect(bufferCapacity));
        writeBuffer.wrap(new byte[SIZE_OF_INT]);
        readBuffer.wrap(new byte[SIZE_OF_INT]);
    }

    @Test
    public void shoudAddToEmptyList()
    {
        writeBuffer.putInt(0, 10);
        compactList.add(writeBuffer);

        assertThat(compactList.size()).isEqualTo(1);

        compactList.get(0, readBuffer, 0);
        assertThat(readBuffer.getInt(0)).isEqualTo(10);

        compactList.remove(0);
        assertThat(compactList.size()).isEqualTo(0);
    }


}
