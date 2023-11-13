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

import org.opensearch.client.json.NdJsonpSerializable;

import java.util.Collections;
import java.util.Iterator;

public class TaggedUnionUtils {
    public static <Union extends TaggedUnion<Tag, ?>, Tag extends Enum<Tag>, Value> Value get(Union union, Tag kind) {
        if (kind == union._kind()) {
            @SuppressWarnings("unchecked")
            Value result = (Value) union._get();
            return result;
        } else {
            throw new IllegalStateException("Cannot get '" + kind + "' variant: current variant is '" + union._kind() + "'.");
        }
    }

    public static <T> Iterator<?> ndJsonIterator(TaggedUnion<?, T> union) {

        T value = union._get();

        if (value instanceof NdJsonpSerializable) {
            // Iterate on value's items, replacing value, if it appears, by the union. This allows JSON wrapping
            // done by the container to happen.
            Iterator<?> valueIterator = ((NdJsonpSerializable) value)._serializables();

            return new Iterator<Object>() {
                @Override
                public boolean hasNext() {
                    return valueIterator.hasNext();
                }

                @Override
                public Object next() {
                    Object next = valueIterator.next();
                    if (next == value) {
                        return union;
                    } else {
                        return next;
                    }
                }
            };
        } else {
            // Nothing to flatten
            return Collections.singletonList(union).iterator();
        }
    }
}
