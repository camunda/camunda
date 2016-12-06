package org.camunda.tngp.broker.taskqueue.processor.stuff;

import java.lang.reflect.Array;

import org.agrona.LangUtil;
import org.camunda.tngp.protocol.clientapi.GroupSizeEncodingEncoder;

@SuppressWarnings("unchecked")
public class ListField<T extends DataStuff> implements DataStuff
{
    protected final Class<T> type;

    private int size = 0;
    public T[] list;

    public ListField(Class<T> type)
    {
        this.type = type;
        createArrayInstance(0);
    }

    private void createArrayInstance(int capacity)
    {
        list = (T[]) Array.newInstance(type, capacity);

        for (int i = 0; i < capacity; i++)
        {
            try
            {
                list[i] = type.newInstance();
            }
            catch (Exception e)
            {
                LangUtil.rethrowUnchecked(e);
            }
        }
    }

    public int getSize()
    {
        return size;
    }

    public void ensureCapacity(int size)
    {
        if (list.length < size)
        {
            createArrayInstance(size);
        }

        this.size = size;
    }

    @Override
    public void reset()
    {
        for (int i = 0; i < size; i++)
        {
            list[i].reset();
        }

        size = 0;
    }

    public int getEncodedLength()
    {
        int length = GroupSizeEncodingEncoder.ENCODED_LENGTH;

        for (int i = 0; i < size; i++)
        {
            length += list[i].getEncodedLength();
        }

        return length;
    }
}
