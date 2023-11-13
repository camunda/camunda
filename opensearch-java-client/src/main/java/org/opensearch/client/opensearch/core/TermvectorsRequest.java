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
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch.core.termvectors.Filter;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.JsonpSerializer;
import org.opensearch.client.json.JsonpUtils;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

// typedef: _global.termvectors.Request

/**
 * Returns information and statistics about terms in the fields of a particular
 * document.
 * 
 */

public class TermvectorsRequest<TDocument> extends RequestBase implements JsonpSerializable {
	@Nullable
	private final TDocument doc;

	@Nullable
	private final Boolean fieldStatistics;

	private final List<String> fields;

	@Nullable
	private final Filter filter;

	@Nullable
	private final String id;

	private final String index;

	@Nullable
	private final Boolean offsets;

	@Nullable
	private final Boolean payloads;

	private final Map<String, String> perFieldAnalyzer;

	@Nullable
	private final Boolean positions;

	@Nullable
	private final String preference;

	@Nullable
	private final Boolean realtime;

	@Nullable
	private final String routing;

	@Nullable
	private final Boolean termStatistics;

	@Nullable
	private final Long version;

	@Nullable
	private final VersionType versionType;

	@Nullable
	private final JsonpSerializer<TDocument> tDocumentSerializer;

	// ---------------------------------------------------------------------------------------------

	private TermvectorsRequest(Builder<TDocument> builder) {

		this.doc = builder.doc;
		this.fieldStatistics = builder.fieldStatistics;
		this.fields = ApiTypeHelper.unmodifiable(builder.fields);
		this.filter = builder.filter;
		this.id = builder.id;
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.offsets = builder.offsets;
		this.payloads = builder.payloads;
		this.perFieldAnalyzer = ApiTypeHelper.unmodifiable(builder.perFieldAnalyzer);
		this.positions = builder.positions;
		this.preference = builder.preference;
		this.realtime = builder.realtime;
		this.routing = builder.routing;
		this.termStatistics = builder.termStatistics;
		this.version = builder.version;
		this.versionType = builder.versionType;
		this.tDocumentSerializer = builder.tDocumentSerializer;

	}

	public static <TDocument> TermvectorsRequest<TDocument> of(
			Function<Builder<TDocument>, ObjectBuilder<TermvectorsRequest<TDocument>>> fn) {
		return fn.apply(new Builder<>()).build();
	}

	/**
	 * API name: {@code doc}
	 */
	@Nullable
	public final TDocument doc() {
		return this.doc;
	}

	/**
	 * Specifies if document count, sum of document frequencies and sum of total
	 * term frequencies should be returned.
	 * <p>
	 * API name: {@code field_statistics}
	 */
	@Nullable
	public final Boolean fieldStatistics() {
		return this.fieldStatistics;
	}

	/**
	 * A comma-separated list of fields to return.
	 * <p>
	 * API name: {@code fields}
	 */
	public final List<String> fields() {
		return this.fields;
	}

	/**
	 * API name: {@code filter}
	 */
	@Nullable
	public final Filter filter() {
		return this.filter;
	}

	/**
	 * The id of the document, when not specified a doc param should be supplied.
	 * <p>
	 * API name: {@code id}
	 */
	@Nullable
	public final String id() {
		return this.id;
	}

	/**
	 * Required - The index in which the document resides.
	 * <p>
	 * API name: {@code index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * Specifies if term offsets should be returned.
	 * <p>
	 * API name: {@code offsets}
	 */
	@Nullable
	public final Boolean offsets() {
		return this.offsets;
	}

	/**
	 * Specifies if term payloads should be returned.
	 * <p>
	 * API name: {@code payloads}
	 */
	@Nullable
	public final Boolean payloads() {
		return this.payloads;
	}

	/**
	 * API name: {@code per_field_analyzer}
	 */
	public final Map<String, String> perFieldAnalyzer() {
		return this.perFieldAnalyzer;
	}

	/**
	 * Specifies if term positions should be returned.
	 * <p>
	 * API name: {@code positions}
	 */
	@Nullable
	public final Boolean positions() {
		return this.positions;
	}

	/**
	 * Specify the node or shard the operation should be performed on (default:
	 * random).
	 * <p>
	 * API name: {@code preference}
	 */
	@Nullable
	public final String preference() {
		return this.preference;
	}

	/**
	 * Specifies if request is real-time as opposed to near-real-time (default:
	 * true).
	 * <p>
	 * API name: {@code realtime}
	 */
	@Nullable
	public final Boolean realtime() {
		return this.realtime;
	}

	/**
	 * Specific routing value.
	 * <p>
	 * API name: {@code routing}
	 */
	@Nullable
	public final String routing() {
		return this.routing;
	}

