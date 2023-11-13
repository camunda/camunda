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

package org.opensearch.client.transport;

import org.opensearch.client.util.ObjectBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Container for all application-specific or request-specific options, including headers, query parameters and warning handlers.
 */
public interface TransportOptions {

    Collection<Map.Entry<String, String>> headers();

    Map<String, String> queryParameters();

    Function<List<String>, Boolean> onWarnings();

    Builder toBuilder();

    default TransportOptions with(Consumer<Builder> fn) {
        Builder builder = toBuilder();
        fn.accept(builder);
        return builder.build();
    }

    static Builder builder() {
        return new BuilderImpl();
    }

    interface Builder extends ObjectBuilder<TransportOptions> {

        Builder addHeader(String name, String value);

        Builder setParameter(String name, String value);

        Builder onWarnings(Function<List<String>, Boolean> listener);
    }

    class BuilderImpl implements Builder {
        protected List<Map.Entry<String, String>> headers = Collections.emptyList();
        protected Map<String,String> queryParameters = Collections.emptyMap();
        protected Function<List<String>, Boolean> onWarnings = null;

        public BuilderImpl() {
        }

        public BuilderImpl(TransportOptions src) {
            Collection<Map.Entry<String, String>> srcHeaders = src.headers();
            if (srcHeaders != null && !srcHeaders.isEmpty()) {
                headers = new ArrayList<>(srcHeaders);
            }
            Map<String,String> srcParams = src.queryParameters();
            if (srcParams != null && !srcParams.isEmpty()) {
                queryParameters = new HashMap<>(srcParams);
            }
            onWarnings = src.onWarnings();
        }

        @Override
        public Builder addHeader(String name, String value) {
            if (headers.isEmpty()) {
                headers = new ArrayList<>();
            }
            headers.add(Map.entry(name, value));
            return this;
        }

        @Override
        public Builder setParameter(String name, String value) {
            if (value == null) {
                if (!queryParameters.isEmpty()) {
                    queryParameters.remove(name);
                }
            } else {
                if (queryParameters.isEmpty()) {
                    queryParameters = new HashMap<>();
                }
                queryParameters.put(name, value);
            }
            return this;
        }

        @Override
        public Builder onWarnings(Function<List<String>, Boolean> listener) {
            onWarnings = listener;
            return this;
        }

        @Override
        public TransportOptions build() {
            return new DefaultImpl(this);
        }
    }

    class DefaultImpl implements TransportOptions {
        private final List<Map.Entry<String, String>> headers;
        private final Map<String, String> params;
        private final Function<List<String>, Boolean> onWarnings;

        protected DefaultImpl(BuilderImpl builder) {
            this.headers = builder.headers.isEmpty() ? Collections.emptyList() : List.copyOf(builder.headers);
            this.params = builder.queryParameters.isEmpty() ?
                    Collections.emptyMap() :
                    Map.copyOf(builder.queryParameters);
            this.onWarnings = builder.onWarnings;
        }

        @Override
        public Collection<Map.Entry<String, String>> headers() {
            return headers;
        }

        @Override
        public Map<String, String> queryParameters() {
            return params;
        }

        @Override
        public Function<List<String>, Boolean> onWarnings() {
            return onWarnings;
        }

        @Override
        public Builder toBuilder() {
            return new BuilderImpl(this);
        }
    }
}
