package org.camunda.tngp.broker.taskqueue;

import static uk.co.real_logic.agrona.BitUtil.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.log.index.LogEntryIndexer;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;
import uk.co.real_logic.sbe.ir.generated.MessageHeaderDecoder;

public class TaskInstanceIndexer implements LogEntryIndexer
{
    public static final int VALUE_LENGTH = SIZE_OF_LONG;

    protected MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected TaskInstanceDecoder decoder = new TaskInstanceDecoder();

    protected ByteBuffer readBuffer = ByteBuffer.allocateDirect(MessageHeaderDecoder.ENCODED_LENGTH + TaskInstanceDecoder.BLOCK_LENGTH);
    protected UnsafeBuffer readBufferWrapper = new UnsafeBuffer(readBuffer);

    @Override
    public void indexEntry(HashIndex index, long position, FileChannel fileChannel, int offset, int length)
    {
        readBuffer.clear();
        try
        {
            fileChannel.read(readBuffer, offset);

            headerDecoder.wrap(readBufferWrapper, 0);

            if(headerDecoder.templateId() == TaskInstanceDecoder.TEMPLATE_ID)
            {
                decoder.wrap(readBufferWrapper, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());
                index.put(decoder.id(), position);
            }
        }
        catch (IOException e)
        {
            // TODO
            e.printStackTrace();
        }
    }

}
