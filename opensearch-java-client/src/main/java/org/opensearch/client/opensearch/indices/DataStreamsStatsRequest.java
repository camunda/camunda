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

package org.opensearch.client.opensearch.indices;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// typedef: indices.data_streams_stats.Request

/**
 * Returns statistics of data streams
 */
public class DataStreamsStatsRequest extends RequestBase {

    private final List<String> name;

    @Nullable
    private final Boolean human;

    // ---------------------------------------------------------------------------------------------

    private DataStreamsStatsRequest(Builder builder) {
        this.name = ApiTypeHelper.unmodifiable(builder.name);
        this.human = builder.human;
    }

    public static DataStreamsStatsRequest of(Function<Builder, ObjectBuilder<DataStreamsStatsRequest>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Required - Comma-separated list of data streams to get.
     * <p>
     * API name: {@code name}
     */
    public final List<String> name() {
        return this.name;
    }

    /**
     * Specify this query parameter to include human readable fields in the response.
     * <p>
     * API name: {@code human}
     */
    @Nullable
    public final Boolean human() {
        return this.human;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link DataStreamsStatsRequest}.
     */
    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DataStreamsStatsRequest> {

        @Nullable
        private List<String> name;

        @Nullable
        private Boolean human;

        /**
         * Required - Comma-separated list of data streams to get.
         * <p>
         * API name: {@code name}
         * <p>
         * Adds all elements of <code>list</code> to <code>name</code>.
         */
        public final Builder name(List<String> list) {
            this.name = _listAddAll(this.name, list);
            return this;
        }

        /**
         * Required - Comma-separated list of data streams to get.
         * <p>
         * API name: {@code name}
         * <p>
         * Adds one or more values to <code>name</code>.
         */
        public final Builder name(String value, String... values) {
            this.name = _listAdd(this.name, value, values);
            return this;
        }

        /**
         * Specify this query parameter to include human readable fields in the response.
         * <p>
         * API name: {@code human}
         */
        public final Builder human(@Nullable Boolean value) {
            this.human = value;
            return this;
        }

        public DataStreamsStatsRequest build() {
            _checkSingleUse();

            return new DataStreamsStatsRequest(this);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Endpoint "{@code indices.get_data_stream}".
     */
    public static final Endpoint<DataStreamsStatsRequest, DataStreamsStatsResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(
            // Request method
            request -> {
                return "GET";
            },

            // Request path
            request -> {
                final int _name = 1 << 0;
                int propsSet = 0;

                if (ApiTypeHelper.isDefined(request.name())) {
                    propsSet |= _name;
                }

                if (propsSet == 0) {
                    StringBuilder sbd = new StringBuilder();
                    sbd.append("/_data_stream");
                    sbd.append("/_stats");
                    return sbd.toString();
                }

                if (propsSet == (_name)) {
                    StringBuilder sbd = new StringBuilder();
                    sbd.append("/_data_stream");
                    sbd.append("/");
                    SimpleEndpoint.pathEncode(request.name.stream().map(v -> v).collect(Collectors.joining(",")), sbd);
                    sbd.append("/_stats");
                    return sbd.toString();
                }
                throw SimpleEndpoint.noPathTemplateFound("path");
            },

            // Request parameters
            request -> {
                Map<String, String> params = new HashMap<>();
                if (request.human != null) {
                    params.put("human", String.valueOf(request.human));
                }
                return params;
            }, SimpleEndpoint.emptyMap(), false, DataStreamsStatsResponse._DESERIALIZER);

}
