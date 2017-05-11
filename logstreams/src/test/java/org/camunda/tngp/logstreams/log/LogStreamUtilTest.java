package org.camunda.tngp.logstreams.log;

import org.camunda.tngp.logstreams.impl.log.index.LogBlockIndex;
import org.camunda.tngp.logstreams.spi.LogStorage;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.ByteBuffer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.logstreams.log.LogStreamUtil.INVALID_ADDRESS;
import static org.camunda.tngp.logstreams.log.LogStreamUtil.MAX_READ_EVENT_SIZE;
import static org.camunda.tngp.logstreams.log.LogTestUtil.*;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.*;

public class LogStreamUtilTest
{
    @Mock
    public LogStream logStream;

    @Mock
    public LogStorage logStorage;

    @Mock
    public LogBlockIndex logBlockIndex;



    @Before
    public void init()
    {
        MockitoAnnotations.initMocks(this);
        when(logStream.getLogStorage()).thenReturn(logStorage);
        when(logStream.getLogBlockIndex()).thenReturn(logBlockIndex);

    }

    @Test
    public void shouldReturnInvalidAddressIfNoValidStartAddressExist()
    {
        when(logStorage.getFirstBlockAddress()).thenReturn((long) INVALID_ADDRESS);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logStorage).getFirstBlockAddress();
        assertThat(addressForPosition).isEqualTo(INVALID_ADDRESS);
    }


    @Test
    public void shouldFindCorrectAddress()
    {
        when(logBlockIndex.size()).thenReturn(1);
        when(logBlockIndex.lookupBlockAddress(LOG_POSITION)).thenReturn(LOG_ADDRESS);
        MockLogStorage
            .newLogEntry()
            .messageLength(EVENT_LENGTH)
            .partlyRead()
            .build(logStorage);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logBlockIndex).lookupBlockAddress(LOG_POSITION);
        verify(logStorage, times(2 * ((int) LOG_POSITION + 1))).read(any(ByteBuffer.class), anyLong());
        assertThat(addressForPosition).isGreaterThan(LOG_ADDRESS + LOG_POSITION * EVENT_LENGTH);
    }

    @Test
    public void shouldFindCorrectAddressWithoutBlockIndex()
    {
        when(logStorage.getFirstBlockAddress()).thenReturn(LOG_ADDRESS);
        MockLogStorage
            .newLogEntry()
            .messageLength(EVENT_LENGTH)
            .partlyRead()
            .build(logStorage);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logStorage).getFirstBlockAddress();
        verify(logStorage, times(2 * ((int) LOG_POSITION + 1))).read(any(ByteBuffer.class), anyLong());
        assertThat(addressForPosition).isGreaterThan(LOG_ADDRESS + LOG_POSITION * EVENT_LENGTH);
    }

    @Test
    public void shouldNotFindCorrectAddressForNotExistingPosition()
    {
        when(logBlockIndex.size()).thenReturn(1);
        when(logBlockIndex.lookupBlockAddress(LOG_POSITION)).thenReturn(LOG_ADDRESS);
        MockLogStorage
            .newLogEntry()
            .messageLength(EVENT_LENGTH)
            .maxPosition(LOG_POSITION - 1)
            .partlyRead()
            .build(logStorage);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logBlockIndex).lookupBlockAddress(LOG_POSITION);
        verify(logStorage, times(2 * ((int) LOG_POSITION) + 1)).read(any(ByteBuffer.class), anyLong());
        assertThat(addressForPosition).isEqualTo(INVALID_ADDRESS);
    }

    @Test
    public void shouldNotFindCorrectAddressForNotExistingPositionWithoutBlockIndex()
    {
        when(logStorage.getFirstBlockAddress()).thenReturn(LOG_ADDRESS);
        MockLogStorage
            .newLogEntry()
            .messageLength(EVENT_LENGTH)
            .maxPosition(LOG_POSITION - 1)
            .partlyRead()
            .build(logStorage);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logStorage).getFirstBlockAddress();
        verify(logStorage, times(2 * ((int) LOG_POSITION) + 1)).read(any(ByteBuffer.class), anyLong());
        assertThat(addressForPosition).isEqualTo(INVALID_ADDRESS);
    }

    @Test
    public void shouldFindCorrectAddressForLargeEvents()
    {
        when(logBlockIndex.size()).thenReturn(1);
        when(logBlockIndex.lookupBlockAddress(LOG_POSITION)).thenReturn(LOG_ADDRESS);
        MockLogStorage
            .newLogEntry()
            .messageLength(2 * MAX_READ_EVENT_SIZE)
            .partlyRead()
            .build(logStorage);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logBlockIndex).lookupBlockAddress(LOG_POSITION);
        verify(logStorage, times(4 * ((int) LOG_POSITION + 1))).read(any(ByteBuffer.class), anyLong());
        assertThat(addressForPosition).isGreaterThan(LOG_ADDRESS + LOG_POSITION * 2 * MAX_READ_EVENT_SIZE);
    }

    @Test
    public void shouldFindCorrectAddressForLargeEventsWithoutBlockIndex()
    {
        when(logStorage.getFirstBlockAddress()).thenReturn(LOG_ADDRESS);
        MockLogStorage
            .newLogEntry()
            .messageLength(2 * MAX_READ_EVENT_SIZE)
            .partlyRead()
            .build(logStorage);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logStorage).getFirstBlockAddress();
        verify(logStorage, times(4 * ((int) LOG_POSITION + 1))).read(any(ByteBuffer.class), anyLong());
        assertThat(addressForPosition).isGreaterThan(LOG_ADDRESS + LOG_POSITION * 2 * MAX_READ_EVENT_SIZE);
    }


    @Test
    public void shouldNotFindCorrectAddressForLargeEventsAndNotExistingPosition()
    {
        when(logBlockIndex.size()).thenReturn(1);
        when(logBlockIndex.lookupBlockAddress(LOG_POSITION)).thenReturn(LOG_ADDRESS);
        MockLogStorage
            .newLogEntry()
            .messageLength(2 * MAX_READ_EVENT_SIZE)
            .maxPosition(LOG_POSITION - 1)
            .partlyRead()
            .build(logStorage);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logBlockIndex).lookupBlockAddress(LOG_POSITION);
        verify(logStorage, times(4 * ((int) LOG_POSITION) + 1)).read(any(ByteBuffer.class), anyLong());
        assertThat(addressForPosition).isEqualTo(INVALID_ADDRESS);
    }

    @Test
    public void shouldNotFindCorrectAddressForLargeEventsAndNotExistingPositionWithoutBlockIndex()
    {
        when(logStorage.getFirstBlockAddress()).thenReturn(LOG_ADDRESS);
        MockLogStorage
            .newLogEntry()
            .messageLength(2 * MAX_READ_EVENT_SIZE)
            .maxPosition(LOG_POSITION - 1)
            .partlyRead()
            .build(logStorage);

        // when
        final long addressForPosition = LogStreamUtil.getAddressForPosition(logStream, LOG_POSITION);

        // then
        verify(logBlockIndex).size();
        verify(logStorage).getFirstBlockAddress();
        verify(logStorage, times(4 * ((int) LOG_POSITION) + 1)).read(any(ByteBuffer.class), anyLong());
        assertThat(addressForPosition).isEqualTo(INVALID_ADDRESS);
    }

}