	/**
	 * Specifies if total term frequency and document frequency should be returned.
	 * <p>
	 * API name: {@code term_statistics}
	 */
	@Nullable
	public final Boolean termStatistics() {
		return this.termStatistics;
	}

	/**
	 * Explicit version number for concurrency control
	 * <p>
	 * API name: {@code version}
	 */
	@Nullable
	public final Long version() {
		return this.version;
	}

	/**
	 * Specific version type
	 * <p>
	 * API name: {@code version_type}
	 */
	@Nullable
	public final VersionType versionType() {
		return this.versionType;
	}

	/**
	 * Serialize this object to JSON.
	 */
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		serializeInternal(generator, mapper);
		generator.writeEnd();
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		if (this.doc != null) {
			generator.writeKey("doc");
			JsonpUtils.serialize(this.doc, generator, tDocumentSerializer, mapper);

		}
		if (this.filter != null) {
			generator.writeKey("filter");
			this.filter.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.perFieldAnalyzer)) {
			generator.writeKey("per_field_analyzer");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.perFieldAnalyzer.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link TermvectorsRequest}.
	 */

	public static class Builder<TDocument> extends ObjectBuilderBase
			implements
				ObjectBuilder<TermvectorsRequest<TDocument>> {
		@Nullable
		private TDocument doc;

		@Nullable
		private Boolean fieldStatistics;

		@Nullable
		private List<String> fields;

		@Nullable
		private Filter filter;

		@Nullable
		private String id;

		private String index;

		@Nullable
		private Boolean offsets;

		@Nullable
		private Boolean payloads;

		@Nullable
		private Map<String, String> perFieldAnalyzer;

		@Nullable
		private Boolean positions;

		@Nullable
		private String preference;

		@Nullable
		private Boolean realtime;

		@Nullable
		private String routing;

		@Nullable
		private Boolean termStatistics;

		@Nullable
		private Long version;

		@Nullable
		private VersionType versionType;

		@Nullable
		private JsonpSerializer<TDocument> tDocumentSerializer;

		/**
		 * API name: {@code doc}
		 */
		public final Builder<TDocument> doc(@Nullable TDocument value) {
			this.doc = value;
			return this;
		}

		/**
		 * Specifies if document count, sum of document frequencies and sum of total
		 * term frequencies should be returned.
		 * <p>
		 * API name: {@code field_statistics}
		 */
		public final Builder<TDocument> fieldStatistics(@Nullable Boolean value) {
			this.fieldStatistics = value;
			return this;
		}

		/**
		 * A comma-separated list of fields to return.
		 * <p>
		 * API name: {@code fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>fields</code>.
		 */
		public final Builder<TDocument> fields(List<String> list) {
			this.fields = _listAddAll(this.fields, list);
			return this;
		}

		/**
		 * A comma-separated list of fields to return.
		 * <p>
		 * API name: {@code fields}
		 * <p>
		 * Adds one or more values to <code>fields</code>.
		 */
		public final Builder<TDocument> fields(String value, String... values) {
			this.fields = _listAdd(this.fields, value, values);
			return this;
		}

		/**
		 * API name: {@code filter}
		 */
		public final Builder<TDocument> filter(@Nullable Filter value) {
			this.filter = value;
			return this;
		}

		/**
		 * API name: {@code filter}
		 */
		public final Builder<TDocument> filter(Function<Filter.Builder, ObjectBuilder<Filter>> fn) {
			return this.filter(fn.apply(new Filter.Builder()).build());
		}

		/**
		 * The id of the document, when not specified a doc param should be supplied.
		 * <p>
		 * API name: {@code id}
		 */
		public final Builder<TDocument> id(@Nullable String value) {
			this.id = value;
			return this;
		}

		/**
		 * Required - The index in which the document resides.
		 * <p>
		 * API name: {@code index}
		 */
		public final Builder<TDocument> index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * Specifies if term offsets should be returned.
		 * <p>
		 * API name: {@code offsets}
		 */
		public final Builder<TDocument> offsets(@Nullable Boolean value) {
			this.offsets = value;
			return this;
		}

		/**
		 * Specifies if term payloads should be returned.
		 * <p>
		 * API name: {@code payloads}
		 */
		public final Builder<TDocument> payloads(@Nullable Boolean value) {
			this.payloads = value;
			return this;
		}

		/**
		 * API name: {@code per_field_analyzer}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>perFieldAnalyzer</code>.
		 */
		public final Builder<TDocument> perFieldAnalyzer(Map<String, String> map) {
			this.perFieldAnalyzer = _mapPutAll(this.perFieldAnalyzer, map);
			return this;
		}

		/**
		 * API name: {@code per_field_analyzer}
		 * <p>
		 * Adds an entry to <code>perFieldAnalyzer</code>.
		 */
		public final Builder<TDocument> perFieldAnalyzer(String key, String value) {
			this.perFieldAnalyzer = _mapPut(this.perFieldAnalyzer, key, value);
			return this;
		}

		/**
		 * Specifies if term positions should be returned.
		 * <p>
		 * API name: {@code positions}
		 */
		public final Builder<TDocument> positions(@Nullable Boolean value) {
			this.positions = value;
			return this;
		}

		/**
		 * Specify the node or shard the operation should be performed on (default:
		 * random).
		 * <p>
		 * API name: {@code preference}
		 */
		public final Builder<TDocument> preference(@Nullable String value) {
			this.preference = value;
			return this;
		}

		/**
		 * Specifies if request is real-time as opposed to near-real-time (default:
		 * true).
		 * <p>
		 * API name: {@code realtime}
		 */
		public final Builder<TDocument> realtime(@Nullable Boolean value) {
			this.realtime = value;
			return this;
		}

		/**
		 * Specific routing value.
		 * <p>
		 * API name: {@code routing}
		 */
		public final Builder<TDocument> routing(@Nullable String value) {
			this.routing = value;
			return this;
		}

		/**
		 * Specifies if total term frequency and document frequency should be returned.
		 * <p>
		 * API name: {@code term_statistics}
		 */
		public final Builder<TDocument> termStatistics(@Nullable Boolean value) {
			this.termStatistics = value;
			return this;
		}

		/**
		 * Explicit version number for concurrency control
		 * <p>
		 * API name: {@code version}
		 */
		public final Builder<TDocument> version(@Nullable Long value) {
			this.version = value;
			return this;
		}

		/**
		 * Specific version type
		 * <p>
		 * API name: {@code version_type}
		 */
		public final Builder<TDocument> versionType(@Nullable VersionType value) {
			this.versionType = value;
			return this;
		}

		/**
		 * Serializer for TDocument. If not set, an attempt will be made to find a
		 * serializer from the JSON context.
		 */
		public final Builder<TDocument> tDocumentSerializer(@Nullable JsonpSerializer<TDocument> value) {
			this.tDocumentSerializer = value;
			return this;
		}

		/**
		 * Builds a {@link TermvectorsRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public TermvectorsRequest<TDocument> build() {
			_checkSingleUse();

			return new TermvectorsRequest<TDocument>(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Create a JSON deserializer for TermvectorsRequest
	 */
	public static <TDocument> JsonpDeserializer<TermvectorsRequest<TDocument>> createTermvectorsRequestDeserializer(
			JsonpDeserializer<TDocument> tDocumentDeserializer) {
		return ObjectBuilderDeserializer.createForObject((Supplier<Builder<TDocument>>) Builder::new,
				op -> TermvectorsRequest.setupTermvectorsRequestDeserializer(op, tDocumentDeserializer));
	};

	protected static <TDocument> void setupTermvectorsRequestDeserializer(
			ObjectDeserializer<TermvectorsRequest.Builder<TDocument>> op,
			JsonpDeserializer<TDocument> tDocumentDeserializer) {

		op.add(Builder::doc, tDocumentDeserializer, "doc");
		op.add(Builder::filter, Filter._DESERIALIZER, "filter");
		op.add(Builder::perFieldAnalyzer,
				JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()), "per_field_analyzer");

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code termvectors}".
	 */
	public static final Endpoint<TermvectorsRequest<?>, TermvectorsResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "POST";

			},

			// Request path
			request -> {
				final int _index = 1 << 0;
				final int _id = 1 << 1;

				int propsSet = 0;

				propsSet |= _index;
				if (request.id() != null)
					propsSet |= _id;

				if (propsSet == (_index | _id)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index, buf);
					buf.append("/_termvectors");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.id, buf);
					return buf.toString();
				}
				if (propsSet == (_index)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index, buf);
					buf.append("/_termvectors");
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
				if (request.termStatistics != null) {
					params.put("term_statistics", String.valueOf(request.termStatistics));
				}
				if (request.offsets != null) {
					params.put("offsets", String.valueOf(request.offsets));
				}
				if (request.payloads != null) {
					params.put("payloads", String.valueOf(request.payloads));
				}
				if (request.versionType != null) {
					params.put("version_type", request.versionType.jsonValue());
				}
				if (request.preference != null) {
					params.put("preference", request.preference);
				}
				if (request.positions != null) {
					params.put("positions", String.valueOf(request.positions));
				}
				if (request.fieldStatistics != null) {
					params.put("field_statistics", String.valueOf(request.fieldStatistics));
				}
				if (ApiTypeHelper.isDefined(request.fields)) {
					params.put("fields", request.fields.stream().map(v -> v).collect(Collectors.joining(",")));
				}
				if (request.version != null) {
					params.put("version", String.valueOf(request.version));
				}
				return params;

			}, SimpleEndpoint.emptyMap(), true, TermvectorsResponse._DESERIALIZER);
}
