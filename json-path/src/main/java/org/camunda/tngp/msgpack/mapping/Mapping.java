package org.camunda.tngp.msgpack.mapping;

import org.camunda.tngp.msgpack.jsonpath.JsonPathQuery;

/**
 * Represents a mapping to map from one message pack document to another.
 * The mapping has a json path query for the source and a json path string which points to the target.
 *
 * This makes it possible to map a part of a message pack document into a new/existing document.
 * With the mapping it is possible to replace/rename objects.
 */
public class Mapping
{
    public static final String JSON_ROOT_PATH = "$";

    private JsonPathQuery source;
    private String targetQueryString;

    public Mapping(JsonPathQuery source, String targetQueryString)
    {
        this.source = source;
        this.targetQueryString = targetQueryString;
    }

    public JsonPathQuery getSource()
    {
        return this.source;
    }


    public String getTargetQueryString()
    {
        return this.targetQueryString;
    }

    public boolean isRootTargetMapping()
    {
        return JSON_ROOT_PATH.equals(targetQueryString);
    }
}
