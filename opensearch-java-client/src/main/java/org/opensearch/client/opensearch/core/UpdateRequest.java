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
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.opensearch._types.WaitForActiveShards;
import org.opensearch.client.opensearch.core.search.SourceConfig;
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
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

// typedef: _global.update.Request

/**
 * Updates a document with a script or partial document.
 *
 */

public class UpdateRequest<TDocument, TPartialDocument> extends RequestBase implements JsonpSerializable {
	@Nullable
	private final SourceConfig source;

	@Nullable
	private final Boolean detectNoop;

	@Nullable
	private final TPartialDocument doc;

	@Nullable
	private final Boolean docAsUpsert;

	private final String id;

	@Nullable
	private final Long ifPrimaryTerm;

	@Nullable
	private final Long ifSeqNo;

	private final String index;

	@Nullable
	private final String lang;

	@Nullable
	private final Refresh refresh;

	@Nullable
	private final Boolean requireAlias;

	@Nullable
	private final Integer retryOnConflict;

	@Nullable
	private final String routing;

	@Nullable
	private final Script script;

	@Nullable
	private final Boolean scriptedUpsert;

	@Nullable
	private final Time timeout;

	@Nullable
	private final TDocument upsert;

	@Nullable
	private final WaitForActiveShards waitForActiveShards;

	@Nullable
	private final JsonpSerializer<TDocument> tDocumentSerializer;

	@Nullable
	private final JsonpSerializer<TPartialDocument> tPartialDocumentSerializer;

	// ---------------------------------------------------------------------------------------------

	private UpdateRequest(Builder<TDocument, TPartialDocument> builder) {

		this.source = builder.source;
		this.detectNoop = builder.detectNoop;
		this.doc = builder.doc;
		this.docAsUpsert = builder.docAsUpsert;
		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.ifPrimaryTerm = builder.ifPrimaryTerm;
		this.ifSeqNo = builder.ifSeqNo;
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.lang = builder.lang;
		this.refresh = builder.refresh;
		this.requireAlias = builder.requireAlias;
		this.retryOnConflict = builder.retryOnConflict;
		this.routing = builder.routing;
		this.script = builder.script;
		this.scriptedUpsert = builder.scriptedUpsert;
		this.timeout = builder.timeout;
		this.upsert = builder.upsert;
		this.waitForActiveShards = builder.waitForActiveShards;
		this.tDocumentSerializer = builder.tDocumentSerializer;
		this.tPartialDocumentSerializer = builder.tPartialDocumentSerializer;

	}

	public static <TDocument, TPartialDocument> UpdateRequest<TDocument, TPartialDocument> of(
			Function<Builder<TDocument, TPartialDocument>, ObjectBuilder<UpdateRequest<TDocument, TPartialDocument>>> fn) {
		return fn.apply(new Builder<>()).build();
	}

	/**
	 * Set to false to disable source retrieval. You can also specify a
	 * comma-separated list of the fields you want to retrieve.
	 * <p>
	 * API name: {@code _source}
	 */
	@Nullable
	public final SourceConfig source() {
		return this.source;
	}

	/**
	 * Set to false to disable setting 'result' in the response to 'noop' if no
	 * change to the document occurred.
	 * <p>
	 * API name: {@code detect_noop}
	 */
	@Nullable
	public final Boolean detectNoop() {
		return this.detectNoop;
	}

	/**
	 * A partial update to an existing document.
	 * <p>
	 * API name: {@code doc}
	 */
	@Nullable
	public final TPartialDocument doc() {
		return this.doc;
	}

	/**
	 * Set to true to use the contents of 'doc' as the value of 'upsert'
	 * <p>
	 * API name: {@code doc_as_upsert}
	 */
	@Nullable
	public final Boolean docAsUpsert() {
		return this.docAsUpsert;
	}

	/**
	 * Required - Document ID
	 * <p>
	 * API name: {@code id}
	 */
	public final String id() {
		return this.id;
	}

	/**
	 * Only perform the operation if the document has this primary term.
	 * <p>
	 * API name: {@code if_primary_term}
	 */
	@Nullable
	public final Long ifPrimaryTerm() {
		return this.ifPrimaryTerm;
	}

