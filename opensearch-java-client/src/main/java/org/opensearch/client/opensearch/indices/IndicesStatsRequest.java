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

package org.opensearch.client.opensearch.indices;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.ExpandWildcard;
import org.opensearch.client.opensearch._types.Level;
import org.opensearch.client.opensearch._types.RequestBase;
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

// typedef: indices.stats.Request

/**
 * Provides statistics on operations happening in an index.
 *
 */

public class IndicesStatsRequest extends RequestBase {
	private final List<String> completionFields;

	private final List<ExpandWildcard> expandWildcards;

	private final List<String> fielddataFields;

	private final List<String> fields;

	@Nullable
	private final Boolean forbidClosedIndices;

	private final List<String> groups;

	@Nullable
	private final Boolean includeSegmentFileSizes;

	@Nullable
	private final Boolean includeUnloadedSegments;

	private final List<String> index;

	@Nullable
	private final Level level;

	private final List<String> metric;

	private final List<String> types;

	// ---------------------------------------------------------------------------------------------

	private IndicesStatsRequest(Builder builder) {

		this.completionFields = ApiTypeHelper.unmodifiable(builder.completionFields);
		this.expandWildcards = ApiTypeHelper.unmodifiable(builder.expandWildcards);
		this.fielddataFields = ApiTypeHelper.unmodifiable(builder.fielddataFields);
		this.fields = ApiTypeHelper.unmodifiable(builder.fields);
		this.forbidClosedIndices = builder.forbidClosedIndices;
		this.groups = ApiTypeHelper.unmodifiable(builder.groups);
		this.includeSegmentFileSizes = builder.includeSegmentFileSizes;
		this.includeUnloadedSegments = builder.includeUnloadedSegments;
		this.index = ApiTypeHelper.unmodifiable(builder.index);
		this.level = builder.level;
		this.metric = ApiTypeHelper.unmodifiable(builder.metric);
		this.types = ApiTypeHelper.unmodifiable(builder.types);

	}

