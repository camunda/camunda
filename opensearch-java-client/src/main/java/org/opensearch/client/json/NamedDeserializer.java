/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.client.json;

import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;

import java.util.EnumSet;

/**
 * A deserializer that delegates to another deserializer provided as a JSON mapper attribute.
 */
public class NamedDeserializer<T> implements JsonpDeserializer<T> {

    private static final EnumSet<JsonParser.Event> events = EnumSet.of(
        Event.START_OBJECT,
        Event.START_ARRAY,
        Event.VALUE_FALSE,
        Event.VALUE_TRUE,
        Event.VALUE_NUMBER,
        Event.VALUE_STRING,
        Event.VALUE_NULL
    );

    private final String name;

    public NamedDeserializer(String name) {
        this.name = name;
    }

    @Override
    public EnumSet<JsonParser.Event> nativeEvents() {
        return events;
    }

    @Override
    public EnumSet<JsonParser.Event> acceptedEvents() {
        return events;
    }

    @Override
    public T deserialize(JsonParser parser, JsonpMapper mapper) {
        JsonpDeserializer<T> deserializer = mapper.attribute(name);
        if (deserializer == null) {
            throw new JsonParsingException("Missing deserializer for generic type: " + name, parser.getLocation());
        }
        return deserializer.deserialize(parser, mapper);
    }

    @Override
    public T deserialize(JsonParser parser, JsonpMapper mapper, JsonParser.Event event) {
        JsonpDeserializer<T> deserializer = mapper.attribute(name);
        if (deserializer == null) {
            throw new JsonParsingException("Missing deserializer for generic type: " + name, parser.getLocation());
        }
        return deserializer.deserialize(parser, mapper, event);
    }
}
