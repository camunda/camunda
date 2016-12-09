package org.camunda.tngp.example.msgpack.impl.newidea;

import org.agrona.BitUtil;

public class MsgPackFilterContext extends AbstractDynamicContext
{

    protected static final int FILTER_ID_OFFSET = 0;

    protected static final int STATIC_ELEMENT_SIZE = BitUtil.SIZE_OF_INT;

    public MsgPackFilterContext(int capacity,int dynamicContextSize)
    {
        super(capacity, STATIC_ELEMENT_SIZE, dynamicContextSize);
    }

    public int filterId()
    {
        return cursorView.getInt(FILTER_ID_OFFSET);
    }

    public void filterId(int filterId)
    {
        cursorView.putInt(FILTER_ID_OFFSET, filterId);
    }
}
