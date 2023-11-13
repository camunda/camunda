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

package org.opensearch.client.opensearch.cat;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ObjectBuilder;

/**
 * Provides low-level information about the disk utilization of a PIT by
 * describing its Lucene segments
 * 
 */
public class PitSegmentsRequest extends CatRequestBase {
    
    @Nullable
    private List<String> pitId;

    public PitSegmentsRequest(Builder builder) {
        this.pitId = builder.pitId;
    }

    public static PitSegmentsRequest of(Function<Builder, ObjectBuilder<PitSegmentsRequest>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * A list of Pit IDs to get segments
     * <p>
     * API name - {@code pit_id}
     */
    @Nullable
    public final List<String> pitId() {
        return this.pitId;
    }

    /**
     * Builder for {@link PitSegmentsRequest}
     */
    public static class Builder extends CatRequestBaseBuilder<PitSegmentsRequest.Builder> {
        private List<String> pitId;

        /**
         * A list of Pit IDs to get segments
         * <p>
         * API name - {@code pit_id}
         */
        public final Builder pitId(@Nullable List<String> pitId) {
            this.pitId = pitId;
            return this;
        }

        /**
         * Builds a {@link PitSegmentsRequest}.
         * 
         * @throws NullPointerException if some of the required fields are null.
         */
        public PitSegmentsRequest build() {
            _checkSingleUse();
            return new PitSegmentsRequest(this);
        }

        @Override
        protected Builder self() {
            return this;
        }
    }

    /**
     * Endpoint "{@code point_in_time_segments}"
     */
    public static final Endpoint<PitSegmentsRequest, SegmentsResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(
            // Request Method
            request -> {
                return "GET";
            },

            // Request Path
            request -> {
                final int _all = 1 << 0;

                int propsSet = 0;

                if (request.pitId() == null) {
                    propsSet |= _all;
                }
                if (propsSet == 0) {
                    return "/_cat/pit_segments";
                } else {
                    return "/_cat/pit_segments/_all";
                }
            },
            SimpleEndpoint.emptyMap(), SimpleEndpoint.emptyMap(), false, SegmentsResponse._DESERIALIZER);

}
