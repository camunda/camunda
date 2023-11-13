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

package org.opensearch.client.opensearch.core;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch.core.search.SourceConfigParam;
import org.opensearch.client.json.JsonpDeserializer;
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

// typedef: _global.get_source.Request

/**
 * Returns the source of a document.
 *
 */

public class GetSourceRequest {
	@Nullable
	private final SourceConfigParam source;

	private final List<String> sourceExcludes;

	private final List<String> sourceIncludes;

	private final String id;

	private final String index;

	@Nullable
	private final String preference;

	@Nullable
	private final Boolean realtime;

	@Nullable
	private final Boolean refresh;

	@Nullable
	private final String routing;

	private final List<String> storedFields;

	@Nullable
	private final Long version;

	@Nullable
	private final VersionType versionType;

	// ---------------------------------------------------------------------------------------------

	private GetSourceRequest(Builder builder) {

		this.source = builder.source;
		this.sourceExcludes = ApiTypeHelper.unmodifiable(builder.sourceExcludes);
		this.sourceIncludes = ApiTypeHelper.unmodifiable(builder.sourceIncludes);
		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.preference = builder.preference;
		this.realtime = builder.realtime;
		this.refresh = builder.refresh;
		this.routing = builder.routing;
		this.storedFields = ApiTypeHelper.unmodifiable(builder.storedFields);
		this.version = builder.version;
		this.versionType = builder.versionType;

	}

	public static GetSourceRequest of(Function<Builder, ObjectBuilder<GetSourceRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * True or false to return the _source field or not, or a list of fields to
	 * return.
	 * <p>
	 * API name: {@code _source}
	 */
	@Nullable
	public final SourceConfigParam source() {
		return this.source;
	}

	/**
	 * A comma-separated list of source fields to exclude in the response.
	 * <p>
	 * API name: {@code _source_excludes}
	 */
	public final List<String> sourceExcludes() {
		return this.sourceExcludes;
	}

	/**
	 * A comma-separated list of source fields to include in the response.
	 * <p>
	 * API name: {@code _source_includes}
	 */
	public final List<String> sourceIncludes() {
		return this.sourceIncludes;
	}

	/**
	 * Required - Unique identifier of the document.
	 * <p>
	 * API name: {@code id}
	 */
	public final String id() {
		return this.id;
	}

	/**
	 * Required - Name of the index that contains the document.
	 * <p>
	 * API name: {@code index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * Specifies the node or shard the operation should be performed on. Random by
	 * default.
	 * <p>
	 * API name: {@code preference}
	 */
	@Nullable
	public final String preference() {
		return this.preference;
	}

	/**
	 * Boolean) If true, the request is real-time as opposed to near-real-time.
	 * <p>
	 * API name: {@code realtime}
	 */
	@Nullable
	public final Boolean realtime() {
		return this.realtime;
	}

	/**
	 * If true, Elasticsearch refreshes the affected shards to make this operation
	 * visible to search. If false, do nothing with refreshes.
	 * <p>
	 * API name: {@code refresh}
	 */
	@Nullable
	public final Boolean refresh() {
		return this.refresh;
	}

	/**
	 * Target the specified primary shard.
	 * <p>
	 * API name: {@code routing}
	 */
	@Nullable
	public final String routing() {
		return this.routing;
	}

	/**
	 * API name: {@code stored_fields}
	 */
	public final List<String> storedFields() {
		return this.storedFields;
	}

	/**
	 * Explicit version number for concurrency control. The specified version must
	 * match the current version of the document for the request to succeed.
	 * <p>
	 * API name: {@code version}
	 */
	@Nullable
	public final Long version() {
		return this.version;
	}

	/**
	 * Specific version type: internal, external, external_gte.
	 * <p>
	 * API name: {@code version_type}
	 */
	@Nullable
	public final VersionType versionType() {
		return this.versionType;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GetSourceRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GetSourceRequest> {
		@Nullable
		private SourceConfigParam source;

		@Nullable
		private List<String> sourceExcludes;

		@Nullable
		private List<String> sourceIncludes;

		private String id;

		private String index;

		@Nullable
		private String preference;

		@Nullable
		private Boolean realtime;

		@Nullable
		private Boolean refresh;

		@Nullable
		private String routing;

		@Nullable
		private List<String> storedFields;

		@Nullable
		private Long version;

		@Nullable
		private VersionType versionType;

		/**
		 * True or false to return the _source field or not, or a list of fields to
		 * return.
		 * <p>
		 * API name: {@code _source}
		 */
		public final Builder source(@Nullable SourceConfigParam value) {
			this.source = value;
			return this;
		}

		/**
		 * True or false to return the _source field or not, or a list of fields to
		 * return.
		 * <p>
		 * API name: {@code _source}
		 */
		public final Builder source(Function<SourceConfigParam.Builder, ObjectBuilder<SourceConfigParam>> fn) {
			return this.source(fn.apply(new SourceConfigParam.Builder()).build());
		}

		/**
		 * A comma-separated list of source fields to exclude in the response.
		 * <p>
		 * API name: {@code _source_excludes}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>sourceExcludes</code>.
		 */
		public final Builder sourceExcludes(List<String> list) {
			this.sourceExcludes = _listAddAll(this.sourceExcludes, list);
			return this;
		}

		/**
		 * A comma-separated list of source fields to exclude in the response.
		 * <p>
		 * API name: {@code _source_excludes}
		 * <p>
		 * Adds one or more values to <code>sourceExcludes</code>.
		 */
		public final Builder sourceExcludes(String value, String... values) {
			this.sourceExcludes = _listAdd(this.sourceExcludes, value, values);
			return this;
		}

		/**
		 * A comma-separated list of source fields to include in the response.
		 * <p>
		 * API name: {@code _source_includes}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>sourceIncludes</code>.
		 */
		public final Builder sourceIncludes(List<String> list) {
			this.sourceIncludes = _listAddAll(this.sourceIncludes, list);
			return this;
		}

		/**
		 * A comma-separated list of source fields to include in the response.
		 * <p>
		 * API name: {@code _source_includes}
		 * <p>
		 * Adds one or more values to <code>sourceIncludes</code>.
		 */
		public final Builder sourceIncludes(String value, String... values) {
			this.sourceIncludes = _listAdd(this.sourceIncludes, value, values);
			return this;
		}

		/**
		 * Required - Unique identifier of the document.
		 * <p>
		 * API name: {@code id}
		 */
		public final Builder id(String value) {
			this.id = value;
			return this;
		}

		/**
		 * Required - Name of the index that contains the document.
		 * <p>
		 * API name: {@code index}
		 */
		public final Builder index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * Specifies the node or shard the operation should be performed on. Random by
		 * default.
		 * <p>
		 * API name: {@code preference}
		 */
		public final Builder preference(@Nullable String value) {
			this.preference = value;
			return this;
		}

		/**
		 * Boolean) If true, the request is real-time as opposed to near-real-time.
		 * <p>
		 * API name: {@code realtime}
		 */
		public final Builder realtime(@Nullable Boolean value) {
			this.realtime = value;
			return this;
		}

		/**
		 * If true, Elasticsearch refreshes the affected shards to make this operation
		 * visible to search. If false, do nothing with refreshes.
		 * <p>
		 * API name: {@code refresh}
		 */
		public final Builder refresh(@Nullable Boolean value) {
			this.refresh = value;
			return this;
		}

		/**
		 * Target the specified primary shard.
		 * <p>
		 * API name: {@code routing}
		 */
		public final Builder routing(@Nullable String value) {
			this.routing = value;
			return this;
		}

		/**
		 * API name: {@code stored_fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>storedFields</code>.
		 */
		public final Builder storedFields(List<String> list) {
			this.storedFields = _listAddAll(this.storedFields, list);
			return this;
		}

		/**
		 * API name: {@code stored_fields}
		 * <p>
		 * Adds one or more values to <code>storedFields</code>.
		 */
		public final Builder storedFields(String value, String... values) {
			this.storedFields = _listAdd(this.storedFields, value, values);
			return this;
		}

		/**
		 * Explicit version number for concurrency control. The specified version must
		 * match the current version of the document for the request to succeed.
		 * <p>
		 * API name: {@code version}
		 */
		public final Builder version(@Nullable Long value) {
			this.version = value;
			return this;
		}

		/**
		 * Specific version type: internal, external, external_gte.
		 * <p>
		 * API name: {@code version_type}
		 */
		public final Builder versionType(@Nullable VersionType value) {
			this.versionType = value;
			return this;
		}

		/**
		 * Builds a {@link GetSourceRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GetSourceRequest build() {
			_checkSingleUse();

			return new GetSourceRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code get_source}".
	 */
	public static final SimpleEndpoint<GetSourceRequest, ?> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "GET";

			},

			// Request path
			request -> {
				final int _index = 1 << 0;
				final int _id = 1 << 1;

				int propsSet = 0;

				propsSet |= _index;
				propsSet |= _id;

				if (propsSet == (_index | _id)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index, buf);
					buf.append("/_source");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.id, buf);
					return buf.toString();
				}
				throw SimpleEndpoint.noPathTemplateFound("path");

			},

