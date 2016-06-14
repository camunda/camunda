package org.camunda.tngp.log;

import uk.co.real_logic.agrona.DirectBuffer;

public interface FragmentReader
{
    void wrap(final DirectBuffer buffer, int offset, int length);
}
