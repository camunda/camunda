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

package org.opensearch.client.opensearch.cluster;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch._types.Level;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.WaitForActiveShards;
import org.opensearch.client.opensearch._types.WaitForEvents;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

// typedef: cluster.health.Request

/**
 * Returns basic information about the health of the cluster.
 * 
 */

public class HealthRequest extends RequestBase {
	private final List<ExpandWildcard> expandWildcards;

	private final List<String> index;

	@Nullable
	private final Level level;

	@Nullable
	private final Boolean local;

	@Deprecated
	@Nullable
	private final Time masterTimeout;

	@Nullable
	private final Time clusterManagerTimeout;

	@Nullable
	private final Time timeout;

	@Nullable
	private final WaitForActiveShards waitForActiveShards;

	@Nullable
	private final WaitForEvents waitForEvents;

	@Nullable
	private final Boolean waitForNoInitializingShards;

	@Nullable
	private final Boolean waitForNoRelocatingShards;

	@Nullable
	private final String waitForNodes;

	@Nullable
	private final HealthStatus waitForStatus;

	// ---------------------------------------------------------------------------------------------

	private HealthRequest(Builder builder) {

		this.expandWildcards = ApiTypeHelper.unmodifiable(builder.expandWildcards);
		this.index = ApiTypeHelper.unmodifiable(builder.index);
		this.level = builder.level;
		this.local = builder.local;
		this.masterTimeout = builder.masterTimeout;
		this.clusterManagerTimeout = builder.clusterManagerTimeout;
		this.timeout = builder.timeout;
		this.waitForActiveShards = builder.waitForActiveShards;
		this.waitForEvents = builder.waitForEvents;
		this.waitForNoInitializingShards = builder.waitForNoInitializingShards;
		this.waitForNoRelocatingShards = builder.waitForNoRelocatingShards;
		this.waitForNodes = builder.waitForNodes;
		this.waitForStatus = builder.waitForStatus;

	}

