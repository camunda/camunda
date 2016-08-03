package org.camunda.tngp.dispatcher;

import uk.co.real_logic.agrona.DirectBuffer;

@FunctionalInterface
public interface FragmentHandler
{
    int CONSUME_FRAGMENT_RESULT = 0;
    int POSTPONE_FRAGMENT_RESULT = 1;

    int onFragment(DirectBuffer buffer, int offset, int length, int streamId, boolean isMarkedFailed);

}
