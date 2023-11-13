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

//----------------------------------------------------
// THIS CODE IS GENERATED. MANUAL EDITS WILL BE LOST.
//----------------------------------------------------

package org.opensearch.client.opensearch.cat;

import org.opensearch.client.opensearch._types.Bytes;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// typedef: cat.recovery.Request

/**
 * Returns information about index shard recoveries, both on-going completed.
 */

public class RecoveryRequest extends CatRequestBase {
    @Nullable
    private final Boolean activeOnly;

    @Nullable
    private final Bytes bytes;

    @Nullable
    private final Boolean detailed;

    private final List<String> index;

    // ---------------------------------------------------------------------------------------------

    private RecoveryRequest(Builder builder) {
        super(builder);
        this.activeOnly = builder.activeOnly;
        this.bytes = builder.bytes;
        this.detailed = builder.detailed;
        this.index = ApiTypeHelper.unmodifiable(builder.index);

    }

    public static RecoveryRequest of(Function<Builder, ObjectBuilder<RecoveryRequest>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * If <code>true</code>, the response only includes ongoing shard recoveries
     * <p>
     * API name: {@code active_only}
     */
    @Nullable
    public final Boolean activeOnly() {
        return this.activeOnly;
    }

    /**
     * The unit in which to display byte values
     * <p>
     * API name: {@code bytes}
     */
    @Nullable
    public final Bytes bytes() {
        return this.bytes;
    }

    /**
     * If <code>true</code>, the response includes detailed information about shard
     * recoveries
     * <p>
     * API name: {@code detailed}
     */
    @Nullable
    public final Boolean detailed() {
        return this.detailed;
    }

    /**
     * Comma-separated list or wildcard expression of index names to limit the
     * returned information
     * <p>
     * API name: {@code index}
     */
    public final List<String> index() {
        return this.index;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link RecoveryRequest}.
     */

    public static class Builder extends CatRequestBaseBuilder<RecoveryRequest.Builder> {
        @Nullable
        private Boolean activeOnly;

        @Nullable
        private Bytes bytes;

        @Nullable
        private Boolean detailed;

        @Nullable
        private List<String> index;

        /**
         * If <code>true</code>, the response only includes ongoing shard recoveries
         * <p>
         * API name: {@code active_only}
         */
        public final Builder activeOnly(@Nullable Boolean value) {
            this.activeOnly = value;
            return this;
        }

        /**
         * The unit in which to display byte values
         * <p>
         * API name: {@code bytes}
         */
        public final Builder bytes(@Nullable Bytes value) {
            this.bytes = value;
            return this;
        }

        /**
         * If <code>true</code>, the response includes detailed information about shard
         * recoveries
         * <p>
         * API name: {@code detailed}
         */
        public final Builder detailed(@Nullable Boolean value) {
            this.detailed = value;
            return this;
        }

        /**
         * Comma-separated list or wildcard expression of index names to limit the
         * returned information
         * <p>
         * API name: {@code index}
         * <p>
         * Adds all elements of <code>list</code> to <code>index</code>.
         */
        public final Builder index(List<String> list) {
            this.index = _listAddAll(this.index, list);
            return this;
        }

        /**
         * Comma-separated list or wildcard expression of index names to limit the
         * returned information
         * <p>
         * API name: {@code index}
         * <p>
         * Adds one or more values to <code>index</code>.
         */
        public final Builder index(String value, String... values) {
            this.index = _listAdd(this.index, value, values);
            return this;
        }

        /**
         * Builds a {@link RecoveryRequest}.
         *
         * @throws NullPointerException if some of the required fields are null.
         */
        public RecoveryRequest build() {
            _checkSingleUse();

            return new RecoveryRequest(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Endpoint "{@code cat.recovery}".
     */
    public static final Endpoint<RecoveryRequest, RecoveryResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

            // Request method
            request -> {
                return "GET";

            },

            // Request path
            request -> {
                final int _index = 1 << 0;

                int propsSet = 0;

                if (ApiTypeHelper.isDefined(request.index())) propsSet |= _index;

                if (propsSet == 0) {
                    StringBuilder buf = new StringBuilder();
                    buf.append("/_cat");
                    buf.append("/recovery");
                    return buf.toString();
                }
                if (propsSet == (_index)) {
                    StringBuilder buf = new StringBuilder();
                    buf.append("/_cat");
                    buf.append("/recovery");
                    buf.append("/");
                    SimpleEndpoint.pathEncode(request.index.stream().map(v -> v).collect(Collectors.joining(",")), buf);
                    return buf.toString();
                }
                throw SimpleEndpoint.noPathTemplateFound("path");

            },

            // Request parameters
            request -> {
                Map<String, String> params =  new HashMap<>(request.queryParameters());
                if (request.detailed != null) {
                    params.put("detailed", String.valueOf(request.detailed));
                }
                if (request.activeOnly != null) {
                    params.put("active_only", String.valueOf(request.activeOnly));
                }
                if (request.bytes != null) {
                    params.put("bytes", request.bytes.jsonValue());
                }
                return params;

            }, SimpleEndpoint.emptyMap(), false, RecoveryResponse._DESERIALIZER);
}
