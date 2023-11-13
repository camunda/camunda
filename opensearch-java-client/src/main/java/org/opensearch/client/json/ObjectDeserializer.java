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

import org.opensearch.client.util.QuadConsumer;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParser.Event;
import jakarta.json.stream.JsonParsingException;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;

public class ObjectDeserializer<ObjectType> implements JsonpDeserializer<ObjectType> {

    /** A field deserializer parses a value and calls the setter on the target object. */
    public abstract static class FieldDeserializer<ObjectType> {
        protected final String name;

        public FieldDeserializer(String name) {
            this.name = name;
        }

        public abstract void deserialize(JsonParser parser, JsonpMapper mapper, String fieldName, ObjectType object);

        public abstract void deserialize(JsonParser parser, JsonpMapper mapper, String fieldName, ObjectType object, Event event);
    }

    /** Field deserializer for objects (and boxed primitives) */
    public static class FieldObjectDeserializer<ObjectType, FieldType> extends FieldDeserializer<ObjectType> {
        private final BiConsumer<ObjectType, FieldType> setter;
        private final JsonpDeserializer<FieldType> deserializer;

        public FieldObjectDeserializer(
            BiConsumer<ObjectType, FieldType> setter, JsonpDeserializer<FieldType> deserializer,
            String name
        ) {
            super(name);
            this.setter = setter;
            this.deserializer = deserializer;
        }

        public String name() {
            return this.name;
        }

        public void deserialize(JsonParser parser, JsonpMapper mapper, String fieldName, ObjectType object) {
            FieldType fieldValue = deserializer.deserialize(parser, mapper);
            setter.accept(object, fieldValue);
        }

        public void deserialize(JsonParser parser, JsonpMapper mapper, String fieldName, ObjectType object, Event event) {
            JsonpUtils.ensureAccepts(deserializer, parser, event);
            FieldType fieldValue = deserializer.deserialize(parser, mapper, event);
            setter.accept(object, fieldValue);
        }
    }

    private static final FieldDeserializer<?> IGNORED_FIELD = new FieldDeserializer<Object>("-") {

        @Override
        public void deserialize(JsonParser parser, JsonpMapper mapper, String fieldName, Object object) {
            JsonpUtils.skipValue(parser);
        }

        @Override
        public void deserialize(JsonParser parser, JsonpMapper mapper, String fieldName, Object object, Event event) {
            JsonpUtils.skipValue(parser, event);
        }
    };

    //---------------------------------------------------------------------------------------------
    private static final EnumSet<Event> EventSetObject = EnumSet.of(Event.START_OBJECT, Event.KEY_NAME);
    private static final EnumSet<Event> EventSetObjectAndString = EnumSet.of(Event.START_OBJECT, Event.VALUE_STRING, Event.KEY_NAME);

    private EnumSet<Event> acceptedEvents = EventSetObject; // May be changed in `shortcutProperty()`
    private final Supplier<ObjectType> constructor;
    protected final Map<String, FieldDeserializer<ObjectType>> fieldDeserializers;
    private FieldDeserializer<ObjectType> singleKey;
    private String typeProperty;
    private String defaultType;
    private FieldDeserializer<ObjectType> shortcutProperty;
    private QuadConsumer<ObjectType, String, JsonParser, JsonpMapper> unknownFieldHandler;

    public ObjectDeserializer(Supplier<ObjectType> constructor) {
        this.constructor = constructor;
        this.fieldDeserializers = new HashMap<>();
    }

    /**
     * Return the top-level property names of the target type for this deserializer.
     */
    public Set<String> fieldNames() {
        return Collections.unmodifiableSet(fieldDeserializers.keySet());
    }

    public @Nullable String shortcutProperty() {
        return this.shortcutProperty == null ? null : this.shortcutProperty.name;
    }

    @Override
    public EnumSet<Event> nativeEvents() {
        // May also return string if we have a shortcut property. This is needed to identify ambiguous unions.
        return acceptedEvents;
    }

    @Override
    public EnumSet<Event> acceptedEvents() {
        return acceptedEvents;
    }

