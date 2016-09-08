package org.camunda.tngp.dispatcher.impl.allocation;

import java.io.IOException;
import java.nio.ByteBuffer;

public class ExternallyAllocatedBuffer extends AllocatedBuffer
{

    public ExternallyAllocatedBuffer(ByteBuffer buffer)
    {
        super(buffer);
    }

    @Override
    public void close() throws IOException
    {
        // nothing to do; buffer allocated externally
    }

}
