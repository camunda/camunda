package org.camunda.tngp.example.msgpack.impl.newidea;

import java.nio.ByteBuffer;

import org.agrona.BitUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.list.CompactList;

public class MsgPackTraversalContext
{

    protected static final int CURRENT_ELEMENT_OFFSET = 0;
    protected static final int NUM_ELEMENTS_OFFSET = BitUtil.SIZE_OF_INT;
    protected static final int APPLYING_FILTER_OFFSET = BitUtil.SIZE_OF_INT * 2;
    protected static final int CONTAINER_TYPE_OFFSET = BitUtil.SIZE_OF_INT * 3;

    protected static final int ELEMENT_SIZE = BitUtil.SIZE_OF_INT * 4;
    protected static final DirectBuffer EMPTY_BYTES = new UnsafeBuffer(new byte[ELEMENT_SIZE]);

    protected CompactList traversalStack;
    protected UnsafeBuffer cursorView = new UnsafeBuffer(0, 0);

    public MsgPackTraversalContext(int maxTraversalDepth)
    {
        traversalStack = new CompactList(
                ELEMENT_SIZE,
                maxTraversalDepth,
                (size) -> ByteBuffer.allocate(size * ELEMENT_SIZE));
    }

    public boolean hasElements()
    {
        return traversalStack.size() > 0;
    }

    public int size()
    {
        return traversalStack.size();
    }

    // cursor operations

    public void moveTo(int element)
    {
        traversalStack.wrap(element, cursorView);
    }

    public void moveToLastElement()
    {
        traversalStack.wrap(traversalStack.size() - 1, cursorView);
    }

    public void appendElement()
    {
        traversalStack.add(EMPTY_BYTES, 0, ELEMENT_SIZE);
        moveToLastElement();
    }

    public void removeLastElement()
    {
        traversalStack.remove(traversalStack.size() - 1);

        if (size() > 0)
        {
            moveToLastElement();
        }
    }

    public int currentElement()
    {
        return cursorView.getInt(CURRENT_ELEMENT_OFFSET);
    }

    public void currentElement(int newValue)
    {
        cursorView.putInt(CURRENT_ELEMENT_OFFSET, newValue);
    }

    public int numElements()
    {
        return cursorView.getInt(NUM_ELEMENTS_OFFSET);
    }

    public void numElements(int newValue)
    {
        cursorView.putInt(NUM_ELEMENTS_OFFSET, newValue);
    }

    public int applyingFilter()
    {
        return cursorView.getInt(APPLYING_FILTER_OFFSET);
    }

    public void applyingFilter(int newValue)
    {
        cursorView.putInt(APPLYING_FILTER_OFFSET, newValue);
    }

    public boolean isMap()
    {
        return cursorView.getInt(CONTAINER_TYPE_OFFSET) == 0;
    }

    public void setIsMap(boolean isMap)
    {
        cursorView.putInt(CONTAINER_TYPE_OFFSET, isMap ? 0 : 1);
    }

}