    public ObjectType deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
        return deserialize(constructor.get(), parser, mapper, event);
    }

    public ObjectType deserialize(ObjectType value, JsonParser parser, JsonpMapper mapper, Event event) {
        if (event == Event.VALUE_NULL) {
            return null;
        }

        if (singleKey != null) {
            // There's a wrapping property whose name is the key value
            if (event == Event.START_OBJECT) {
                event = JsonpUtils.expectNextEvent(parser, Event.KEY_NAME);
            }
            singleKey.deserialize(parser, mapper, null, value, event);
            event = parser.next();
        }

        if (shortcutProperty != null && event != Event.START_OBJECT && event != Event.KEY_NAME) {
            // This is the shortcut property (should be a value event, this will be checked by its deserializer)
            shortcutProperty.deserialize(parser, mapper, shortcutProperty.name, value, event);

        } else if (typeProperty == null) {
            if (event != Event.START_OBJECT && event != Event.KEY_NAME) {
                // Report we're waiting for a start_object, since this is the most common beginning for object parser
                JsonpUtils.expectEvent(parser, Event.START_OBJECT, event);
            }

            if (event == Event.START_OBJECT) {
                event = parser.next();
            }
            // Regular object: read all properties until we reach the end of the object
            while (event != Event.END_OBJECT) {
                JsonpUtils.expectEvent(parser, Event.KEY_NAME, event);
                String fieldName = parser.getString();

                FieldDeserializer<ObjectType> fieldDeserializer = fieldDeserializers.get(fieldName);
                if (fieldDeserializer == null) {
                    parseUnknownField(parser, mapper, fieldName, value);
                } else {
                    fieldDeserializer.deserialize(parser, mapper, fieldName, value);
                }
                event = parser.next();
            }
        } else {
            // Union variant: find the property to find the proper deserializer
            // We cannot start with a key name here.
            JsonpUtils.expectEvent(parser, Event.START_OBJECT, event);
            Map.Entry<String, JsonParser> unionInfo = JsonpUtils.lookAheadFieldValue(typeProperty, defaultType, parser, mapper);
            String variant = unionInfo.getKey();
            JsonParser innerParser = unionInfo.getValue();

            FieldDeserializer<ObjectType> fieldDeserializer = fieldDeserializers.get(variant);
            if (fieldDeserializer == null) {
                parseUnknownField(parser, mapper, variant, value);
            } else {
                fieldDeserializer.deserialize(innerParser, mapper, variant, value);
            }
        }

        if (singleKey != null) {
            JsonpUtils.expectNextEvent(parser, Event.END_OBJECT);
        }

        return value;
    }

    protected void parseUnknownField(JsonParser parser, JsonpMapper mapper, String fieldName, ObjectType object) {
        if (this.unknownFieldHandler != null) {
            this.unknownFieldHandler.accept(object, fieldName, parser, mapper);

        } else if (mapper.ignoreUnknownFields()) {
            JsonpUtils.skipValue(parser);

        } else {
            throw new JsonParsingException(
                "Unknown field '" + fieldName + "' for type '" + object.getClass().getName() +"'",
                parser.getLocation()
            );
        }
    }

    public void setUnknownFieldHandler(QuadConsumer<ObjectType, String, JsonParser, JsonpMapper> unknownFieldHandler) {
        this.unknownFieldHandler = unknownFieldHandler;
    }

    @SuppressWarnings("unchecked")
    public void ignore(String name) {
        this.fieldDeserializers.put(name, (FieldDeserializer<ObjectType>) IGNORED_FIELD);
    }

    public void shortcutProperty(String name) {
        this.shortcutProperty = this.fieldDeserializers.get(name);
        if (this.shortcutProperty == null) {
            throw new NoSuchElementException("No deserializer was setup for '" + name + "'");
        }

        acceptedEvents = EventSetObjectAndString;
    }

    //----- Object types

    public <FieldType> void add(
        BiConsumer<ObjectType, FieldType> setter,
        JsonpDeserializer<FieldType> deserializer,
        String name
    ) {
        FieldObjectDeserializer<ObjectType, FieldType> fieldDeserializer =
            new FieldObjectDeserializer<>(setter, deserializer, name);
        this.fieldDeserializers.put(name, fieldDeserializer);
    }

    public <FieldType> void add(
        BiConsumer<ObjectType, FieldType> setter,
        JsonpDeserializer<FieldType> deserializer,
        String name, String... aliases
    ) {
        FieldObjectDeserializer<ObjectType, FieldType> fieldDeserializer =
            new FieldObjectDeserializer<>(setter, deserializer, name);
        this.fieldDeserializers.put(name, fieldDeserializer);
        for (String alias: aliases) {
            this.fieldDeserializers.put(alias, fieldDeserializer);
        }
    }

    public <FieldType> void setKey(BiConsumer<ObjectType, FieldType> setter, JsonpDeserializer<FieldType> deserializer) {
        this.singleKey = new FieldObjectDeserializer<>(setter, deserializer, null);
    }

    public void setTypeProperty(String name, String defaultType) {
        this.typeProperty = name;
        this.defaultType = defaultType;
    }

    //----- Primitive types

    public void add(ObjIntConsumer<ObjectType> setter, String name, String... deprecatedNames) {
        // FIXME (perf): add specialized deserializer to avoid intermediate boxing
        add(setter::accept, JsonpDeserializer.integerDeserializer(), name, deprecatedNames);
    }

// Experiment: avoid boxing, allow multiple primitive parsers (e.g. int as number & string)
//    public void add(
//        ObjIntConsumer<ObjectType> setter,
//        JsonpIntParser vp,
//        String name, String... deprecatedNames
//    ) {
//        this.fieldDeserializers.put(name, new FieldDeserializer<ObjectType>(name, deprecatedNames) {
//            @Override
//            public void deserialize(JsonParser parser, JsonpMapper mapper, String fieldName, ObjectType object) {
//                JsonpUtils.expectNextEvent(parser, Event.VALUE_NUMBER);
//                setter.accept(object, vp.parse(parser));
//            }
//        });
//    }
//
//    public static class JsonpIntParser {
//        public int parse(JsonParser parser) {
//            JsonpUtils.expectNextEvent(parser, Event.VALUE_NUMBER);
//            return parser.getInt();
//        }
//    }

}
