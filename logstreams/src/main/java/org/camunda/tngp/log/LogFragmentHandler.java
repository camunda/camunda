package org.camunda.tngp.log;

import java.nio.channels.FileChannel;

@FunctionalInterface
public interface LogFragmentHandler
{

    void onFragment(long position, FileChannel fileChannel, int offset, int length);

}
