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

import org.opensearch.client.util.TaggedUnion;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import jakarta.json.stream.JsonParsingException;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import static jakarta.json.stream.JsonParser.Event;

/**
 * Utilities for union types whose discriminant is not directly part of the structure, either as an enclosing property name or as
 * an inner property. This is used for Elasticsearch aggregation results and suggesters, using the {@code typed_keys} parameter that
 * encodes a name+type in a single JSON property.
 *
 */
public interface ExternallyTaggedUnion {

    /**
     * A deserializer for externally-tagged unions. Since the union variant discriminant is provided externally, this cannot be a
     * regular {@link JsonpDeserializer} as the caller has to provide the discriminant value.
     */
    class Deserializer<Union extends TaggedUnion<?, Member>, Member> {
        private final Map<String, JsonpDeserializer<? extends Member>> deserializers;
        private final BiFunction<String, Member, Union> unionCtor;

        public Deserializer(Map<String, JsonpDeserializer<? extends Member>> deserializers, BiFunction<String, Member, Union> unionCtor) {
            this.deserializers = deserializers;
            this.unionCtor = unionCtor;
        }

        /**
         * Deserialize a union value, given its type.
         */
        public Union deserialize(String type, JsonParser parser, JsonpMapper mapper) {
            JsonpDeserializer<? extends Member> deserializer = deserializers.get(type);
            if (deserializer == null) {
                throw new JsonParsingException("Unknown variant type '" + type + "'", parser.getLocation());
            }

            return unionCtor.apply(type, deserializer.deserialize(parser, mapper));
        }

        /**
         * Deserialize an externally tagged union encoded as typed keys, a JSON dictionary whose property names combine type and name
         * in a single string.
         */
        public TypedKeysDeserializer<Union> typedKeys() {
            return new TypedKeysDeserializer<>(this);
        }
    }

    class TypedKeysDeserializer<Union extends TaggedUnion<?, ?>> extends JsonpDeserializerBase<Map<String, Union>> {
        Deserializer<Union, ?> deserializer;
        protected TypedKeysDeserializer(Deserializer<Union, ?> deser) {
            super(EnumSet.of(Event.START_OBJECT));
            this.deserializer = deser;
        }

        @Override
        public Map<String, Union> deserialize(JsonParser parser, JsonpMapper mapper, Event event) {
            Map<String, Union> result = new HashMap<>();
            while ((event = parser.next()) != Event.END_OBJECT) {
                JsonpUtils.expectEvent(parser, event, Event.KEY_NAME);
                deserializeEntry(parser.getString(), parser, mapper, result);
            }
            return result;
        }

        public void deserializeEntry(String key, JsonParser parser, JsonpMapper mapper, Map<String, Union> targetMap) {
            int hashPos = key.indexOf('#');
            if (hashPos == -1) {
                throw new JsonParsingException(
                    "Property name '" + key + "' is not in the 'type#name' format. Make sure the request has 'typed_keys' set.",
                    parser.getLocation()
                );
            }

            String type = key.substring(0, hashPos);
            String name = key.substring(hashPos + 1);

            targetMap.put(name, deserializer.deserialize(type, parser, mapper));
        }
    }

    /**
     * Serialize an externally tagged union using the typed keys encoding.
     */
    static <T extends JsonpSerializable & TaggedUnion<? extends JsonEnum, ?>> void serializeTypedKeys(
        Map<String, T> map, JsonGenerator generator, JsonpMapper mapper
    ) {
        generator.writeStartObject();
        serializeTypedKeysInner(map, generator, mapper);
        generator.writeEnd();
    }

    /**
     * Serialize an externally tagged union using the typed keys encoding, without the enclosing start/end object.
     */
    static <T extends JsonpSerializable & TaggedUnion<? extends JsonEnum, ?>> void serializeTypedKeysInner(
        Map<String, T> map, JsonGenerator generator, JsonpMapper mapper
    ) {
        for (Map.Entry<String, T> entry: map.entrySet()) {
            T value = entry.getValue();
            generator.writeKey(value._kind().jsonValue() + "#" + entry.getKey());
            value.serialize(generator, mapper);
        }
    }
}
