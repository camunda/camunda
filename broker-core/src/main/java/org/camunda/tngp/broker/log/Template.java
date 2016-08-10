package org.camunda.tngp.broker.log;

import org.camunda.tngp.util.ReflectUtil;
import org.camunda.tngp.util.buffer.BufferReader;

/**
 * A template is immutable and thread-safe
 *
 * @author Lindhauer
 */
public class Template<T extends BufferReader>
{

    protected int id;
    protected Class<T> readerClass;

    public Template(int id, Class<T> readerClass)
    {
        this.readerClass = readerClass;
        this.id = id;
    }

    public T newReader()
    {
        return ReflectUtil.newInstance(readerClass);
    }

    public int id()
    {
        return id;
    }
}
