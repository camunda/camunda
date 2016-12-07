package org.camunda.tngp.example.msgpack.impl;

import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class JsonPathQueryExecutor
{
    protected DirectBuffer msgPackBuffer = new UnsafeBuffer(0, 0);

    protected ImmutableIntList currentSelection = new ImmutableIntList(100);
    protected ImmutableIntList tempSelection = new ImmutableIntList(100);

    public void wrapMsgPack(DirectBuffer buffer, int offset, int length)
    {
        this.msgPackBuffer.wrap(buffer, offset, length);
        currentSelection.clear();
        tempSelection.clear();
        currentSelection.add(0);
    }

    // TODO: query-operators should be represented differently
    public void select(String key)
    {

    }



}
