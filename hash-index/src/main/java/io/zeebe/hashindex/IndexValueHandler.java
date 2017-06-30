package io.zeebe.hashindex;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

public interface IndexValueHandler
{
    void readValue(DirectBuffer buffer, int offset, int length);

    void writeValue(MutableDirectBuffer buffer, int offset, int length);
}