	public static IndicesStatsRequest of(Function<Builder, ObjectBuilder<IndicesStatsRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * A comma-separated list of fields for <code>fielddata</code> and
	 * <code>suggest</code> index metric (supports wildcards)
	 * <p>
	 * API name: {@code completion_fields}
	 */
	public final List<String> completionFields() {
		return this.completionFields;
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
	 * A comma-separated list of fields for <code>fielddata</code> index metric
	 * (supports wildcards)
	 * <p>
	 * API name: {@code fielddata_fields}
	 */
	public final List<String> fielddataFields() {
		return this.fielddataFields;
	}

	/**
	 * A comma-separated list of fields for <code>fielddata</code> and
	 * <code>completion</code> index metric (supports wildcards)
	 * <p>
	 * API name: {@code fields}
	 */
	public final List<String> fields() {
		return this.fields;
	}

	/**
	 * If set to false stats will also collected from closed indices if explicitly
	 * specified or if expand_wildcards expands to closed indices
	 * <p>
	 * API name: {@code forbid_closed_indices}
	 */
	@Nullable
	public final Boolean forbidClosedIndices() {
		return this.forbidClosedIndices;
	}

	/**
	 * A comma-separated list of search groups for <code>search</code> index metric
	 * <p>
	 * API name: {@code groups}
	 */
	public final List<String> groups() {
		return this.groups;
	}

	/**
	 * Whether to report the aggregated disk usage of each one of the Lucene index
	 * files (only applies if segment stats are requested)
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
	 * A comma-separated list of index names; use <code>_all</code> or empty string
	 * to perform the operation on all indices
	 * <p>
	 * API name: {@code index}
	 */
	public final List<String> index() {
		return this.index;
	}

	/**
	 * Return stats aggregated at cluster, index or shard level
	 * <p>
	 * API name: {@code level}
	 */
	@Nullable
	public final Level level() {
		return this.level;
	}

	/**
	 * Limit the information returned the specific metrics.
	 * <p>
	 * API name: {@code metric}
	 */
	public final List<String> metric() {
		return this.metric;
	}

	/**
	 * A comma-separated list of document types for the <code>indexing</code> index
	 * metric
	 * <p>
	 * API name: {@code types}
	 */
	public final List<String> types() {
		return this.types;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndicesStatsRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndicesStatsRequest> {
		@Nullable
		private List<String> completionFields;

		@Nullable
		private List<ExpandWildcard> expandWildcards;

		@Nullable
		private List<String> fielddataFields;

		@Nullable
		private List<String> fields;

		@Nullable
		private Boolean forbidClosedIndices;

		@Nullable
		private List<String> groups;

		@Nullable
		private Boolean includeSegmentFileSizes;

		@Nullable
		private Boolean includeUnloadedSegments;

		@Nullable
		private List<String> index;

		@Nullable
		private Level level;

		@Nullable
		private List<String> metric;

		@Nullable
		private List<String> types;

		/**
		 * A comma-separated list of fields for <code>fielddata</code> and
		 * <code>suggest</code> index metric (supports wildcards)
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
		 * A comma-separated list of fields for <code>fielddata</code> and
		 * <code>suggest</code> index metric (supports wildcards)
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
		 * A comma-separated list of fields for <code>fielddata</code> index metric
		 * (supports wildcards)
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
		 * A comma-separated list of fields for <code>fielddata</code> index metric
		 * (supports wildcards)
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
		 * A comma-separated list of fields for <code>fielddata</code> and
		 * <code>completion</code> index metric (supports wildcards)
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
		 * A comma-separated list of fields for <code>fielddata</code> and
		 * <code>completion</code> index metric (supports wildcards)
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
		 * If set to false stats will also collected from closed indices if explicitly
		 * specified or if expand_wildcards expands to closed indices
		 * <p>
		 * API name: {@code forbid_closed_indices}
		 */
		public final Builder forbidClosedIndices(@Nullable Boolean value) {
			this.forbidClosedIndices = value;
			return this;
		}

		/**
		 * A comma-separated list of search groups for <code>search</code> index metric
		 * <p>
		 * API name: {@code groups}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>groups</code>.
		 */
		public final Builder groups(List<String> list) {
			this.groups = _listAddAll(this.groups, list);
			return this;
		}

		/**
		 * A comma-separated list of search groups for <code>search</code> index metric
		 * <p>
		 * API name: {@code groups}
		 * <p>
		 * Adds one or more values to <code>groups</code>.
		 */
		public final Builder groups(String value, String... values) {
			this.groups = _listAdd(this.groups, value, values);
			return this;
		}

		/**
		 * Whether to report the aggregated disk usage of each one of the Lucene index
		 * files (only applies if segment stats are requested)
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
		 * A comma-separated list of index names; use <code>_all</code> or empty string
		 * to perform the operation on all indices
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
		 * A comma-separated list of index names; use <code>_all</code> or empty string
		 * to perform the operation on all indices
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
		 * Return stats aggregated at cluster, index or shard level
		 * <p>
		 * API name: {@code level}
		 */
		public final Builder level(@Nullable Level value) {
			this.level = value;
			return this;
		}

		/**
		 * Limit the information returned the specific metrics.
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
		 * Limit the information returned the specific metrics.
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
		 * A comma-separated list of document types for the <code>indexing</code> index
		 * metric
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
		 * A comma-separated list of document types for the <code>indexing</code> index
		 * metric
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
		 * Builds a {@link IndicesStatsRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndicesStatsRequest build() {
			_checkSingleUse();

			return new IndicesStatsRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code indices.stats}".
	 */
	public static final Endpoint<IndicesStatsRequest, IndicesStatsResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "GET";

			},

			// Request path
			request -> {
				final int _metric = 1 << 0;
				final int _index = 1 << 1;

				int propsSet = 0;

				if (ApiTypeHelper.isDefined(request.metric()))
					propsSet |= _metric;
				if (ApiTypeHelper.isDefined(request.index()))
					propsSet |= _index;

				if (propsSet == 0) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_stats");
					return buf.toString();
				}
				if (propsSet == (_metric)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_stats");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.metric.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					return buf.toString();
				}
				if (propsSet == (_index)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index.stream().map(v -> v).collect(Collectors.joining(",")), buf);
					buf.append("/_stats");
					return buf.toString();
				}
				if (propsSet == (_index | _metric)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index.stream().map(v -> v).collect(Collectors.joining(",")), buf);
					buf.append("/_stats");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.metric.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					return buf.toString();
				}
				throw SimpleEndpoint.noPathTemplateFound("path");

			},

			// Request parameters
			request -> {
				Map<String, String> params = new HashMap<>();
				if (ApiTypeHelper.isDefined(request.types)) {
					params.put("types", request.types.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (ApiTypeHelper.isDefined(request.expandWildcards)) {
					params.put("expand_wildcards",
							request.expandWildcards.stream()
									.map(v -> v.jsonValue()).collect(Collectors.joining(",")));
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
				if (ApiTypeHelper.isDefined(request.groups)) {
					params.put("groups", request.groups.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (request.includeUnloadedSegments != null) {
					params.put("include_unloaded_segments", String.valueOf(request.includeUnloadedSegments));
				}
				if (ApiTypeHelper.isDefined(request.fields)) {
					params.put("fields", request.fields.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (request.forbidClosedIndices != null) {
					params.put("forbid_closed_indices", String.valueOf(request.forbidClosedIndices));
				}
				if (request.includeSegmentFileSizes != null) {
					params.put("include_segment_file_sizes", String.valueOf(request.includeSegmentFileSizes));
				}
				return params;

			}, SimpleEndpoint.emptyMap(), false, IndicesStatsResponse._DESERIALIZER);
}
