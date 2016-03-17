package org.camunda.tngp.log.appender;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class LogFragementAppender
{

    public int append(
            final AppendableLogFragment fragment,
            final ByteBuffer block)
    {
        final FileChannel fileChannel = fragment.getFileChannel();

        final int blockLength = block.remaining();
        final int fragmentTail = fragment.getTailPosition();
        final int available = fragment.getFragmentSize() - fragmentTail;

        int newTail = -1;

        if(blockLength <= available)
        {
            try
            {
                fileChannel.write(block, fragmentTail);
                fileChannel.force(false);
                newTail = blockLength;
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        return newTail;
    }

}
