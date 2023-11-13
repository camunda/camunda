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

package org.opensearch.client.transport.endpoints;

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.JsonpSerializer;
import org.opensearch.client.json.JsonpUtils;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for dictionary responses, i.e. a series of key/value pairs.
 */
public abstract class DictionaryResponse<TKey, TValue> implements JsonpSerializable {
    private final Map<String, TValue> result;

    @Nullable
    private final JsonpSerializer<TKey> tKeySerializer;

    @Nullable
    private final JsonpSerializer<TValue> tValueSerializer;

    // ---------------------------------------------------------------------------------------------

    protected DictionaryResponse(AbstractBuilder<TKey, TValue, ?> builder) {

        this.result = builder.result;
        this.tKeySerializer = builder.tKeySerializer;
        this.tValueSerializer = builder.tValueSerializer;

    }

    /**
     * Returns the response as a map.
     */
    public Map<String, TValue> result() {
        return this.result == null ? Collections.emptyMap() : result;
    }

    /**
     *
     */
    public TValue get(String key) {
        return this.result == null ? null : result.get(key);
    }

    /**
     * Serialize this value to JSON.
     */
    public void serialize(JsonGenerator generator, JsonpMapper mapper) {
        generator.writeStartObject();
        this.serializeInternal(generator, mapper);
        generator.writeEnd();
    }

    protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {
        for (Map.Entry<String, TValue> item0 : this.result.entrySet()) {
            generator.writeKey(item0.getKey());
            JsonpUtils.serialize(item0.getValue(), generator, tValueSerializer, mapper);
        }
    }

    protected abstract static class AbstractBuilder<TKey, TValue, BuilderT extends AbstractBuilder<TKey, TValue, BuilderT>>
        extends ObjectBuilderBase {

        private Map<String, TValue> result;

        @Nullable
        private JsonpSerializer<TKey> tKeySerializer;

        @Nullable
        private JsonpSerializer<TValue> tValueSerializer;

        /**
         * Response result.
         */
        public BuilderT result(Map<String, TValue> value) {
            this.result = value;
            return self();
        }

        /**
         * Add a key/value to {@link #result(Map)}, creating the map if needed.
         */
        public BuilderT putResult(String key, TValue value) {
            if (this.result == null) {
                this.result = new HashMap<>();
            }
            this.result.put(key, value);
            return self();
        }

        /**
         * Serializer for TKey. If not set, an attempt will be made to find a serializer
         * from the JSON context.
         *
         */
        public BuilderT tKeySerializer(@Nullable JsonpSerializer<TKey> value) {
            this.tKeySerializer = value;
            return self();
        }

        /**
         * Serializer for TValue. If not set, an attempt will be made to find a
         * serializer from the JSON context.
         *
         */
        public BuilderT tValueSerializer(@Nullable JsonpSerializer<TValue> value) {
            this.tValueSerializer = value;
            return self();
        }

        protected abstract BuilderT self();

    }

    // ---------------------------------------------------------------------------------------------
    protected static <TKey, TValue, BuilderT extends AbstractBuilder<TKey, TValue, BuilderT>> void setupDictionaryResponseDeserializer(
        ObjectDeserializer<BuilderT> op, JsonpDeserializer<TKey> tKeyParser,
        JsonpDeserializer<TValue> tValueParser) {

        op.setUnknownFieldHandler((builder, name, parser, params) -> {
            builder.putResult(name, tValueParser.deserialize(parser, params));
        });
    }
}