	public static HealthRequest of(Function<Builder, ObjectBuilder<HealthRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Whether to expand wildcard expression to concrete indices that are open,
	 * closed or both.
	 * <p>
	 * API name: {@code expand_wildcards}
	 */
	public final List<ExpandWildcard> expandWildcards() {
		return this.expandWildcards;
	}

	/**
	 * Comma-separated list of data streams, indices, and index aliases used to
	 * limit the request. Wildcard expressions (*) are supported. To target all data
	 * streams and indices in a cluster, omit this parameter or use _all or *.
	 * <p>
	 * API name: {@code index}
	 */
	public final List<String> index() {
		return this.index;
	}

	/**
	 * Can be one of cluster, indices or shards. Controls the details level of the
	 * health information returned.
	 * <p>
	 * API name: {@code level}
	 */
	@Nullable
	public final Level level() {
		return this.level;
	}

	/**
	 * If true, the request retrieves information from the local node only. Defaults
	 * to false, which means information is retrieved from the cluster-manager node.
	 * <p>
	 * API name: {@code local}
	 */
	@Nullable
	public final Boolean local() {
		return this.local;
	}

	/**
	 * Period to wait for a connection to the master node. If no response is
	 * received before the timeout expires, the request fails and returns an error.
	 * <p>
	 * API name: {@code master_timeout}
	 */
	@Deprecated
	@Nullable
	public final Time masterTimeout() {
		return this.masterTimeout;
	}

	/**
	 * Period to wait for a connection to the cluster-manager node. If no response is
	 * received before the timeout expires, the request fails and returns an error.
	 * <p>
	 * API name: {@code cluster_manager_timeout}
	 */
	@Nullable
	public final Time clusterManagerTimeout() {
		return this.clusterManagerTimeout;
	}

	/**
	 * Period to wait for a response. If no response is received before the timeout
	 * expires, the request fails and returns an error.
	 * <p>
	 * API name: {@code timeout}
	 */
	@Nullable
	public final Time timeout() {
		return this.timeout;
	}

	/**
	 * A number controlling to how many active shards to wait for, all to wait for
	 * all shards in the cluster to be active, or 0 to not wait.
	 * <p>
	 * API name: {@code wait_for_active_shards}
	 */
	@Nullable
	public final WaitForActiveShards waitForActiveShards() {
		return this.waitForActiveShards;
	}

	/**
	 * Can be one of immediate, urgent, high, normal, low, languid. Wait until all
	 * currently queued events with the given priority are processed.
	 * <p>
	 * API name: {@code wait_for_events}
	 */
	@Nullable
	public final WaitForEvents waitForEvents() {
		return this.waitForEvents;
	}

	/**
	 * A boolean value which controls whether to wait (until the timeout provided)
	 * for the cluster to have no shard initializations. Defaults to false, which
	 * means it will not wait for initializing shards.
	 * <p>
	 * API name: {@code wait_for_no_initializing_shards}
	 */
	@Nullable
	public final Boolean waitForNoInitializingShards() {
		return this.waitForNoInitializingShards;
	}

	/**
	 * A boolean value which controls whether to wait (until the timeout provided)
	 * for the cluster to have no shard relocations. Defaults to false, which means
	 * it will not wait for relocating shards.
	 * <p>
	 * API name: {@code wait_for_no_relocating_shards}
	 */
	@Nullable
	public final Boolean waitForNoRelocatingShards() {
		return this.waitForNoRelocatingShards;
	}

	/**
	 * The request waits until the specified number N of nodes is available. It also
	 * accepts &gt;=N, &lt;=N, &gt;N and &lt;N. Alternatively, it is possible to use
	 * ge(N), le(N), gt(N) and lt(N) notation.
	 * <p>
	 * API name: {@code wait_for_nodes}
	 */
	@Nullable
	public final String waitForNodes() {
		return this.waitForNodes;
	}

	/**
	 * One of green, yellow or red. Will wait (until the timeout provided) until the
	 * status of the cluster changes to the one provided or better, i.e. green &gt;
	 * yellow &gt; red. By default, will not wait for any status.
	 * <p>
	 * API name: {@code wait_for_status}
	 */
	@Nullable
	public final HealthStatus waitForStatus() {
		return this.waitForStatus;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HealthRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<HealthRequest> {
		@Nullable
		private List<ExpandWildcard> expandWildcards;

		@Nullable
		private List<String> index;

		@Nullable
		private Level level;

		@Nullable
		private Boolean local;

		@Deprecated
		@Nullable
		private Time masterTimeout;

		@Nullable
		private Time clusterManagerTimeout;

		@Nullable
		private Time timeout;

		@Nullable
		private WaitForActiveShards waitForActiveShards;

		@Nullable
		private WaitForEvents waitForEvents;

		@Nullable
		private Boolean waitForNoInitializingShards;

		@Nullable
		private Boolean waitForNoRelocatingShards;

		@Nullable
		private String waitForNodes;

		@Nullable
		private HealthStatus waitForStatus;

		/**
		 * Whether to expand wildcard expression to concrete indices that are open,
		 * closed or both.
		 * <p>
		 * API name: {@code expand_wildcards}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>expandWildcards</code>.
		 */
		public final Builder expandWildcards(List<ExpandWildcard> list) {
			this.expandWildcards = _listAddAll(this.expandWildcards, list);
			return this;
		}

		/**
		 * Whether to expand wildcard expression to concrete indices that are open,
		 * closed or both.
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
		 * Comma-separated list of data streams, indices, and index aliases used to
		 * limit the request. Wildcard expressions (*) are supported. To target all data
		 * streams and indices in a cluster, omit this parameter or use _all or *.
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
		 * Comma-separated list of data streams, indices, and index aliases used to
		 * limit the request. Wildcard expressions (*) are supported. To target all data
		 * streams and indices in a cluster, omit this parameter or use _all or *.
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
		 * Can be one of cluster, indices or shards. Controls the details level of the
		 * health information returned.
		 * <p>
		 * API name: {@code level}
		 */
		public final Builder level(@Nullable Level value) {
			this.level = value;
			return this;
		}

		/**
		 * If true, the request retrieves information from the local node only. Defaults
		 * to false, which means information is retrieved from the cluster-manager node.
		 * <p>
		 * API name: {@code local}
		 */
		public final Builder local(@Nullable Boolean value) {
			this.local = value;
			return this;
		}

		/**
		 * Period to wait for a connection to the master node. If no response is
		 * received before the timeout expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code master_timeout}
		 */
		@Deprecated
		public final Builder masterTimeout(@Nullable Time value) {
			this.masterTimeout = value;
			return this;
		}

		/**
		 * Period to wait for a connection to the master node. If no response is
		 * received before the timeout expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code master_timeout}
		 */
		@Deprecated
		public final Builder masterTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.masterTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Period to wait for a connection to the cluster-manager node. If no response is
		 * received before the timeout expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(@Nullable Time value) {
			this.clusterManagerTimeout = value;
			return this;
		}

		/**
		 * Period to wait for a connection to the cluster-manager node. If no response is
		 * received before the timeout expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.clusterManagerTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Period to wait for a response. If no response is received before the timeout
		 * expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder timeout(@Nullable Time value) {
			this.timeout = value;
			return this;
		}

		/**
		 * Period to wait for a response. If no response is received before the timeout
		 * expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder timeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.timeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * A number controlling to how many active shards to wait for, all to wait for
		 * all shards in the cluster to be active, or 0 to not wait.
		 * <p>
		 * API name: {@code wait_for_active_shards}
		 */
		public final Builder waitForActiveShards(@Nullable WaitForActiveShards value) {
			this.waitForActiveShards = value;
			return this;
		}

		/**
		 * A number controlling to how many active shards to wait for, all to wait for
		 * all shards in the cluster to be active, or 0 to not wait.
		 * <p>
		 * API name: {@code wait_for_active_shards}
		 */
		public final Builder waitForActiveShards(
				Function<WaitForActiveShards.Builder, ObjectBuilder<WaitForActiveShards>> fn) {
			return this.waitForActiveShards(fn.apply(new WaitForActiveShards.Builder()).build());
		}

		/**
		 * Can be one of immediate, urgent, high, normal, low, languid. Wait until all
		 * currently queued events with the given priority are processed.
		 * <p>
		 * API name: {@code wait_for_events}
		 */
		public final Builder waitForEvents(@Nullable WaitForEvents value) {
			this.waitForEvents = value;
			return this;
		}

		/**
		 * A boolean value which controls whether to wait (until the timeout provided)
		 * for the cluster to have no shard initializations. Defaults to false, which
		 * means it will not wait for initializing shards.
		 * <p>
		 * API name: {@code wait_for_no_initializing_shards}
		 */
		public final Builder waitForNoInitializingShards(@Nullable Boolean value) {
			this.waitForNoInitializingShards = value;
			return this;
		}

		/**
		 * A boolean value which controls whether to wait (until the timeout provided)
		 * for the cluster to have no shard relocations. Defaults to false, which means
		 * it will not wait for relocating shards.
		 * <p>
		 * API name: {@code wait_for_no_relocating_shards}
		 */
		public final Builder waitForNoRelocatingShards(@Nullable Boolean value) {
			this.waitForNoRelocatingShards = value;
			return this;
		}

		/**
		 * The request waits until the specified number N of nodes is available. It also
		 * accepts &gt;=N, &lt;=N, &gt;N and &lt;N. Alternatively, it is possible to use
		 * ge(N), le(N), gt(N) and lt(N) notation.
		 * <p>
		 * API name: {@code wait_for_nodes}
		 */
		public final Builder waitForNodes(@Nullable String value) {
			this.waitForNodes = value;
			return this;
		}

		/**
		 * One of green, yellow or red. Will wait (until the timeout provided) until the
		 * status of the cluster changes to the one provided or better, i.e. green &gt;
		 * yellow &gt; red. By default, will not wait for any status.
		 * <p>
		 * API name: {@code wait_for_status}
		 */
		public final Builder waitForStatus(@Nullable HealthStatus value) {
			this.waitForStatus = value;
			return this;
		}

		/**
		 * Builds a {@link HealthRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HealthRequest build() {
			_checkSingleUse();

			return new HealthRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code cluster.health}".
	 */
	public static final Endpoint<HealthRequest, HealthResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "GET";

			},

			// Request path
			request -> {
				final int _index = 1 << 0;

				int propsSet = 0;

				if (ApiTypeHelper.isDefined(request.index()))
					propsSet |= _index;

				if (propsSet == 0) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_cluster");
					buf.append("/health");
					return buf.toString();
				}
				if (propsSet == (_index)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_cluster");
					buf.append("/health");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index.stream().map(v -> v).collect(Collectors.joining(",")), buf);
					return buf.toString();
				}
				throw SimpleEndpoint.noPathTemplateFound("path");

			},

