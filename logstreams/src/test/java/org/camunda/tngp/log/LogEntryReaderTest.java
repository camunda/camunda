package org.camunda.tngp.log;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.channels.FileChannel;

import org.junit.Before;
import org.junit.Test;

public class LogEntryReaderTest
{
    Log logMock;
    FragmentReader readerMock;

    LogEntryReader logEntryReader;
    FileChannel fileChannelMock;

    @Before
    public void setup()
    {
        logMock = mock(Log.class);
        fileChannelMock = mock(FileChannel.class);

        readerMock = mock(FragmentReader.class);
        logEntryReader = new LogEntryReader(512);
    }

    @Test
    public void shouldRead() throws IOException
    {
        when(fileChannelMock.read(logEntryReader.readBuffer, 55)).thenReturn(10);
        when(logMock.pollFragment(1234, logEntryReader.pollHandler)).thenAnswer(new LogMocks.LogPollAnswer(1244, fileChannelMock, 55, 88));

        long nextFragment = logEntryReader.read(logMock, 1234, readerMock);

        assertThat(nextFragment).isEqualTo(1244);
        verify(logMock).pollFragment(1234, logEntryReader.pollHandler);
        verify(readerMock).wrap(logEntryReader.readBufferView, 0, 10);
    }

    @Test
    public void shouldNotReadIfNotAvailable() throws IOException
    {
        when(logMock.pollFragment(1234, logEntryReader.pollHandler)).thenAnswer(new LogMocks.LogPollAnswer(-1));

        long nextFragment = logEntryReader.read(logMock, 1234, readerMock);

        assertThat(nextFragment).isEqualTo(-1);

        verify(logMock).pollFragment(1234, logEntryReader.pollHandler);
        verifyNoMoreInteractions(readerMock);
    }

}
