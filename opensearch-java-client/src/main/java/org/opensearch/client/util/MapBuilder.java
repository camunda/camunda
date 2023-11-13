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

package org.opensearch.client.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

public class MapBuilder<K, V, B> implements ObjectBuilder<Map<K, V>> {

    private final Map<K, V> map = new HashMap<>();
    private final Supplier<B> builderCtor;

    public MapBuilder(Supplier<B> builderCtor) {
        this.builderCtor = builderCtor;
    }

    public MapBuilder<K, V, B> put(K key, V value) {
        map.put(key, value);
        return this;
    }

    public MapBuilder<K, V, B> put(K key, Function<B, ObjectBuilder<V>> fn) {
        return put(key, fn.apply(builderCtor.get()).build());
    }

    public MapBuilder<K, V, B> putAll(Map<? extends K, ? extends V> map) {
        this.map.putAll(map);
        return this;
    }

    public MapBuilder<K, V, B> putAll(Iterable<Map.Entry<? extends K, ? extends V>> entries) {
        for (Map.Entry<? extends K, ? extends V> entry: entries) {
            this.map.put(entry.getKey(), entry.getValue());
        }
        return this;
    }

    @Override
    public Map<K, V> build() {
        return map;
    }

    public static <K, V> Map<K, V> of(K k1, V v1) {
        return Collections.singletonMap(k1, v1);
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2) {
        return makeMap(k1, v1, k2, v2);
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3) {
        return makeMap(k1, v1, k2, v2, k3, v3);
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4) {
        return makeMap(k1, v1, k2, v2, k3, v3, k4, v4);
    }

    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3, K k4, V v4, K k5, V v5) {
        return makeMap(k1, v1, k2, v2, k3, v3, k4, v4, k5, v5);
    }

    private static <K, V> Map<K, V> makeMap(Object... values) {
        Map<K, V> result = new HashMap<>(values.length/2);
        for (int i = 0; i < values.length; i+=2) {
            @SuppressWarnings("unchecked")
            K k = (K)values[i];
            @SuppressWarnings("unchecked")
            V v = (V)values[i+1];
            result.put(k, v);
        }
        return result;
    }
}
