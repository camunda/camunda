package net.long_running.dispatcher;

import java.nio.ByteBuffer;

@FunctionalInterface
public interface BlockHandler
{

    void onBlockAvailable(ByteBuffer buffer, int blockOffset, int blockLength, int streamId);

}