			// Request parameters
			request -> {
				Map<String, String> params = new HashMap<>();
				if (request.masterTimeout != null) {
					params.put("master_timeout", request.masterTimeout._toJsonString());
				}
				if (request.clusterManagerTimeout != null) {
					params.put("cluster_manager_timeout", request.clusterManagerTimeout._toJsonString());
				}
				if (ApiTypeHelper.isDefined(request.expandWildcards)) {
					params.put("expand_wildcards",
							request.expandWildcards.stream()
									.map(v -> v.jsonValue()).collect(Collectors.joining(",")));
				}
				if (request.level != null) {
					params.put("level", request.level.jsonValue());
				}
				if (request.waitForEvents != null) {
					params.put("wait_for_events", request.waitForEvents.jsonValue());
				}
				if (request.waitForNoInitializingShards != null) {
					params.put("wait_for_no_initializing_shards", String.valueOf(request.waitForNoInitializingShards));
				}
				if (request.waitForStatus != null) {
					params.put("wait_for_status", request.waitForStatus.jsonValue());
				}
				if (request.waitForActiveShards != null) {
					params.put("wait_for_active_shards", request.waitForActiveShards._toJsonString());
				}
				if (request.waitForNodes != null) {
					params.put("wait_for_nodes", request.waitForNodes);
				}
				if (request.waitForNoRelocatingShards != null) {
					params.put("wait_for_no_relocating_shards", String.valueOf(request.waitForNoRelocatingShards));
				}
				if (request.local != null) {
					params.put("local", String.valueOf(request.local));
				}
				if (request.timeout != null) {
					params.put("timeout", request.timeout._toJsonString());
				}
				return params;

			}, SimpleEndpoint.emptyMap(), false, HealthResponse._DESERIALIZER);
}
