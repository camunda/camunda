package org.camunda.tngp.hashindex.store;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import uk.co.real_logic.agrona.LangUtil;

public class FileChannelIndexStore implements IndexStore
{

    protected final FileChannel fileChannel;

    protected long allocatedLength;

    protected final ByteBuffer zeroBytes;

    public FileChannelIndexStore(FileChannel fileChannel)
    {
        final byte[] filler = new byte[4 * 1024];
        Arrays.fill(filler, (byte) 0);
        zeroBytes = ByteBuffer.wrap(filler);

        this.fileChannel = fileChannel;

        try
        {
            allocatedLength = fileChannel.size();
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public void close()
    {
        try
        {
            fileChannel.force(true);
            fileChannel.close();
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    @Override
    public void read(ByteBuffer buffer, long position)
    {
        try
        {
            fileChannel.read(buffer, position);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    @Override
    public void write(ByteBuffer buffer, long position)
    {
        try
        {
            fileChannel.write(buffer, position);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    @Override
    public long allocate(int length)
    {
        final long previousLength = allocatedLength;

        try
        {
            fileChannel.position(previousLength);

            final int blockSize = zeroBytes.capacity();
            final int blocks = length / blockSize;
            final int blockRemainder = length % blockSize;

            for (int i = 0; i < blocks; i++)
            {
                zeroBytes.position(0);
                fileChannel.write(zeroBytes);
            }

            if (blockRemainder > 0)
            {
                zeroBytes.position(0);
                zeroBytes.limit(blockRemainder);
                fileChannel.write(zeroBytes);
            }
        }
        catch (final IOException ex)
        {
            LangUtil.rethrowUnchecked(ex);
        }

        allocatedLength += length;

        return previousLength;
    }

    public void flush()
    {
        try
        {
            fileChannel.force(true);
        }
        catch (IOException e)
        {
            LangUtil.rethrowUnchecked(e);
        }
    }

    public static FileChannelIndexStore tempFileIndexStore()
    {
        try
        {
            final File tmpFile = File.createTempFile("index-", ".idx");
            tmpFile.deleteOnExit();
            final RandomAccessFile raf = new RandomAccessFile(tmpFile, "rw");
            final FileChannel channel = raf.getChannel();
            return new FileChannelIndexStore(channel)
            {
                @Override
                public void close()
                {
                    try
                    {
                        super.close();
                    }
                    finally
                    {
                        try
                        {
                            raf.close();
                        }
                        catch (IOException e)
                        {
                            LangUtil.rethrowUnchecked(e);
                        }
                        finally
                        {
                            tmpFile.delete();
                        }
                    }
                }
            };
        }
        catch (IOException ex)
        {
            throw new RuntimeException(ex);
        }
    }

}
