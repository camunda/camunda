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
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class ListBuilder<T, B> implements ObjectBuilder<List<T>> {
    private final List<T> list = new ArrayList<>();
    private final Supplier<B> builderCtor;

    public static <T, B extends ObjectBuilder<T>> ListBuilder<T, B> of(Supplier<B> builderCtor) {
        return new ListBuilder<>(builderCtor);
    }

    public ListBuilder(Supplier<B> builderCtor) {
        this.builderCtor = builderCtor;
    }

    public ListBuilder<T, B> add(T value) {
        list.add(value);
        return this;
    }

    public ListBuilder<T, B> add(Function<B, ObjectBuilder<T>> fn) {
        return add(fn.apply(builderCtor.get()).build());
    }

    public ListBuilder<T, B> addAll(Iterable<? extends T> iterable) {
        for (T item: iterable) {
            list.add(item);
        }
        return this;
    }

    @Override
    public List<T> build() {
        return list;
    }
}
