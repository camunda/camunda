package net.long_running.dispatcher;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

@FunctionalInterface
public interface FragmentHandler
{

    void onFragment(UnsafeBuffer buffer, int offset, int length);

}
