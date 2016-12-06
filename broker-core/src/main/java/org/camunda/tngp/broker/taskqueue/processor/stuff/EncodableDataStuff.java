package org.camunda.tngp.broker.taskqueue.processor.stuff;

import org.agrona.MutableDirectBuffer;

public interface EncodableDataStuff extends DataStuff
{
    void encode(MutableDirectBuffer buffer, int offset);
}
