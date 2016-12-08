package org.camunda.tngp.example.msgpack.jsonpath;

import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackFilter;

public class JsonPathQuery
{
    protected static final int MAX_DEPTH = 100;

    // TODO: encode filters in byte array to make them garbage-free
    protected MsgPackFilter[] filters = new MsgPackFilter[MAX_DEPTH];
    protected int size;

    public void reset()
    {
        size = 0;
    }

    public void addFilter(MsgPackFilter filter)
    {
        filters[size] = filter;
        size++;
    }

    public int getSize()
    {
        return size;
    }

    public MsgPackFilter getFilter(int index)
    {
        // TODO: range check

        return filters[index];
    }

}
