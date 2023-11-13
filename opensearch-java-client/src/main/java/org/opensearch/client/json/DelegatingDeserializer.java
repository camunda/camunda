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

import java.util.EnumSet;

public abstract class DelegatingDeserializer<T, U> implements JsonpDeserializer<T> {

    protected abstract JsonpDeserializer<U> unwrap();

    @Override
    public EnumSet<JsonParser.Event> nativeEvents() {
        return unwrap().nativeEvents();
    }

    @Override
    public EnumSet<JsonParser.Event> acceptedEvents() {
        return unwrap().acceptedEvents();
    }

    public abstract static class SameType<T> extends DelegatingDeserializer<T, T> {
        @Override
        public T deserialize(JsonParser parser, JsonpMapper mapper) {
            return unwrap().deserialize(parser, mapper);
        }

        @Override
        public T deserialize(JsonParser parser, JsonpMapper mapper, JsonParser.Event event) {
            return unwrap().deserialize(parser, mapper, event);
        }
    }

    /**
     * Unwraps a deserializer. The object type of the result may be different from that of {@code deserializer}
     * and unwrapping can happen several times, until the result is no more a {@code DelegatingDeserializer}.
     */
    public static JsonpDeserializer<?> unwrap(JsonpDeserializer<?> deserializer) {
        while (deserializer instanceof DelegatingDeserializer) {
            deserializer = ((DelegatingDeserializer<?,?>) deserializer).unwrap();
        }
        return deserializer;
    }
}