	/**
	 * Only perform the operation if the document has this sequence number.
	 * <p>
	 * API name: {@code if_seq_no}
	 */
	@Nullable
	public final Long ifSeqNo() {
		return this.ifSeqNo;
	}

	/**
	 * Required - The name of the index
	 * <p>
	 * API name: {@code index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * The script language.
	 * <p>
	 * API name: {@code lang}
	 */
	@Nullable
	public final String lang() {
		return this.lang;
	}

	/**
	 * If 'true', Elasticsearch refreshes the affected shards to make this operation
	 * visible to search, if 'wait_for' then wait for a refresh to make this
	 * operation visible to search, if 'false' do nothing with refreshes.
	 * <p>
	 * API name: {@code refresh}
	 */
	@Nullable
	public final Refresh refresh() {
		return this.refresh;
	}

	/**
	 * If true, the destination must be an index alias.
	 * <p>
	 * API name: {@code require_alias}
	 */
	@Nullable
	public final Boolean requireAlias() {
		return this.requireAlias;
	}

	/**
	 * Specify how many times should the operation be retried when a conflict
	 * occurs.
	 * <p>
	 * API name: {@code retry_on_conflict}
	 */
	@Nullable
	public final Integer retryOnConflict() {
		return this.retryOnConflict;
	}

	/**
	 * Custom value used to route operations to a specific shard.
	 * <p>
	 * API name: {@code routing}
	 */
	@Nullable
	public final String routing() {
		return this.routing;
	}

	/**
	 * Script to execute to update the document.
	 * <p>
	 * API name: {@code script}
	 */
	@Nullable
	public final Script script() {
		return this.script;
	}

	/**
	 * Set to true to execute the script whether or not the document exists.
	 * <p>
	 * API name: {@code scripted_upsert}
	 */
	@Nullable
	public final Boolean scriptedUpsert() {
		return this.scriptedUpsert;
	}

	/**
	 * Period to wait for dynamic mapping updates and active shards. This guarantees
	 * Elasticsearch waits for at least the timeout before failing. The actual wait
	 * time could be longer, particularly when multiple waits occur.
	 * <p>
	 * API name: {@code timeout}
	 */
	@Nullable
	public final Time timeout() {
		return this.timeout;
	}

	/**
	 * If the document does not already exist, the contents of 'upsert' are inserted
	 * as a new document. If the document exists, the 'script' is executed.
	 * <p>
	 * API name: {@code upsert}
	 */
	@Nullable
	public final TDocument upsert() {
		return this.upsert;
	}

