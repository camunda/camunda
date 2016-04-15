package org.camunda.tngp.hashindex;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

import static uk.co.real_logic.agrona.BitUtil.*;

public interface IndexValueHandler
{
    void readValue(DirectBuffer buffer, int offset, int length);

    void writeValue(MutableDirectBuffer buffer, int offset, int length);
}