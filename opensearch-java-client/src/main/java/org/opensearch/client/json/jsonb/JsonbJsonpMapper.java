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

package org.opensearch.client.json.jsonb;

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpDeserializerBase;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpMapperBase;
import org.opensearch.client.json.JsonpSerializable;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.spi.JsonbProvider;
import jakarta.json.spi.JsonProvider;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;

import java.io.CharArrayReader;
import java.io.CharArrayWriter;
import java.util.EnumSet;

public class JsonbJsonpMapper extends JsonpMapperBase {

    private final JsonProvider jsonProvider;
    private final Jsonb jsonb;

    public JsonbJsonpMapper(JsonProvider jsonProvider, Jsonb jsonb) {
        this.jsonProvider = jsonProvider;
        this.jsonb = jsonb;
    }

    public JsonbJsonpMapper(JsonProvider jsonProvider, JsonbProvider jsonbProvider) {
        this(jsonProvider, jsonbProvider.create().build());
    }

    public JsonbJsonpMapper() {
        this(JsonProvider.provider(), JsonbProvider.provider());
    }

    @Override
    protected <T> JsonpDeserializer<T> getDefaultDeserializer(Class<T> clazz) {
        return new Deserializer<>(clazz);
    }

    @Override
    public <T> void serialize(T value, JsonGenerator generator) {
        if (value instanceof JsonpSerializable) {
            ((JsonpSerializable)value).serialize(generator, this);
            return;
        }

        // JSON-B doesn't offer a way to serialize to a JSON generator, so we have to roundtrip via a string representation.
        CharArrayWriter caw = new CharArrayWriter();
        jsonb.toJson(value, caw);

        CharArrayReader car = new CharArrayReader(caw.toCharArray());
        JsonParser parser = jsonProvider.createParser(car);
        transferAll(parser, generator);
    }

    @Override
    public JsonProvider jsonProvider() {
        return jsonProvider;
    }

    private class Deserializer<T> extends JsonpDeserializerBase<T> {
        private final Class<T> clazz;

        Deserializer(Class<T> clazz) {
            super(EnumSet.allOf(JsonParser.Event.class));
            this.clazz = clazz;
        }

        @Override
        public T deserialize(JsonParser parser, JsonpMapper mapper, JsonParser.Event event) {
            // TODO: Add a runtime check to use Yasson's JsonB extensions
            // JsonB doesn't provide methods to deserialize from a JsonParser or a JsonValue. We therefore have
            // to roundtrip through a string, which is far from efficient. Yasson addresses this with an additional
            // `YassonJsonb` that extends the base JSON-B interface with additional mapping functions. We should check
            // here at runtime if the mapper implements this interface and use it if present.
            CharArrayWriter caw = new CharArrayWriter();
            JsonGenerator generator = jsonProvider.createGenerator(caw);
            transferAll(parser, event, generator);
            generator.close();

            CharArrayReader car = new CharArrayReader(caw.toCharArray());
            return jsonb.fromJson(car, clazz);
        }
    }

    private void transferAll(JsonParser from, JsonGenerator to) {
        transferAll(from, from.next(), to);
    }

    /**
     * Pipe a JSON parser to a JSON generator.
     */
    private void transferAll(JsonParser from, JsonParser.Event event, JsonGenerator to) {
        transferEvent(from, event, to);
        switch(event) {
            case START_OBJECT: {
                int depth = 1;
                do {
                    event = from.next();
                    transferEvent(from, event, to);
                    switch (event) {
                        case START_OBJECT:
                            depth++;
                            break;
                        case END_OBJECT:
                            depth--;
                            break;
                    }
                } while(!(event == Event.END_OBJECT && depth == 0));
            }
            break;

            case START_ARRAY: {
                int depth = 1;
                do {
                    event = from.next();
                    transferEvent(from, event, to);
                    switch (event) {
                        case START_ARRAY:
                            depth++;
                            break;
                        case END_ARRAY:
                            depth--;
                            break;
                    }
                } while(!(event == Event.END_ARRAY && depth == 0));
            }
            break;

            default:
                // nothing more
        }
    }

    /**
     * Transfer a single event from a parser to a generator
     */
    private void transferEvent(JsonParser from, JsonParser.Event event, JsonGenerator to) {
        switch (event) {
            case START_OBJECT:
                to.writeStartObject();
                break;

            case START_ARRAY:
                to.writeStartArray();
                break;

            case END_OBJECT:
            case END_ARRAY:
                to.writeEnd();
                break;

            case KEY_NAME:
                to.writeKey(from.getString());
                break;

            case VALUE_STRING:
                to.write(from.getString());
                break;

            case VALUE_NUMBER:
                if (from.isIntegralNumber()) {
                    to.write(from.getLong());
                } else {
                    to.write(from.getBigDecimal());
                }
                break;

            case VALUE_NULL:
                to.writeNull();
                break;

            case VALUE_TRUE:
                to.write(true);
                break;

            case VALUE_FALSE:
                to.write(false);
                break;
        }
    }
}
