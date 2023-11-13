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

package org.opensearch.client.opensearch.nodes;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.Level;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.Time;
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

// typedef: nodes.stats.Request

/**
 * Returns statistical information about nodes in the cluster.
 *
 */

public class NodesStatsRequest extends RequestBase {
	private final List<String> completionFields;

	private final List<String> fielddataFields;

	private final List<String> fields;

	@Nullable
	private final Boolean groups;

	@Nullable
	private final Boolean includeSegmentFileSizes;

	@Nullable
	private final Boolean includeUnloadedSegments;

	private final List<String> indexMetric;

	@Nullable
	private final Level level;

	@Deprecated
	@Nullable
	private final Time masterTimeout;

	@Nullable
	private final Time clusterManagerTimeout;

	private final List<String> metric;

	private final List<String> nodeId;

	@Nullable
	private final Time timeout;

	private final List<String> types;

	// ---------------------------------------------------------------------------------------------

	private NodesStatsRequest(Builder builder) {

		this.completionFields = ApiTypeHelper.unmodifiable(builder.completionFields);
		this.fielddataFields = ApiTypeHelper.unmodifiable(builder.fielddataFields);
		this.fields = ApiTypeHelper.unmodifiable(builder.fields);
		this.groups = builder.groups;
		this.includeSegmentFileSizes = builder.includeSegmentFileSizes;
		this.includeUnloadedSegments = builder.includeUnloadedSegments;
		this.indexMetric = ApiTypeHelper.unmodifiable(builder.indexMetric);
		this.level = builder.level;
		this.masterTimeout = builder.masterTimeout;
		this.clusterManagerTimeout = builder.clusterManagerTimeout;
		this.metric = ApiTypeHelper.unmodifiable(builder.metric);
		this.nodeId = ApiTypeHelper.unmodifiable(builder.nodeId);
		this.timeout = builder.timeout;
		this.types = ApiTypeHelper.unmodifiable(builder.types);

	}

