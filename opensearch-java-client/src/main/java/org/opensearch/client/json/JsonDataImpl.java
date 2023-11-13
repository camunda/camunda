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

import jakarta.json.JsonValue;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;

import java.io.StringReader;
import java.io.StringWriter;

class JsonDataImpl implements JsonData {
    private final Object value;
    private final JsonpMapper mapper;

    JsonDataImpl(Object value, JsonpMapper mapper) {
        this.value = value;
        this.mapper = mapper;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public JsonValue toJson() {
        return toJson(null);
    }

    @Override
    public JsonValue toJson(JsonpMapper mapper) {
        if (value instanceof JsonValue) {
            return (JsonValue) value;
        }

        // Provided mapper has precedence over the one that was optionally set at creation time
        mapper = mapper != null ? mapper : this.mapper;
        if (mapper == null) {
            throw new IllegalStateException("Contains a '" + value.getClass().getName() +
                "' that cannot be converted to a JsonValue without a mapper");
        }

        final JsonParser parser = getParser(mapper);
        parser.next(); // move to first event
        return parser.getValue();
    }

    @Override
    public <T> T to(Class<T> clazz) {
        return to(clazz, null);
    }

    @Override
    public <T> T to(Class<T> clazz, JsonpMapper mapper) {
        if (clazz.isAssignableFrom(value.getClass())) {
            return (T) value;
        }

        mapper = getMapper(mapper);

        JsonParser parser = getParser(mapper);
        return mapper.deserialize(parser, clazz);
    }

    @Override
    public <T> T deserialize(JsonpDeserializer<T> deserializer) {
        return deserialize(deserializer, null);
    }

    @Override
    public <T> T deserialize(JsonpDeserializer<T> deserializer, JsonpMapper mapper) {
        mapper = getMapper(mapper);

        return deserializer.deserialize(getParser(mapper), mapper);
    }

    @Override
    public void serialize(JsonGenerator generator, JsonpMapper mapper) {
        if (value instanceof JsonValue) {
            generator.write((JsonValue) value);
        } else {
            // Mapper provided at creation time has precedence
            (this.mapper != null ? this.mapper : mapper).serialize(value, generator);
        }
    }

    private JsonpMapper getMapper(JsonpMapper localMapper) {
        // Local mapper has precedence over the one provided at creation time
        localMapper = localMapper != null ? localMapper : this.mapper;
        if (localMapper == null) {
            throw new IllegalStateException("A JsonpMapper is needed to convert JsonData");
        }

        return localMapper;
    }

    private JsonParser getParser(JsonpMapper mapper) {
        // FIXME: inefficient roundtrip through a string. Should be replaced by an Event buffer structure.
        StringWriter sw = new StringWriter();
        JsonGenerator generator = mapper.jsonProvider().createGenerator(sw);

        if (value instanceof JsonValue) {
            generator.write((JsonValue) value);
        } else {
            mapper.serialize(value, generator);
        }
        generator.close();

        return mapper.jsonProvider().createParser(new StringReader(sw.toString()));
    }
}
