package org.camunda.tngp.msgpack.jsonpath;

import org.camunda.tngp.msgpack.filter.MsgPackFilter;
import org.camunda.tngp.msgpack.query.MsgPackFilterContext;

public class JsonPathQuery
{
    protected static final int MAX_DEPTH = 30;
    protected static final int MAX_FILTER_CONTEXT_LENGTH = 50;
    protected static final int NO_INVALID_POSITION = -1;

    protected MsgPackFilter[] filters;
    protected MsgPackFilterContext filterInstances = new MsgPackFilterContext(MAX_DEPTH, MAX_FILTER_CONTEXT_LENGTH);
    protected int size;

    protected int invalidPosition;
    protected String errorMessage;

    public JsonPathQuery(MsgPackFilter[] filters)
    {
        this.filters = filters;
    }

    public void reset()
    {
        filterInstances.clear();
        invalidPosition = NO_INVALID_POSITION;
    }

    public MsgPackFilterContext getFilterInstances()
    {
        return filterInstances;
    }

    public MsgPackFilter[] getFilters()
    {
        return filters;
    }

    public void invalidate(int position, String message)
    {
        this.invalidPosition = position;
        this.errorMessage = message;
    }

    public boolean isValid()
    {
        return invalidPosition == NO_INVALID_POSITION;
    }

    public int getInvalidPosition()
    {
        return invalidPosition;
    }

    public String getErrorReason()
    {
        return errorMessage;
    }

}
