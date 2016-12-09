package org.camunda.tngp.example.msgpack.jsonpath;

import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackFilter;
import org.camunda.tngp.example.msgpack.impl.newidea.MsgPackFilterContext;

public class JsonPathQuery
{
    protected static final int MAX_DEPTH = 30;
    protected static final int MAX_FILTER_CONTEXT_LENGTH = 50;

    protected MsgPackFilter[] filters;
    protected MsgPackFilterContext filterInstances = new MsgPackFilterContext(MAX_DEPTH, MAX_FILTER_CONTEXT_LENGTH);
    protected int size;

    public JsonPathQuery(MsgPackFilter[] filters)
    {
        this.filters = filters;
    }

    public void reset()
    {
        filterInstances.clear();
    }

    public MsgPackFilterContext getFilterInstances()
    {
        return filterInstances;
    }

    public MsgPackFilter[] getFilters()
    {
        return filters;
    }

}