	/**
	 * The number of shard copies that must be active before proceeding with the
	 * operations. Set to 'all' or any positive integer up to the total number of
	 * shards in the index (number_of_replicas+1). Defaults to 1 meaning the primary
	 * shard.
	 * <p>
	 * API name: {@code wait_for_active_shards}
	 */
	@Nullable
	public final WaitForActiveShards waitForActiveShards() {
		return this.waitForActiveShards;
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

		if (this.source != null) {
			generator.writeKey("_source");
			this.source.serialize(generator, mapper);

		}
		if (this.detectNoop != null) {
			generator.writeKey("detect_noop");
			generator.write(this.detectNoop);

		}
		if (this.doc != null) {
			generator.writeKey("doc");
			JsonpUtils.serialize(this.doc, generator, tPartialDocumentSerializer, mapper);

		}
		if (this.docAsUpsert != null) {
			generator.writeKey("doc_as_upsert");
			generator.write(this.docAsUpsert);

		}
		if (this.script != null) {
			generator.writeKey("script");
			this.script.serialize(generator, mapper);

		}
		if (this.scriptedUpsert != null) {
			generator.writeKey("scripted_upsert");
			generator.write(this.scriptedUpsert);

		}
		if (this.upsert != null) {
			generator.writeKey("upsert");
			JsonpUtils.serialize(this.upsert, generator, tDocumentSerializer, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link UpdateRequest}.
	 */

	public static class Builder<TDocument, TPartialDocument> extends ObjectBuilderBase
			implements
				ObjectBuilder<UpdateRequest<TDocument, TPartialDocument>> {
		@Nullable
		private SourceConfig source;

		@Nullable
		private Boolean detectNoop;

		@Nullable
		private TPartialDocument doc;

		@Nullable
		private Boolean docAsUpsert;

		private String id;

		@Nullable
		private Long ifPrimaryTerm;

		@Nullable
		private Long ifSeqNo;

		private String index;

		@Nullable
		private String lang;

		@Nullable
		private Refresh refresh;

		@Nullable
		private Boolean requireAlias;

		@Nullable
		private Integer retryOnConflict;

		@Nullable
		private String routing;

		@Nullable
		private Script script;

		@Nullable
		private Boolean scriptedUpsert;

		@Nullable
		private Time timeout;

		@Nullable
		private TDocument upsert;

		@Nullable
		private WaitForActiveShards waitForActiveShards;

		@Nullable
		private JsonpSerializer<TDocument> tDocumentSerializer;

		@Nullable
		private JsonpSerializer<TPartialDocument> tPartialDocumentSerializer;

		/**
		 * Set to false to disable source retrieval. You can also specify a
		 * comma-separated list of the fields you want to retrieve.
		 * <p>
		 * API name: {@code _source}
		 */
		public final Builder<TDocument, TPartialDocument> source(@Nullable SourceConfig value) {
			this.source = value;
			return this;
		}

		/**
		 * Set to false to disable source retrieval. You can also specify a
		 * comma-separated list of the fields you want to retrieve.
		 * <p>
		 * API name: {@code _source}
		 */
		public final Builder<TDocument, TPartialDocument> source(
				Function<SourceConfig.Builder, ObjectBuilder<SourceConfig>> fn) {
			return this.source(fn.apply(new SourceConfig.Builder()).build());
		}

		/**
		 * Set to false to disable setting 'result' in the response to 'noop' if no
		 * change to the document occurred.
		 * <p>
		 * API name: {@code detect_noop}
		 */
		public final Builder<TDocument, TPartialDocument> detectNoop(@Nullable Boolean value) {
			this.detectNoop = value;
			return this;
		}

		/**
		 * A partial update to an existing document.
		 * <p>
		 * API name: {@code doc}
		 */
		public final Builder<TDocument, TPartialDocument> doc(@Nullable TPartialDocument value) {
			this.doc = value;
			return this;
		}

		/**
		 * Set to true to use the contents of 'doc' as the value of 'upsert'
		 * <p>
		 * API name: {@code doc_as_upsert}
		 */
		public final Builder<TDocument, TPartialDocument> docAsUpsert(@Nullable Boolean value) {
			this.docAsUpsert = value;
			return this;
		}

		/**
		 * Required - Document ID
		 * <p>
		 * API name: {@code id}
		 */
		public final Builder<TDocument, TPartialDocument> id(String value) {
			this.id = value;
			return this;
		}

		/**
		 * Only perform the operation if the document has this primary term.
		 * <p>
		 * API name: {@code if_primary_term}
		 */
		public final Builder<TDocument, TPartialDocument> ifPrimaryTerm(@Nullable Long value) {
			this.ifPrimaryTerm = value;
			return this;
		}

		/**
		 * Only perform the operation if the document has this sequence number.
		 * <p>
		 * API name: {@code if_seq_no}
		 */
		public final Builder<TDocument, TPartialDocument> ifSeqNo(@Nullable Long value) {
			this.ifSeqNo = value;
			return this;
		}

		/**
		 * Required - The name of the index
		 * <p>
		 * API name: {@code index}
		 */
		public final Builder<TDocument, TPartialDocument> index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * The script language.
		 * <p>
		 * API name: {@code lang}
		 */
		public final Builder<TDocument, TPartialDocument> lang(@Nullable String value) {
			this.lang = value;
			return this;
		}

		/**
		 * If 'true', Elasticsearch refreshes the affected shards to make this operation
		 * visible to search, if 'wait_for' then wait for a refresh to make this
		 * operation visible to search, if 'false' do nothing with refreshes.
		 * <p>
		 * API name: {@code refresh}
		 */
		public final Builder<TDocument, TPartialDocument> refresh(@Nullable Refresh value) {
			this.refresh = value;
			return this;
		}

		/**
		 * If true, the destination must be an index alias.
		 * <p>
		 * API name: {@code require_alias}
		 */
		public final Builder<TDocument, TPartialDocument> requireAlias(@Nullable Boolean value) {
			this.requireAlias = value;
			return this;
		}

		/**
		 * Specify how many times should the operation be retried when a conflict
		 * occurs.
		 * <p>
		 * API name: {@code retry_on_conflict}
		 */
		public final Builder<TDocument, TPartialDocument> retryOnConflict(@Nullable Integer value) {
			this.retryOnConflict = value;
			return this;
		}

		/**
		 * Custom value used to route operations to a specific shard.
		 * <p>
		 * API name: {@code routing}
		 */
		public final Builder<TDocument, TPartialDocument> routing(@Nullable String value) {
			this.routing = value;
			return this;
		}

		/**
		 * Script to execute to update the document.
		 * <p>
		 * API name: {@code script}
		 */
		public final Builder<TDocument, TPartialDocument> script(@Nullable Script value) {
			this.script = value;
			return this;
		}

		/**
		 * Script to execute to update the document.
		 * <p>
		 * API name: {@code script}
		 */
		public final Builder<TDocument, TPartialDocument> script(Function<Script.Builder, ObjectBuilder<Script>> fn) {
			return this.script(fn.apply(new Script.Builder()).build());
		}

		/**
		 * Set to true to execute the script whether or not the document exists.
		 * <p>
		 * API name: {@code scripted_upsert}
		 */
		public final Builder<TDocument, TPartialDocument> scriptedUpsert(@Nullable Boolean value) {
			this.scriptedUpsert = value;
			return this;
		}

		/**
		 * Period to wait for dynamic mapping updates and active shards. This guarantees
		 * Elasticsearch waits for at least the timeout before failing. The actual wait
		 * time could be longer, particularly when multiple waits occur.
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder<TDocument, TPartialDocument> timeout(@Nullable Time value) {
			this.timeout = value;
			return this;
		}

		/**
		 * Period to wait for dynamic mapping updates and active shards. This guarantees
		 * Elasticsearch waits for at least the timeout before failing. The actual wait
		 * time could be longer, particularly when multiple waits occur.
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder<TDocument, TPartialDocument> timeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.timeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * If the document does not already exist, the contents of 'upsert' are inserted
		 * as a new document. If the document exists, the 'script' is executed.
		 * <p>
		 * API name: {@code upsert}
		 */
		public final Builder<TDocument, TPartialDocument> upsert(@Nullable TDocument value) {
			this.upsert = value;
			return this;
		}

		/**
		 * The number of shard copies that must be active before proceeding with the
		 * operations. Set to 'all' or any positive integer up to the total number of
		 * shards in the index (number_of_replicas+1). Defaults to 1 meaning the primary
		 * shard.
		 * <p>
		 * API name: {@code wait_for_active_shards}
		 */
		public final Builder<TDocument, TPartialDocument> waitForActiveShards(@Nullable WaitForActiveShards value) {
			this.waitForActiveShards = value;
			return this;
		}

		/**
		 * The number of shard copies that must be active before proceeding with the
		 * operations. Set to 'all' or any positive integer up to the total number of
		 * shards in the index (number_of_replicas+1). Defaults to 1 meaning the primary
		 * shard.
		 * <p>
		 * API name: {@code wait_for_active_shards}
		 */
		public final Builder<TDocument, TPartialDocument> waitForActiveShards(
				Function<WaitForActiveShards.Builder, ObjectBuilder<WaitForActiveShards>> fn) {
			return this.waitForActiveShards(fn.apply(new WaitForActiveShards.Builder()).build());
		}

		/**
		 * Serializer for TDocument. If not set, an attempt will be made to find a
		 * serializer from the JSON context.
		 */
		public final Builder<TDocument, TPartialDocument> tDocumentSerializer(
				@Nullable JsonpSerializer<TDocument> value) {
			this.tDocumentSerializer = value;
			return this;
		}

		/**
		 * Serializer for TPartialDocument. If not set, an attempt will be made to find
		 * a serializer from the JSON context.
		 */
		public final Builder<TDocument, TPartialDocument> tPartialDocumentSerializer(
				@Nullable JsonpSerializer<TPartialDocument> value) {
			this.tPartialDocumentSerializer = value;
			return this;
		}

		/**
		 * Builds a {@link UpdateRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public UpdateRequest<TDocument, TPartialDocument> build() {
			_checkSingleUse();

			return new UpdateRequest<TDocument, TPartialDocument>(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Create a JSON deserializer for UpdateRequest
	 */
	public static <TDocument, TPartialDocument>
	JsonpDeserializer<UpdateRequest<TDocument, TPartialDocument>> createUpdateRequestDeserializer(
			JsonpDeserializer<TDocument> tDocumentDeserializer,
			JsonpDeserializer<TPartialDocument> tPartialDocumentDeserializer) {
		return ObjectBuilderDeserializer.createForObject((Supplier<Builder<TDocument, TPartialDocument>>) Builder::new,
				op -> UpdateRequest.setupUpdateRequestDeserializer(op, tDocumentDeserializer,
						tPartialDocumentDeserializer));
	};

	protected static <TDocument, TPartialDocument> void setupUpdateRequestDeserializer(
			ObjectDeserializer<UpdateRequest.Builder<TDocument, TPartialDocument>> op,
			JsonpDeserializer<TDocument> tDocumentDeserializer,
			JsonpDeserializer<TPartialDocument> tPartialDocumentDeserializer) {

		op.add(Builder::source, SourceConfig._DESERIALIZER, "_source");
		op.add(Builder::detectNoop, JsonpDeserializer.booleanDeserializer(), "detect_noop");
		op.add(Builder::doc, tPartialDocumentDeserializer, "doc");
		op.add(Builder::docAsUpsert, JsonpDeserializer.booleanDeserializer(), "doc_as_upsert");
		op.add(Builder::script, Script._DESERIALIZER, "script");
		op.add(Builder::scriptedUpsert, JsonpDeserializer.booleanDeserializer(), "scripted_upsert");
		op.add(Builder::upsert, tDocumentDeserializer, "upsert");

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code update}".
	 */
	public static final SimpleEndpoint<UpdateRequest<?, ?>, ?> _ENDPOINT = new SimpleEndpoint<>(

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
				propsSet |= _id;

				if (propsSet == (_index | _id)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index, buf);
					buf.append("/_update");
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
				if (request.requireAlias != null) {
					params.put("require_alias", String.valueOf(request.requireAlias));
				}
				if (request.ifPrimaryTerm != null) {
					params.put("if_primary_term", String.valueOf(request.ifPrimaryTerm));
				}
				if (request.ifSeqNo != null) {
					params.put("if_seq_no", String.valueOf(request.ifSeqNo));
				}
				if (request.refresh != null) {
					params.put("refresh", request.refresh.jsonValue());
				}
				if (request.waitForActiveShards != null) {
					params.put("wait_for_active_shards", request.waitForActiveShards._toJsonString());
				}
				if (request.lang != null) {
					params.put("lang", request.lang);
				}
				if (request.retryOnConflict != null) {
					params.put("retry_on_conflict", String.valueOf(request.retryOnConflict));
				}
				if (request.timeout != null) {
					params.put("timeout", request.timeout._toJsonString());
				}
				return params;

			}, SimpleEndpoint.emptyMap(), true, UpdateResponse._DESERIALIZER);

	/**
	 * Create an "{@code update}" endpoint.
	 */
	public static <TDocument> Endpoint<UpdateRequest<?, ?>, UpdateResponse<TDocument>, ErrorResponse> createUpdateEndpoint(
			JsonpDeserializer<TDocument> tDocumentDeserializer) {
		return _ENDPOINT
				.withResponseDeserializer(UpdateResponse.createUpdateResponseDeserializer(tDocumentDeserializer));
	}
}
