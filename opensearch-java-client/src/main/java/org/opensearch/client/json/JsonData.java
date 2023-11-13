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
import jakarta.json.stream.JsonParser;

import java.util.EnumSet;

/**
 * A raw JSON value. It can be converted to a JSON node tree or to an arbitrary object using a {@link JsonpMapper}.
 * <p>
 * This type is used in API types for values that don't have a statically-defined type or that cannot be represented
 * as a generic parameter of the enclosing data structure.
 * <p>
 * Instances of this class returned by API clients keep a reference to the client's {@link JsonpMapper} and can be
 * converted to arbitrary types using {@link #to(Class)} without requiring an explicit mapper.
 */
@JsonpDeserializable
public interface JsonData extends JsonpSerializable {

    /**
     * Converts this object to a JSON node tree. A mapper must have been provided at creation time.
     *
     * @throws IllegalStateException if no mapper was provided at creation time.
     */
    JsonValue toJson();

    /**
     * Converts this object to a JSON node tree.
     */
    JsonValue toJson(JsonpMapper mapper);

    /**
     * Converts this object to a target class. A mapper must have been provided at creation time.
     *
     * @throws IllegalStateException if no mapper was provided at creation time.
     */
    <T> T to(Class<T> clazz);

    /**
     * Converts this object to a target class.
     */
     <T> T to(Class<T> clazz, JsonpMapper mapper);

    /**
     * Converts this object using a deserializer. A mapper must have been provided at creation time.
     *
     * @throws IllegalStateException if no mapper was provided at creation time.
     */
     <T> T deserialize(JsonpDeserializer<T> deserializer);

    /**
     * Converts this object using a deserializer.
     */
     <T> T deserialize(JsonpDeserializer<T> deserializer, JsonpMapper mapper);

    /**
     * Creates a raw JSON value from an existing object. A mapper will be needed to convert the result.
     */
    static <T> JsonData of(T value) {
        return new JsonDataImpl(value, null);
    }

    /**
     * Creates a raw JSON value from an existing object, along with the mapper to use for further conversions.
     */
    static <T> JsonData of(T value, JsonpMapper mapper) {
        return new JsonDataImpl(value, mapper);
    }

    /**
     * Creates a raw JSON value from a parser. The provider mapper will be used for conversions unless one is
     * explicitly provided using {@link #to(Class, JsonpMapper)}, {@link #toJson(JsonpMapper)} or
     * {@link #deserialize(JsonpDeserializer)}.
     */
    static JsonData from(JsonParser parser, JsonpMapper mapper) {
        parser.next(); // Need to be at the beginning of the value to read
        return of(parser.getValue(), mapper);
    }

    JsonpDeserializer<JsonData> _DESERIALIZER = JsonpDeserializer.of(
        EnumSet.allOf(JsonParser.Event.class), JsonData::from
    );
}