			// Request parameters
			request -> {
				Map<String, String> params = new HashMap<>();
				if (request.routing != null) {
					params.put("routing", request.routing);
				}
				if (request.realtime != null) {
					params.put("realtime", String.valueOf(request.realtime));
				}
				if (request.versionType != null) {
					params.put("version_type", request.versionType.jsonValue());
				}
				if (ApiTypeHelper.isDefined(request.storedFields)) {
					params.put("stored_fields",
							request.storedFields.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (request.preference != null) {
					params.put("preference", request.preference);
				}
				if (request.refresh != null) {
					params.put("refresh", String.valueOf(request.refresh));
				}
				if (request.source != null) {
					params.put("_source", request.source._toJsonString());
				}
				if (ApiTypeHelper.isDefined(request.sourceExcludes)) {
					params.put("_source_excludes",
							request.sourceExcludes.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (ApiTypeHelper.isDefined(request.sourceIncludes)) {
					params.put("_source_includes",
							request.sourceIncludes.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (request.version != null) {
					params.put("version", String.valueOf(request.version));
				}
				return params;

			}, SimpleEndpoint.emptyMap(), false, GetSourceResponse._DESERIALIZER);

	/**
	 * Create an "{@code get_source}" endpoint.
	 */
	public static <TDocument> Endpoint<GetSourceRequest, GetSourceResponse<TDocument>, ErrorResponse> createGetSourceEndpoint(
			JsonpDeserializer<TDocument> tDocumentDeserializer) {
		return _ENDPOINT
				.withResponseDeserializer(GetSourceResponse.createGetSourceResponseDeserializer(tDocumentDeserializer));
	}
}
