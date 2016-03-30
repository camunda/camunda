package org.camunda.tngp.log;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.nio.channels.FileChannel;

import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.log.index.LogEntryIndexer;
import org.camunda.tngp.log.index.LogIndex;
import org.junit.Before;
import org.junit.Test;

public class LogIndexTest
{

    Log logMock;
    LogEntryIndexer entryIndexerMock;

    LogIndex logIndex;
    FileChannel fileChannelMock;

    @Before
    public void createLog()
    {
        final HashIndex indexMock = mock(HashIndex.class);

        logMock = mock(Log.class);
        entryIndexerMock = mock(LogEntryIndexer.class);

        logIndex = new LogIndex(logMock, entryIndexerMock, indexMock);
        fileChannelMock = mock(FileChannel.class);
    }

    @Test
    public void shouldInvokeEntryIndexer()
    {
        final long currentPosition = 0;

        when(logMock.pollFragment(anyLong(), any(LogFragmentHandler.class))).thenAnswer(invocation ->
        {
            final long position = (long) invocation.getArguments()[0];

            if(position == currentPosition)
            {
                LogFragmentHandler logFragmentHandler = (LogFragmentHandler)invocation.getArguments()[1];
                logFragmentHandler.onFragment(currentPosition, fileChannelMock, 0, 10);
            }

            return -1;
        });


        // if
        logIndex.updateIndex();

        // then
        verify(entryIndexerMock, times(1)).indexEntry(any(), eq(currentPosition), eq(fileChannelMock), eq(0), eq(10));
    }

}
