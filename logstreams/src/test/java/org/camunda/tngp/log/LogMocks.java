package org.camunda.tngp.log;

import java.nio.channels.FileChannel;

import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class LogMocks
{

    public static class LogPollAnswer implements Answer<Long>
    {
        protected FileChannel fileChannel;
        protected long result;
        protected int fileChannelOffset;
        protected int fragmentLength;

        public LogPollAnswer(long result, FileChannel fileChannel, int fileChannelOffset, int fragmentLength)
        {
            this.result = result;
            this.fileChannel = fileChannel;
            this.fileChannelOffset = fileChannelOffset;
            this.fragmentLength = fragmentLength;
        }

        public LogPollAnswer(long result)
        {
            this.result = -1;
        }


        public Long answer(InvocationOnMock invocation) throws Throwable
        {
            if (fileChannel != null)
            {
                final long position = (long) invocation.getArguments()[0];
                final LogFragmentHandler logFragmentHandler = (LogFragmentHandler) invocation.getArguments()[1];
                logFragmentHandler.onFragment(position, fileChannel, fileChannelOffset, fragmentLength);
            }

            return result;
        }

    }
}
