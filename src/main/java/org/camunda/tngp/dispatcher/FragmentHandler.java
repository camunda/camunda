package org.camunda.tngp.dispatcher;

import uk.co.real_logic.agrona.DirectBuffer;

@FunctionalInterface
public interface FragmentHandler
{

    void onFragment(DirectBuffer buffer, int offset, int length, int streamId);

}
