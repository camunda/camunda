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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ObjectBuilderBase {
    private boolean _used = false;

    protected void _checkSingleUse() {
        if (this._used) {
            throw new IllegalStateException("Object builders can only be used once");
        }
        this._used = true;
    }

    //----- List utilities

    /** A private extension of ArrayList so that we can recognize our own creations */
    static final class InternalList<T> extends ArrayList<T> {
        InternalList() {
        }

        InternalList(Collection<? extends T> c) {
            super(c);
        }
    };

    /** Get a mutable list from the current list value of an object builder property */
    private static <T> List<T> _mutableList(List<T> list) {
        if (list == null) {
            return new InternalList<>();
        } else if (list instanceof InternalList) {
            return list;
        } else {
            // Adding to a list we don't own: make a defensive copy, also ensuring it is mutable.
            return new InternalList<>(list);
        }
    }

    /** Add a value to a (possibly {@code null}) list */
    @SafeVarargs
    protected static <T> List<T> _listAdd(List<T> list, T value, T... values) {
        list = _mutableList(list);
        list.add(value);
        if (values.length > 0) {
            list.addAll(Arrays.asList(values));
        }
        return list;
    }

    /** Add all elements of a list to a (possibly {@code null}) list */
    protected static <T> List<T> _listAddAll(List<T> list, List<T> values) {
        if (list == null) {
            // Keep the original list to avoid an unnecessary copy.
            // It will be copied if we add more values.
            return Objects.requireNonNull(values);
        } else {
            list = _mutableList(list);
            list.addAll(values);
            return list;
        }
    }

    //----- Map utilities

    /** A private extension of HashMap so that we can recognize our own creations */
    private static final class InternalMap<K, V> extends HashMap<K, V> {
        InternalMap() {
        }

        InternalMap(Map<? extends K, ? extends V> m) {
            super(m);
        }
    }

    /** Get a mutable map from the current map value of an object builder property */
    private static <K, V> Map<K, V> _mutableMap(Map<K, V> map) {
        if (map == null) {
            return new InternalMap<>();
        } else if (map instanceof InternalMap) {
            return map;
        } else {
            // Adding to a map we don't own: make a defensive copy, also ensuring it is mutable.
            return new InternalMap<>(map);
        }
    }

    /** Add a value to a (possibly {@code null}) map */
    protected static <K, V> Map<K, V> _mapPut(Map<K, V> map, K key, V value) {
        map = _mutableMap(map);
        map.put(key, value);
        return map;
    }

    /** Add all elements of a list to a (possibly {@code null}) map */
    protected static <K, V> Map<K, V> _mapPutAll(Map<K, V> map, Map<K, V> entries) {
        if (map == null) {
            // Keep the original map to avoid an unnecessary copy.
            // It will be copied if we add more entries.
            return Objects.requireNonNull(entries);
        } else {
            map = _mutableMap(map);
            map.putAll(entries);
            return map;
        }
    }
}
