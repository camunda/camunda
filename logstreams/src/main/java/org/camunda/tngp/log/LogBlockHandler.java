package org.camunda.tngp.log;

import java.nio.channels.FileChannel;

@FunctionalInterface
public interface LogBlockHandler
{
    void onBlock(long position, FileChannel channel, int blockOffset, int blockLength);
}
