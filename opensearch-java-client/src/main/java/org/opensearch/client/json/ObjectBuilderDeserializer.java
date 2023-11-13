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

import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonParser;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * An object deserializer based on an {@link ObjectBuilder}.
 */
public class ObjectBuilderDeserializer<T, B extends ObjectBuilder<T>> extends DelegatingDeserializer<T, B> {

    private final JsonpDeserializer<B> builderDeserializer;

    public static <B extends ObjectBuilder<T>, T> JsonpDeserializer<T> lazy(
        Supplier<B> builderCtor,
        Consumer<ObjectDeserializer<B>> builderDeserializerSetup
    ) {
        return new LazyDeserializer<>(() -> {
            ObjectDeserializer<B> builderDeser = new ObjectDeserializer<B>(builderCtor);
            builderDeserializerSetup.accept(builderDeser);
            return new ObjectBuilderDeserializer<>(builderDeser);
        });
    }

    public static <B, T> JsonpDeserializer<T> lazy(
        Supplier<B> builderCtor,
        Consumer<ObjectDeserializer<B>> builderDeserializerSetup,
        Function<B, T> buildFn
    ) {
        return new LazyDeserializer<>(() -> {
                ObjectDeserializer<B> builderDeser = new ObjectDeserializer<B>(builderCtor);
                builderDeserializerSetup.accept(builderDeser);
                return new BuildFunctionDeserializer<>(builderDeser, buildFn);
            });
    }

    public static <T, B extends ObjectBuilder<T>> JsonpDeserializer<T> createForObject(
        Supplier<B> ctor,
        Consumer<ObjectDeserializer<B>> configurer
    ) {
        ObjectDeserializer<B> op = new ObjectDeserializer<>(ctor);
        configurer.accept(op);
        return new ObjectBuilderDeserializer<>(op);
    }

    public ObjectBuilderDeserializer(JsonpDeserializer<B> builderDeserializer) {
        this.builderDeserializer = builderDeserializer;
    }

    @Override
    protected JsonpDeserializer<B> unwrap() {
        return builderDeserializer;
    }

    @Override
    public T deserialize(JsonParser parser, JsonpMapper mapper) {
        ObjectBuilder<T> builder = builderDeserializer.deserialize(parser, mapper);
        return builder.build();
    }

    @Override
    public T deserialize(JsonParser parser, JsonpMapper mapper, JsonParser.Event event) {
        ObjectBuilder<T> builder = builderDeserializer.deserialize(parser, mapper, event);
        return builder.build();
    }
}
