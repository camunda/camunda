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

import jakarta.json.JsonNumber;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for {@link JsonpDeserializer} implementations that accept a set of JSON events known at instanciation time.
 */
public abstract class JsonpDeserializerBase<V> implements JsonpDeserializer<V> {

    private final EnumSet<Event> acceptedEvents;
    private final EnumSet<Event> nativeEvents;

    protected JsonpDeserializerBase(EnumSet<Event> acceptedEvents) {
        this(acceptedEvents, acceptedEvents);
    }

    protected JsonpDeserializerBase(EnumSet<Event> acceptedEvents, EnumSet<Event> nativeEvents) {
        this.acceptedEvents = acceptedEvents;
        this.nativeEvents = nativeEvents;
    }

    /** Combines accepted events from a number of deserializers */
    protected static EnumSet<Event> allAcceptedEvents(JsonpDeserializer<?>... deserializers) {
        EnumSet<Event> result = EnumSet.noneOf(Event.class);
        for (JsonpDeserializer<?> deserializer: deserializers) {

            EnumSet<Event> set = deserializer.acceptedEvents();
            // Disabled for now. Only happens with the experimental Union2 and is caused by string and number
            // parsers leniency. Need to be replaced with a check on a preferred event type.
            //if (!Collections.disjoint(result, set)) {
            //    throw new IllegalArgumentException("Deserializer accepted events are not disjoint");
            //}

            result.addAll(set);
        }
        return result;
    }

    @Override
    public EnumSet<Event> nativeEvents() {
        return nativeEvents;
    }

    /**
     * The JSON events this deserializer accepts as a starting point
     */
    public final EnumSet<Event> acceptedEvents() {
        return acceptedEvents;
    }

    /**
     * Convenience method for {@code acceptedEvents.contains(event)}
     */
    public final boolean accepts(Event event) {
        return acceptedEvents.contains(event);
    }

    //---------------------------------------------------------------------------------------------

    //----- Builtin types

