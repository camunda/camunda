package org.camunda.tngp.hashindex;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public interface IndexValueHandler
{
    void readValue(DirectBuffer buffer, int offset, int length);

    void writeValue(MutableDirectBuffer buffer, int offset, int length);
}