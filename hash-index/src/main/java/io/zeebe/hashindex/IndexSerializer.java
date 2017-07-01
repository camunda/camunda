package io.zeebe.hashindex;

import java.io.*;

import org.agrona.BitUtil;
import org.agrona.IoUtil;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Can read / write index from / to stream
 *
 */
public class IndexSerializer
{
    private static final int VERSION = 1;

    private final byte[] buffer = new byte[IoUtil.BLOCK_SIZE];
    private final UnsafeBuffer bufferView = new UnsafeBuffer(buffer);

    private HashIndex<?, ?> index;

    public void wrap(HashIndex<?, ?> index)
    {
        this.index = index;
    }

    public void writeToStream(OutputStream outputStream) throws IOException
    {
        bufferView.putInt(0, VERSION);
        outputStream.write(buffer, 0, BitUtil.SIZE_OF_INT);

        index.getIndexBuffer().writeToStream(outputStream, buffer);
        index.getDataBuffer().writeToStream(outputStream, buffer);
    }

    public void readFromStream(InputStream inputStream) throws IOException
    {
        inputStream.read(buffer, 0, BitUtil.SIZE_OF_INT);
        final int version = bufferView.getInt(0);

        if (version != VERSION)
        {
            throw new RuntimeException(String.format("Cannot read index snapshot: expected version %d but got version %d", VERSION, version));
        }

        index.getIndexBuffer().readFromStream(inputStream, buffer);
        index.getDataBuffer().readFromStream(inputStream, buffer);
    }

}
