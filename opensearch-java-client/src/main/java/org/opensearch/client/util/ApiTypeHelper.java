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

import javax.annotation.Nullable;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility functions for API model types
 */
public class ApiTypeHelper {

    private ApiTypeHelper() {}

    //----- Required properties

    private static final ThreadLocal<Boolean> disableRequiredPropertiesCheck = new ThreadLocal<>();

    public static boolean requiredPropertiesCheckDisabled() {
        return disableRequiredPropertiesCheck.get() == Boolean.TRUE;
    }

    public static class DisabledChecksHandle implements AutoCloseable {
        private final Boolean value;

        public DisabledChecksHandle(Boolean value) {
            this.value = value;
        }
        @Override
        public void close() {
            disableRequiredPropertiesCheck.set(value);
        }
    }

    /**
     * DANGEROUS! Allows disabling the verification of required properties on the current thread when calling {@link ObjectBuilder#build()}.
     * This can lead properties expected to be always present to be {@code null}, or have the default value for primitive types.
     * <p>
     * This can be used as a workaround for properties that are erroneously marked as required.
     * <p>
     * The result of this method is an {@link AutoCloseable} handle that can be used in try-with-resource blocks to precisely
     * limit the scope where checks are disabled.
     */
    public static DisabledChecksHandle DANGEROUS_disableRequiredPropertiesCheck(boolean disable) {
        DisabledChecksHandle result = new DisabledChecksHandle(disableRequiredPropertiesCheck.get());
        disableRequiredPropertiesCheck.set(disable);
        return result;
    }

    public static <T> T requireNonNull(T value, Object obj, String name) {
        if (value == null && !requiredPropertiesCheckDisabled()) {
            throw new MissingRequiredPropertyException(obj, name);
        }
        return value;
    }

    //----- Lists

    /** Immutable empty list implementation so that we can create marker list objects */
    static class EmptyList extends AbstractList<Object> {
        @Override
        public Object get(int index) {
            throw new IndexOutOfBoundsException();
        }

        @Override
        public int size() {
            return 0;
        }

        @Override
        public Iterator<Object> iterator() {
            return Collections.emptyIterator();
        }
    };

    static final List<Object> UNDEFINED_LIST = new EmptyList();

    /**
     * Returns an empty list that is undefined from a JSON perspective. It will not be serialized
     * when used as the value of an array property in API objects.
     */
    @SuppressWarnings("unchecked")
    public static <T> List<T> undefinedList() {
        return (List<T>)UNDEFINED_LIST;
    }

    /**
     * Is {@code list} defined according to JSON/JavaScript semantics?
     *
     * @return true if {@code list} is not {@code null} and not {@link #undefinedList()}
     */
    public static <T> boolean isDefined(List<T> list) {
        return list != null && list != UNDEFINED_LIST;
    }

    /**
     * Returns an unmodifiable view of a list. If {@code list} is {@code null}, an {@link #undefinedList()} is returned.
     */
    public static <T> List<T> unmodifiable(@Nullable List<T> list) {
        if (list == null) {
            return undefinedList();
        }
        if (list == UNDEFINED_LIST) {
            return list;
        }
        return Collections.unmodifiableList(list);
    }

    /**
     * Returns an unmodifiable view of a required list.
     */
    public static <T> List<T> unmodifiableRequired(List<T> list, Object obj, String name) {
        // We only check that the list is defined and not that it has some elements, as empty required
        // lists may have a meaning in some APIs. Furthermore, being defined means that it was set by
        // the application, so it's not an omission.
        requireNonNull(list == UNDEFINED_LIST ? null : list, obj, name);
        return Collections.unmodifiableList(list);
    }

    //----- Maps

    static class EmptyMap extends AbstractMap<Object, Object> {
        @Override
        public Set<Entry<Object, Object>> entrySet() {
            return Collections.emptySet();
        }
    }

    static final Map<Object, Object> UNDEFINED_MAP = new EmptyMap();

    /**
     * Returns an empty list that is undefined from a JSON perspective. It will not be serialized
     * when used as the value of an array property in API objects.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> undefinedMap() {
        return (Map<K, V>)UNDEFINED_MAP;
    }

    /**
     * Is {@code map} defined according to JSON/JavaScript semantics?
     *
     * @return true if {@code map} is not {@code null} and not {@link #undefinedMap()}
     */
    public static <K, V> boolean isDefined(Map<K, V> map) {
        return map != null && map != UNDEFINED_MAP;
    }

    /**
     * Returns an unmodifiable view of a map. If {@code map} is {@code null}, an {@link #undefinedMap()} is returned.
     */
    public static <K, V> Map<K, V> unmodifiable(Map<K, V> map) {
        if (map == null) {
            return undefinedMap();
        }
        if (map == UNDEFINED_MAP) {
            return map;
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns an unmodifiable view of a required map.
     */
    public static <K, V> Map<K, V> unmodifiableRequired(Map<K, V> map, Object obj, String name) {
        // We only check that the map is defined and not that it has some elements, as empty required
        // maps may have a meaning in some APIs. Furthermore, being defined means that it was set by
        // the application, so it's not an omission.
        requireNonNull(map == UNDEFINED_MAP ? null : map, obj, name);
        return Collections.unmodifiableMap(map);
    }
}
