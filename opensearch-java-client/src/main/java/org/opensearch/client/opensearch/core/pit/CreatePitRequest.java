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

package org.opensearch.client.opensearch.core.pit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;


/**
 * Creates a Point In Time attribute on Search
 * 
 */
public class CreatePitRequest extends RequestBase {

    private List<String> targetIndexes;

    private Time keepAlive;

    @Nullable
    private String preference;

    @Nullable
    private String routing;

    @Nullable
    private List<ExpandWildcard> expandWildcards;

    @Nullable
    private Boolean allowPartialPitCreation;

    private CreatePitRequest(Builder builder) {
        this.targetIndexes = ApiTypeHelper.unmodifiableRequired(builder.targetIndexes, this, "targetIndexes");
        this.keepAlive = ApiTypeHelper.requireNonNull(builder.keepAlive, this, "keepAlive");
        this.preference = builder.preference;
        this.routing = builder.routing;
        this.expandWildcards = ApiTypeHelper.unmodifiable(builder.expandWildcards);
        this.allowPartialPitCreation = builder.allowPartialPitCreation;
    }

    public static CreatePitRequest of(Function<Builder, ObjectBuilder<CreatePitRequest>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Required - The name(s) of the target index(es) for the PIT.
     * May contain a comma-separated list or a wildcard index pattern.
     * <p>
     * API name: {@code target_indexes}
     */
    public final List<String> targetIndexes() {
        return this.targetIndexes;
    }

    /**
     * Required - The amount of time to keep the PIT. Every time you access a PIT by
     * using the
     * Search API, the PIT lifetime is extended by the amount of time equal to the
     * keep_alive parameter.
     * <p>
     * API name: {@code keep_alive}
     */
    public final Time keepAlive() {
        return this.keepAlive;
    }

    /**
     * The node or the shard used to perform the search. Optional. Default is
     * random.
     * <p>
     * API name: {@code preference}
     */
    @Nullable
    public final String preference() {
        return this.preference;
    }

    /**
     * Specifies to route search requests to a specific shard. Optional. Default is
     * the document’s _id.
     * <p>
     * API name: {@code routing}
     */
    @Nullable
    public final String routing() {
        return this.routing;
    }

    /**
     * The type of index that can match the wildcard pattern. Supports
     * comma-separated values. Valid values are the following:
     * - all: Match any index or data stream, including hidden ones.
     * - open: Match open, non-hidden indexes or non-hidden data streams.
     * - closed: Match closed, non-hidden indexes or non-hidden data streams.
     * - hidden: Match hidden indexes or data streams. Must be combined with open,
     * closed or both open and closed.
     * - none: No wildcard patterns are accepted.
     * Optional. Default is open.
     * <p>
     * API name: {@code expand_wildcards}
     */
    @Nullable
    public final List<ExpandWildcard> expandWildcards() {
        return this.expandWildcards;
    }

    /**
     * Specifies whether to create a PIT with partial failures. Optional. Default is
     * true.
     * <p>
     * API name: {@code allow_partial_pit_creation}
     */
    @Nullable
    public final Boolean allowPartialPitCreation() {
        return this.allowPartialPitCreation;
    }

    /**
     * Builder for {@link CreatePitRequest}
     */
    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<CreatePitRequest> {
        private List<String> targetIndexes;

        private Time keepAlive;

        @Nullable
        private String preference;

        @Nullable
        private String routing;

        @Nullable
        private List<ExpandWildcard> expandWildcards;

        @Nullable
        private Boolean allowPartialPitCreation;

        /**
         * Required - The name(s) of the target index(es) for the PIT.
         * May contain a comma-separated list or a wildcard index pattern.
         * <p>
         * API name: {@code target_indexes}
         * <p>
         * Adds all elements of <code>list</code> to <code>targetIndexes</code>.
         */
        public final Builder targetIndexes(List<String> list) {
            this.targetIndexes = _listAddAll(this.targetIndexes, list);
            return this;
        }

        /**
         * Required - The name(s) of the target index(es) for the PIT.
         * May contain a comma-separated list or a wildcard index pattern.
         * <p>
         * API name: {@code target_indexes}
         * <p>
         * Adds one or more values to <code>targetIndexes</code>.
         */
        public final Builder targetIndexes(String value, String... values) {
            this.targetIndexes = _listAdd(this.targetIndexes, value, values);
            return this;
        }

        /**
         * Required - The amount of time to keep the PIT. Every time you access a PIT by
         * using the
         * Search API, the PIT lifetime is extended by the amount of time equal to the
         * keep_alive parameter.
         * <p>
         * API name: {@code keep_alive}
         */
        public final Builder keepAlive(Time keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /**
         * Required - The amount of time to keep the PIT. Every time you access a PIT by
         * using the
         * Search API, the PIT lifetime is extended by the amount of time equal to the
         * keep_alive parameter.
         * <p>
         * API name: {@code keep_alive}
         */
        public final Builder keepAlive(Function<Time.Builder, ObjectBuilder<Time>> fn) {
            return this.keepAlive(fn.apply(new Time.Builder()).build());
        }

        /**
         * The node or the shard used to perform the search. Optional. Default is
         * random.
         * <p>
         * API name: {@code preference}
         */
        public final Builder preference(@Nullable String preference) {
            this.preference = preference;
            return this;
        }

        /**
         * Specifies to route search requests to a specific shard. Optional. Default is
         * the document’s _id.
         * <p>
         * API name: {@code routing}
         */
        public final Builder routing(@Nullable String routing) {
            this.routing = routing;
            return this;
        }

        /**
         * The type of index that can match the wildcard pattern. Supports
         * comma-separated values. Valid values are the following:
         * - all: Match any index or data stream, including hidden ones.
         * - open: Match open, non-hidden indexes or non-hidden data streams.
         * - closed: Match closed, non-hidden indexes or non-hidden data streams.
         * - hidden: Match hidden indexes or data streams. Must be combined with open,
         * closed or both open and closed.
         * - none: No wildcard patterns are accepted.
         * Optional. Default is open.
         * <p>
         * API name: {@code expand_wildcards}
         * <p>
         * Adds all elements of <code>list</code> to <code>expandWildcards</code>.
         */
        public final Builder expandWildcards(@Nullable List<ExpandWildcard> list) {
            this.expandWildcards = _listAddAll(this.expandWildcards, list);
            return this;
        }

        /**
         * The type of index that can match the wildcard pattern. Supports
         * comma-separated values. Valid values are the following:
         * - all: Match any index or data stream, including hidden ones.
         * - open: Match open, non-hidden indexes or non-hidden data streams.
         * - closed: Match closed, non-hidden indexes or non-hidden data streams.
         * - hidden: Match hidden indexes or data streams. Must be combined with open,
         * closed or both open and closed.
         * - none: No wildcard patterns are accepted.
         * Optional. Default is open.
         * <p>
         * API name: {@code expand_wildcards}
         * <p>
         * Adds one or more values to <code>expandWildcards</code>.
         */
        public final Builder expandWildcards(ExpandWildcard value, ExpandWildcard... values) {
            this.expandWildcards = _listAdd(this.expandWildcards, value, values);
            return this;
        }

        /**
         * Specifies whether to create a PIT with partial failures. Optional. Default is
         * true.
         * <p>
         * API name: {@code allow_partial_pit_creation}
         */
        public final Builder allowPartialPitCreation(@Nullable Boolean allowPartialPitCreation) {
            this.allowPartialPitCreation = allowPartialPitCreation;
            return this;
        }

        /**
         * Builds a {@link CreatePitRequest}.
         * 
         * @throws NullPointerException if some of the required fields are null.
         */
        public CreatePitRequest build() {
            _checkSingleUse();
            return new CreatePitRequest(this);
        }
    }

    public static final Endpoint<CreatePitRequest, CreatePitResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(
            // Request method
            request -> {
                return "POST";
            },

            // Request Path
            request -> {

                final int _targetIndexes = 1 << 0;

                int propsSet = 0;

                propsSet |= _targetIndexes;

                if (propsSet == (_targetIndexes)) {
                    StringBuilder buf = new StringBuilder();
                    buf.append("/");
                    SimpleEndpoint.pathEncode(
                            request.targetIndexes.stream().map(v -> v).collect(Collectors.joining(",")), buf);
                    buf.append("/_search/point_in_time");
                    return buf.toString();
                }
                throw SimpleEndpoint.noPathTemplateFound("path");
            }, request -> {
                Map<String, String> params = new HashMap<>();
                params.put("keep_alive", request.keepAlive._toJsonString());

                if (request.preference != null) {
                    params.put("preference", request.preference);
                }
                if (request.routing != null) {
                    params.put("routing", request.routing);
                }
                if (ApiTypeHelper.isDefined(request.expandWildcards)) {
                    params.put("expand_wildcards",
                            request.expandWildcards.stream()
                                    .map(v -> v.jsonValue()).collect(Collectors.joining(",")));
                }
                if (request.allowPartialPitCreation != null) {
                    params.put("allow_partial_pit_creation", String.valueOf(request.allowPartialPitCreation));
                }
                return params;
            }, SimpleEndpoint.emptyMap(), false, CreatePitResponse._DESERIALIZER);
}
