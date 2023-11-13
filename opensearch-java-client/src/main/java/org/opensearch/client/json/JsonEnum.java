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

import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Base interface for enumerations in API types. Members have a JSON representation and also accept
 * aliases when parsed from a string value. For some enumerations primitive boolean values are also supported.
 */
public interface JsonEnum extends JsonpSerializable {
    String jsonValue();

    @Nullable
    default String[] aliases() {
        return null;
    }

    @Override
    default void serialize(JsonGenerator generator, JsonpMapper params) {
        generator.write(jsonValue());
    }

    class Deserializer<T extends JsonEnum> extends JsonpDeserializerBase<T> {
        private final Map<String, T> lookupTable;

        private static final EnumSet<JsonParser.Event> ACCEPTED_EVENTS = EnumSet.of(
            JsonParser.Event.VALUE_STRING,
            JsonParser.Event.KEY_NAME,
            JsonParser.Event.VALUE_TRUE,
            JsonParser.Event.VALUE_FALSE
        );

        private static final EnumSet<JsonParser.Event> NATIVE_EVENTS = EnumSet.of(JsonParser.Event.VALUE_STRING);

        public Deserializer(T[] values) {
            super(ACCEPTED_EVENTS, NATIVE_EVENTS);

            this.lookupTable = new HashMap<>((int) (values.length / 0.75f) + 1);
            for (T member : values) {
                this.lookupTable.put(member.jsonValue(), member);
                String[] aliases = member.aliases();
                if (aliases != null) {
                    for (String alias : aliases) {
                        this.lookupTable.put(alias, member);
                    }
                }
            }
        }

        @Override
        public T deserialize(JsonParser parser, JsonpMapper mapper, JsonParser.Event event) {
            String value;
            switch (event) {
                case VALUE_TRUE:
                    value = "true";
                    break;
                case VALUE_FALSE:
                    value = "false";
                    break;
                default:
                    value = parser.getString();
            }

            return deserialize(value, parser);
        }

        /**
         * Get the enum member for a JSON string value
         *
         * @param value the JSON value
         * @param parser parsing context
         * @return the enum member
         * @throws JsonParsingException if no matching enum was found
         */
        public T deserialize(String value, JsonParser parser) {
            T result = this.lookupTable.get(value);
            if (result == null) {
                throw new JsonParsingException("Invalid enum '" + value + "'", parser.getLocation());
            }
            return result;
        }

        /**
         * Get the enum member for a JSON string value
         *
         * @param value the JSON value
         * @return the enum member
         * @throws IllegalArgumentException if no matching enum was found
         */
        public T parse(String value) {
            T result = this.lookupTable.get(value);
            if (result == null) {
                throw new NoSuchElementException("Invalid enum '" + value + "'");
            }
            return result;
        }
    }
}
