/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.msgpack.mapping;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;

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
    public static final String MAPPING_STRING = "%s -> %s";

    private final JsonPathQuery source;
    private final String targetQueryString;

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

    @Override
    public String toString()
    {
        return String.format(MAPPING_STRING,
                             new String(source.getExpression().byteArray()),
                             targetQueryString);
    }
}