	public static NodesStatsRequest of(Function<Builder, ObjectBuilder<NodesStatsRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Comma-separated list or wildcard expressions of fields to include in
	 * fielddata and suggest statistics.
	 * <p>
	 * API name: {@code completion_fields}
	 */
	public final List<String> completionFields() {
		return this.completionFields;
	}

	/**
	 * Comma-separated list or wildcard expressions of fields to include in
	 * fielddata statistics.
	 * <p>
	 * API name: {@code fielddata_fields}
	 */
	public final List<String> fielddataFields() {
		return this.fielddataFields;
	}

	/**
	 * Comma-separated list or wildcard expressions of fields to include in the
	 * statistics.
	 * <p>
	 * API name: {@code fields}
	 */
	public final List<String> fields() {
		return this.fields;
	}

	/**
	 * Comma-separated list of search groups to include in the search statistics.
	 * <p>
	 * API name: {@code groups}
	 */
	@Nullable
	public final Boolean groups() {
		return this.groups;
	}

	/**
	 * If true, the call reports the aggregated disk usage of each one of the Lucene
	 * index files (only applies if segment stats are requested).
	 * <p>
	 * API name: {@code include_segment_file_sizes}
	 */
	@Nullable
	public final Boolean includeSegmentFileSizes() {
		return this.includeSegmentFileSizes;
	}

	/**
	 * If set to true segment stats will include stats for segments that are not
	 * currently loaded into memory
	 * <p>
	 * API name: {@code include_unloaded_segments}
	 */
	@Nullable
	public final Boolean includeUnloadedSegments() {
		return this.includeUnloadedSegments;
	}

	/**
	 * Limit the information returned for indices metric to the specific index
	 * metrics. It can be used only if indices (or all) metric is specified.
	 * <p>
	 * API name: {@code index_metric}
	 */
	public final List<String> indexMetric() {
		return this.indexMetric;
	}

	/**
	 * Indicates whether statistics are aggregated at the cluster, index, or shard
	 * level.
	 * <p>
	 * API name: {@code level}
	 */
	@Nullable
	public final Level level() {
		return this.level;
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
	 * Limit the information returned to the specified metrics
	 * <p>
	 * API name: {@code metric}
	 */
	public final List<String> metric() {
		return this.metric;
	}

	/**
	 * Comma-separated list of node IDs or names used to limit returned information.
	 * <p>
	 * API name: {@code node_id}
	 */
	public final List<String> nodeId() {
		return this.nodeId;
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
	 * A comma-separated list of document types for the indexing index metric.
	 * <p>
	 * API name: {@code types}
	 */
	public final List<String> types() {
		return this.types;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodesStatsRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodesStatsRequest> {
		@Nullable
		private List<String> completionFields;

		@Nullable
		private List<String> fielddataFields;

		@Nullable
		private List<String> fields;

		@Nullable
		private Boolean groups;

		@Nullable
		private Boolean includeSegmentFileSizes;

		@Nullable
		private Boolean includeUnloadedSegments;

		@Nullable
		private List<String> indexMetric;

		@Nullable
		private Level level;

		@Deprecated
		@Nullable
		private Time masterTimeout;

		@Nullable
		private Time clusterManagerTimeout;

		@Nullable
		private List<String> metric;

		@Nullable
		private List<String> nodeId;

		@Nullable
		private Time timeout;

		@Nullable
		private List<String> types;

		/**
		 * Comma-separated list or wildcard expressions of fields to include in
		 * fielddata and suggest statistics.
		 * <p>
		 * API name: {@code completion_fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>completionFields</code>.
		 */
		public final Builder completionFields(List<String> list) {
			this.completionFields = _listAddAll(this.completionFields, list);
			return this;
		}

		/**
		 * Comma-separated list or wildcard expressions of fields to include in
		 * fielddata and suggest statistics.
		 * <p>
		 * API name: {@code completion_fields}
		 * <p>
		 * Adds one or more values to <code>completionFields</code>.
		 */
		public final Builder completionFields(String value, String... values) {
			this.completionFields = _listAdd(this.completionFields, value, values);
			return this;
		}

		/**
		 * Comma-separated list or wildcard expressions of fields to include in
		 * fielddata statistics.
		 * <p>
		 * API name: {@code fielddata_fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>fielddataFields</code>.
		 */
		public final Builder fielddataFields(List<String> list) {
			this.fielddataFields = _listAddAll(this.fielddataFields, list);
			return this;
		}

		/**
		 * Comma-separated list or wildcard expressions of fields to include in
		 * fielddata statistics.
		 * <p>
		 * API name: {@code fielddata_fields}
		 * <p>
		 * Adds one or more values to <code>fielddataFields</code>.
		 */
		public final Builder fielddataFields(String value, String... values) {
			this.fielddataFields = _listAdd(this.fielddataFields, value, values);
			return this;
		}

		/**
		 * Comma-separated list or wildcard expressions of fields to include in the
		 * statistics.
		 * <p>
		 * API name: {@code fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>fields</code>.
		 */
		public final Builder fields(List<String> list) {
			this.fields = _listAddAll(this.fields, list);
			return this;
		}

		/**
		 * Comma-separated list or wildcard expressions of fields to include in the
		 * statistics.
		 * <p>
		 * API name: {@code fields}
		 * <p>
		 * Adds one or more values to <code>fields</code>.
		 */
		public final Builder fields(String value, String... values) {
			this.fields = _listAdd(this.fields, value, values);
			return this;
		}

		/**
		 * Comma-separated list of search groups to include in the search statistics.
		 * <p>
		 * API name: {@code groups}
		 */
		public final Builder groups(@Nullable Boolean value) {
			this.groups = value;
			return this;
		}

		/**
		 * If true, the call reports the aggregated disk usage of each one of the Lucene
		 * index files (only applies if segment stats are requested).
		 * <p>
		 * API name: {@code include_segment_file_sizes}
		 */
		public final Builder includeSegmentFileSizes(@Nullable Boolean value) {
			this.includeSegmentFileSizes = value;
			return this;
		}

		/**
		 * If set to true segment stats will include stats for segments that are not
		 * currently loaded into memory
		 * <p>
		 * API name: {@code include_unloaded_segments}
		 */
		public final Builder includeUnloadedSegments(@Nullable Boolean value) {
			this.includeUnloadedSegments = value;
			return this;
		}

		/**
		 * Limit the information returned for indices metric to the specific index
		 * metrics. It can be used only if indices (or all) metric is specified.
		 * <p>
		 * API name: {@code index_metric}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>indexMetric</code>.
		 */
		public final Builder indexMetric(List<String> list) {
			this.indexMetric = _listAddAll(this.indexMetric, list);
			return this;
		}

		/**
		 * Limit the information returned for indices metric to the specific index
		 * metrics. It can be used only if indices (or all) metric is specified.
		 * <p>
		 * API name: {@code index_metric}
		 * <p>
		 * Adds one or more values to <code>indexMetric</code>.
		 */
		public final Builder indexMetric(String value, String... values) {
			this.indexMetric = _listAdd(this.indexMetric, value, values);
			return this;
		}

		/**
		 * Indicates whether statistics are aggregated at the cluster, index, or shard
		 * level.
		 * <p>
		 * API name: {@code level}
		 */
		public final Builder level(@Nullable Level value) {
			this.level = value;
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
		 * Limit the information returned to the specified metrics
		 * <p>
		 * API name: {@code metric}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>metric</code>.
		 */
		public final Builder metric(List<String> list) {
			this.metric = _listAddAll(this.metric, list);
			return this;
		}

		/**
		 * Limit the information returned to the specified metrics
		 * <p>
		 * API name: {@code metric}
		 * <p>
		 * Adds one or more values to <code>metric</code>.
		 */
		public final Builder metric(String value, String... values) {
			this.metric = _listAdd(this.metric, value, values);
			return this;
		}

		/**
		 * Comma-separated list of node IDs or names used to limit returned information.
		 * <p>
		 * API name: {@code node_id}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>nodeId</code>.
		 */
		public final Builder nodeId(List<String> list) {
			this.nodeId = _listAddAll(this.nodeId, list);
			return this;
		}

		/**
		 * Comma-separated list of node IDs or names used to limit returned information.
		 * <p>
		 * API name: {@code node_id}
		 * <p>
		 * Adds one or more values to <code>nodeId</code>.
		 */
		public final Builder nodeId(String value, String... values) {
			this.nodeId = _listAdd(this.nodeId, value, values);
			return this;
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
		 * A comma-separated list of document types for the indexing index metric.
		 * <p>
		 * API name: {@code types}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>types</code>.
		 */
		public final Builder types(List<String> list) {
			this.types = _listAddAll(this.types, list);
			return this;
		}

		/**
		 * A comma-separated list of document types for the indexing index metric.
		 * <p>
		 * API name: {@code types}
		 * <p>
		 * Adds one or more values to <code>types</code>.
		 */
		public final Builder types(String value, String... values) {
			this.types = _listAdd(this.types, value, values);
			return this;
		}

		/**
		 * Builds a {@link NodesStatsRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodesStatsRequest build() {
			_checkSingleUse();

			return new NodesStatsRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code nodes.stats}".
	 */
	public static final Endpoint<NodesStatsRequest, NodesStatsResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "GET";

			},

			// Request path
			request -> {
				final int _metric = 1 << 0;
				final int _indexMetric = 1 << 1;
				final int _nodeId = 1 << 2;

				int propsSet = 0;

				if (ApiTypeHelper.isDefined(request.metric()))
					propsSet |= _metric;
				if (ApiTypeHelper.isDefined(request.indexMetric()))
					propsSet |= _indexMetric;
				if (ApiTypeHelper.isDefined(request.nodeId()))
					propsSet |= _nodeId;

				if (propsSet == 0) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_nodes");
					buf.append("/stats");
					return buf.toString();
				}
				if (propsSet == (_nodeId)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_nodes");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.nodeId.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					buf.append("/stats");
					return buf.toString();
				}
				if (propsSet == (_metric)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_nodes");
					buf.append("/stats");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.metric.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					return buf.toString();
				}
				if (propsSet == (_nodeId | _metric)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_nodes");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.nodeId.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					buf.append("/stats");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.metric.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					return buf.toString();
				}
				if (propsSet == (_metric | _indexMetric)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_nodes");
					buf.append("/stats");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.metric.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					buf.append("/");
					SimpleEndpoint.pathEncode(request.indexMetric.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					return buf.toString();
				}
				if (propsSet == (_nodeId | _metric | _indexMetric)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_nodes");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.nodeId.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					buf.append("/stats");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.metric.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					buf.append("/");
					SimpleEndpoint.pathEncode(request.indexMetric.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
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
				if (ApiTypeHelper.isDefined(request.types)) {
					params.put("types", request.types.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (request.level != null) {
					params.put("level", request.level.jsonValue());
				}
				if (ApiTypeHelper.isDefined(request.completionFields)) {
					params.put("completion_fields",
							request.completionFields.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (ApiTypeHelper.isDefined(request.fielddataFields)) {
					params.put("fielddata_fields",
							request.fielddataFields.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (request.groups != null) {
					params.put("groups", String.valueOf(request.groups));
				}
				if (request.includeUnloadedSegments != null) {
					params.put("include_unloaded_segments", String.valueOf(request.includeUnloadedSegments));
				}
				if (ApiTypeHelper.isDefined(request.fields)) {
					params.put("fields", request.fields.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (request.includeSegmentFileSizes != null) {
					params.put("include_segment_file_sizes", String.valueOf(request.includeSegmentFileSizes));
				}
				if (request.timeout != null) {
					params.put("timeout", request.timeout._toJsonString());
				}
				return params;

			}, SimpleEndpoint.emptyMap(), false, NodesStatsResponse._DESERIALIZER);
}
