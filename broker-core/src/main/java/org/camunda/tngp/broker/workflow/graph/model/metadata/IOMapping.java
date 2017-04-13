package org.camunda.tngp.broker.workflow.graph.model.metadata;

import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;

/**
 * Represents an input and output mapping for a flow element.
 * As input and output mapping json path queries are used.
 */
public class IOMapping
{
    private JsonPathQuery inputQuery;
    private JsonPathQuery outputQuery;

    public JsonPathQuery getInputQuery()
    {
        return inputQuery;
    }

    public void setInputQuery(JsonPathQuery inputQuery)
    {
        this.inputQuery = inputQuery;
    }

    public JsonPathQuery getOutputQuery()
    {
        return outputQuery;
    }

    public void setOutputQuery(JsonPathQuery outputQuery)
    {
        this.outputQuery = outputQuery;
    }
}