    static final JsonpDeserializer<String> STRING =
        // String parsing is lenient and accepts any other primitive type
        new JsonpDeserializerBase<String>(EnumSet.of(
                Event.KEY_NAME, Event.VALUE_STRING, Event.VALUE_NUMBER,
                Event.VALUE_FALSE, Event.VALUE_TRUE
            ),
            EnumSet.of(Event.VALUE_STRING)
        ) {
            @Override
            public String deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                if (event == Event.VALUE_TRUE) {
                    return "true";
                }
                if (event == Event.VALUE_FALSE) {
                    return "false";
                }
                return parser.getString(); // also accepts numbers
            }
        };

    static final JsonpDeserializer<Integer> INTEGER =
        new JsonpDeserializerBase<Integer>(
            EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_STRING),
            EnumSet.of(Event.VALUE_NUMBER)
        ) {
            @Override
            public Integer deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                if (event == Event.VALUE_STRING) {
                    return Integer.valueOf(parser.getString());
                }
                return parser.getInt();
            }
        };

    static final JsonpDeserializer<Boolean> BOOLEAN =
        new JsonpDeserializerBase<Boolean>(
            EnumSet.of(Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_STRING),
            EnumSet.of(Event.VALUE_FALSE, Event.VALUE_TRUE)
        ) {
            @Override
            public Boolean deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                if (event == Event.VALUE_STRING) {
                    return Boolean.parseBoolean(parser.getString());
                } else {
                    return event == Event.VALUE_TRUE;
                }
            }
        };

    static final JsonpDeserializer<Long> LONG =
        new JsonpDeserializerBase<Long>(
            EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_STRING),
            EnumSet.of(Event.VALUE_NUMBER)
        ) {
            @Override
            public Long deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                if (event == Event.VALUE_STRING) {
                    return Long.valueOf(parser.getString());
                }
                return parser.getLong();
            }
        };

    static final JsonpDeserializer<Float> FLOAT =
        new JsonpDeserializerBase<Float>(
            EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_STRING),
            EnumSet.of(Event.VALUE_NUMBER)

        ) {
            @Override
            public Float deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                if (event == Event.VALUE_STRING) {
                    return Float.valueOf(parser.getString());
                }
                return parser.getBigDecimal().floatValue();
            }
        };

    static final JsonpDeserializer<Double> DOUBLE =
        new JsonpDeserializerBase<Double>(
            EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_STRING),
            EnumSet.of(Event.VALUE_NUMBER)
        ) {
            @Override
            public Double deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                if (event == Event.VALUE_STRING) {
                    return Double.valueOf(parser.getString());
                }
                return parser.getBigDecimal().doubleValue();
            }
        };

    static final class DoubleOrNullDeserializer extends JsonpDeserializerBase<Double> {
        static final EnumSet<Event> nativeEvents = EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_NULL);
        static final EnumSet<Event> acceptedEvents = EnumSet.of(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_NULL);
        private final double defaultValue;

        DoubleOrNullDeserializer(double defaultValue) {
            super(acceptedEvents, nativeEvents);
            this.defaultValue = defaultValue;
        }

        @Override
        public Double deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
            if (event == Event.VALUE_NULL) {
                return defaultValue;
            }
            if (event == Event.VALUE_STRING) {
                return Double.valueOf(parser.getString());
            }
            return parser.getBigDecimal().doubleValue();
        }
    }

    static final class IntOrNullDeserializer extends JsonpDeserializerBase<Integer> {
        static final EnumSet<Event> nativeEvents = EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_NULL);
        static final EnumSet<Event> acceptedEvents = EnumSet.of(Event.VALUE_STRING, Event.VALUE_NUMBER, Event.VALUE_NULL);
        private final int defaultValue;

        IntOrNullDeserializer(int defaultValue) {
            super(acceptedEvents, nativeEvents);
            this.defaultValue = defaultValue;
        }

        @Override
        public Integer deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
            if (event == Event.VALUE_NULL) {
                return defaultValue;
            }
            if (event == Event.VALUE_STRING) {
                return Integer.valueOf(parser.getString());
            }
            return parser.getInt();
        }
    }

    static final class StringOrNullDeserializer extends JsonpDeserializerBase<String> {
        static final EnumSet<Event> nativeEvents = EnumSet.of(Event.VALUE_STRING, Event.VALUE_NULL);
        static final EnumSet<Event> acceptedEvents = EnumSet.of(Event.KEY_NAME, Event.VALUE_STRING,
            Event.VALUE_NUMBER, Event.VALUE_FALSE, Event.VALUE_TRUE, Event.VALUE_NULL);

        StringOrNullDeserializer() {
            super(acceptedEvents, nativeEvents);
        }

        @Override
        public String deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
            if (event == Event.VALUE_NULL) {
                return null;
            }
            if (event == Event.VALUE_TRUE) {
                return "true";
            }
            if (event == Event.VALUE_FALSE) {
                return "false";
            }
            return parser.getString();
        }
    }

    static final JsonpDeserializer<Double> DOUBLE_OR_NAN =
        new JsonpDeserializerBase<Double>(
            EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_STRING, Event.VALUE_NULL),
            EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_NULL)
        ) {
            @Override
            public Double deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                if (event == Event.VALUE_NULL) {
                    return Double.NaN;
                }
                if (event == Event.VALUE_STRING) {
                    return Double.valueOf(parser.getString());
                }
                return parser.getBigDecimal().doubleValue();
            }
        };

    static final JsonpDeserializer<Number> NUMBER =
        new JsonpDeserializerBase<Number>(
            EnumSet.of(Event.VALUE_NUMBER, Event.VALUE_STRING),
            EnumSet.of(Event.VALUE_NUMBER)
        ) {
            @Override
            public Number deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                if (event == Event.VALUE_STRING) {
                    return Double.valueOf(parser.getString());
                }
                return ((JsonNumber)parser.getValue()).numberValue();
            }
        };

    static final JsonpDeserializer<JsonValue> JSON_VALUE =
        new JsonpDeserializerBase<JsonValue>(
            EnumSet.allOf(Event.class)
        ) {
            @Override
            public JsonValue deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
                return parser.getValue();
            }
        };

    static final JsonpDeserializer<Void> VOID = new JsonpDeserializerBase<Void>(
        EnumSet.noneOf(Event.class)
    ) {
        @Override
        public Void deserialize(JsonParser parser, JsonpMapper mapper) {
            throw new JsonParsingException("Void types should not have any value", parser.getLocation());
        }

        @Override
        public Void deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
            return deserialize(parser, mapper);
        }
    };

    //----- Collections

    static class ArrayDeserializer<T> implements JsonpDeserializer<List<T>> {
        private final JsonpDeserializer<T> itemDeserializer;
        private EnumSet<Event> acceptedEvents;
        private static final EnumSet<Event> nativeEvents = EnumSet.of(Event.START_ARRAY);

        protected ArrayDeserializer(JsonpDeserializer<T> itemDeserializer) {
            this.itemDeserializer = itemDeserializer;
        }

        @Override
        public EnumSet<Event> nativeEvents() {
            return nativeEvents;
        }

        @Override
        public EnumSet<Event> acceptedEvents() {
            // Accepted events is computed lazily
            // no need for double-checked lock, we don't care about computing it several times
            if (acceptedEvents == null) {
                acceptedEvents = EnumSet.of(Event.START_ARRAY);
                acceptedEvents.addAll(itemDeserializer.acceptedEvents());
            }
            return acceptedEvents;
        }

        @Override
        public List<T> deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
            if (event == Event.START_ARRAY) {
                List<T> result = new ArrayList<>();
                while ((event = parser.next()) != Event.END_ARRAY) {
                    JsonpUtils.ensureAccepts(itemDeserializer, parser, event);
                    result.add(itemDeserializer.deserialize(parser, mapper, event));
                }
                return result;
            } else {
                // Single-value mode
                JsonpUtils.ensureAccepts(itemDeserializer, parser, event);
                return Collections.singletonList(itemDeserializer.deserialize(parser, mapper, event));
            }
        }
    }

    static class StringMapDeserializer<T> extends JsonpDeserializerBase<Map<String, T>> {
        private final JsonpDeserializer<T> itemDeserializer;

        protected StringMapDeserializer(JsonpDeserializer<T> itemDeserializer) {
            super(EnumSet.of(Event.START_OBJECT));
            this.itemDeserializer = itemDeserializer;
        }

        @Override
        public Map<String, T> deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
            Map<String, T> result = new HashMap<>();
            while ((event = parser.next()) != Event.END_OBJECT) {
                JsonpUtils.expectEvent(parser, Event.KEY_NAME, event);
                String key = parser.getString();
                T value = itemDeserializer.deserialize(parser, mapper);
                result.put(key, value);
            }
            return result;
        }
    }

    static class EnumMapDeserializer<K, V> extends JsonpDeserializerBase<Map<K, V>> {
        private final JsonpDeserializer<K> keyDeserializer;
        private final JsonpDeserializer<V> valueDeserializer;

        protected EnumMapDeserializer(JsonpDeserializer<K> keyDeserializer, JsonpDeserializer<V> valueDeserializer) {
            super(EnumSet.of(Event.START_OBJECT));
            this.keyDeserializer = keyDeserializer;
            this.valueDeserializer = valueDeserializer;
        }

        @Override
        public Map<K, V> deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
            Map<K, V> result = new HashMap<>();
            while ((event = parser.next()) != Event.END_OBJECT) {
                JsonpUtils.expectEvent(parser, Event.KEY_NAME, event);
                K key = keyDeserializer.deserialize(parser, mapper, event);
                V value = valueDeserializer.deserialize(parser, mapper);
                result.put(key, value);
            }
            return result;
        }
    }
}
