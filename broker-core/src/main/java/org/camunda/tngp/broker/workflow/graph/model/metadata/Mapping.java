package org.camunda.tngp.broker.workflow.graph.model.metadata;

import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;

/**
 * Represents a mapping which can used for input or output mappings.
 * The mapping has a json path query for the source and target.
 */
public class Mapping
{
    public static final String JSON_ROOT_PATH = "$";

    private JsonPathQuery source;
    private JsonPathQuery target;
    private String targetQueryString;

    public Mapping(JsonPathQuery source, JsonPathQuery target, String targetQueryString)
    {
        this.source = source;
        this.target = target;
        this.targetQueryString = targetQueryString;
    }

    public JsonPathQuery getSource()
    {
        return this.source;
    }

    public JsonPathQuery getTarget()
    {
        return this.target;
    }

    public String getTargetQueryString()
    {
        return this.targetQueryString;
    }
}
