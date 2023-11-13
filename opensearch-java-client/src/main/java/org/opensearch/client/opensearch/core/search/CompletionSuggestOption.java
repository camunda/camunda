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

package org.opensearch.client.opensearch.core.search;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.JsonpSerializer;
import org.opensearch.client.json.JsonpUtils;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

// typedef: _global.search._types.CompletionSuggestOption



public class CompletionSuggestOption<TDocument> implements JsonpSerializable {
	@Nullable
	private final Boolean collateMatch;

	private final Map<String, List<Context>> contexts;

	private final Map<String, JsonData> fields;

	private final String id;

	private final String index;

	@Nullable
	private final String routing;

	private final double score;

	private final TDocument source;

	private final String text;

	@Nullable
	private final JsonpSerializer<TDocument> tDocumentSerializer;

	// ---------------------------------------------------------------------------------------------

	private CompletionSuggestOption(Builder<TDocument> builder) {

		this.collateMatch = builder.collateMatch;
		this.contexts = ApiTypeHelper.unmodifiable(builder.contexts);
		this.fields = ApiTypeHelper.unmodifiable(builder.fields);
		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.routing = builder.routing;
		this.score = ApiTypeHelper.requireNonNull(builder.score, this, "score");
		this.source = ApiTypeHelper.requireNonNull(builder.source, this, "source");
		this.text = ApiTypeHelper.requireNonNull(builder.text, this, "text");
		this.tDocumentSerializer = builder.tDocumentSerializer;

	}

	public static <TDocument> CompletionSuggestOption<TDocument> of(
			Function<Builder<TDocument>, ObjectBuilder<CompletionSuggestOption<TDocument>>> fn) {
		return fn.apply(new Builder<>()).build();
	}

	/**
	 * API name: {@code collate_match}
	 */
	@Nullable
	public final Boolean collateMatch() {
		return this.collateMatch;
	}

	/**
	 * API name: {@code contexts}
	 */
	public final Map<String, List<Context>> contexts() {
		return this.contexts;
	}

	/**
	 * API name: {@code fields}
	 */
	public final Map<String, JsonData> fields() {
		return this.fields;
	}

	/**
	 * Required - API name: {@code _id}
	 */
	public final String id() {
		return this.id;
	}

	/**
	 * Required - API name: {@code _index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * API name: {@code _routing}
	 */
	@Nullable
	public final String routing() {
		return this.routing;
	}

	/**
	 * Required - API name: {@code _score}
	 */
	public final double score() {
		return this.score;
	}

	/**
	 * Required - API name: {@code _source}
	 */
	public final TDocument source() {
		return this.source;
	}

	/**
	 * Required - API name: {@code text}
	 */
	public final String text() {
		return this.text;
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

		if (this.collateMatch != null) {
			generator.writeKey("collate_match");
			generator.write(this.collateMatch);

		}
		if (ApiTypeHelper.isDefined(this.contexts)) {
			generator.writeKey("contexts");
			generator.writeStartObject();
			for (Map.Entry<String, List<Context>> item0 : this.contexts.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.writeStartArray();
				if (item0.getValue() != null) {
					for (Context item1 : item0.getValue()) {
						item1.serialize(generator, mapper);

					}
				}
				generator.writeEnd();

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.fields)) {
			generator.writeKey("fields");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.fields.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("_id");
		generator.write(this.id);

		generator.writeKey("_index");
		generator.write(this.index);

		if (this.routing != null) {
			generator.writeKey("_routing");
			generator.write(this.routing);

		}
		generator.writeKey("_score");
		generator.write(this.score);

		generator.writeKey("_source");
		JsonpUtils.serialize(this.source, generator, tDocumentSerializer, mapper);

		generator.writeKey("text");
		generator.write(this.text);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CompletionSuggestOption}.
	 */

	public static class Builder<TDocument> extends ObjectBuilderBase
			implements
				ObjectBuilder<CompletionSuggestOption<TDocument>> {
		@Nullable
		private Boolean collateMatch;

		@Nullable
		private Map<String, List<Context>> contexts;

		@Nullable
		private Map<String, JsonData> fields;

		private String id;

		private String index;

		@Nullable
		private String routing;

		private Double score;

		private TDocument source;

		private String text;

		@Nullable
		private JsonpSerializer<TDocument> tDocumentSerializer;

		/**
		 * API name: {@code collate_match}
		 */
		public final Builder<TDocument> collateMatch(@Nullable Boolean value) {
			this.collateMatch = value;
			return this;
		}

		/**
		 * API name: {@code contexts}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>contexts</code>.
		 */
		public final Builder<TDocument> contexts(Map<String, List<Context>> map) {
			this.contexts = _mapPutAll(this.contexts, map);
			return this;
		}

		/**
		 * API name: {@code contexts}
		 * <p>
		 * Adds an entry to <code>contexts</code>.
		 */
		public final Builder<TDocument> contexts(String key, List<Context> value) {
			this.contexts = _mapPut(this.contexts, key, value);
			return this;
		}

		/**
		 * API name: {@code fields}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>fields</code>.
		 */
		public final Builder<TDocument> fields(Map<String, JsonData> map) {
			this.fields = _mapPutAll(this.fields, map);
			return this;
		}

		/**
		 * API name: {@code fields}
		 * <p>
		 * Adds an entry to <code>fields</code>.
		 */
		public final Builder<TDocument> fields(String key, JsonData value) {
			this.fields = _mapPut(this.fields, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code _id}
		 */
		public final Builder<TDocument> id(String value) {
			this.id = value;
			return this;
		}

		/**
		 * Required - API name: {@code _index}
		 */
		public final Builder<TDocument> index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code _routing}
		 */
		public final Builder<TDocument> routing(@Nullable String value) {
			this.routing = value;
			return this;
		}

		/**
		 * Required - API name: {@code _score}
		 */
		public final Builder<TDocument> score(double value) {
			this.score = value;
			return this;
		}

		/**
		 * Required - API name: {@code _source}
		 */
		public final Builder<TDocument> source(TDocument value) {
			this.source = value;
			return this;
		}

		/**
		 * Required - API name: {@code text}
		 */
		public final Builder<TDocument> text(String value) {
			this.text = value;
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
		 * Builds a {@link CompletionSuggestOption}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CompletionSuggestOption<TDocument> build() {
			_checkSingleUse();

			return new CompletionSuggestOption<TDocument>(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Create a JSON deserializer for CompletionSuggestOption
	 */
	public static <TDocument> JsonpDeserializer<CompletionSuggestOption<TDocument>> createCompletionSuggestOptionDeserializer(
			JsonpDeserializer<TDocument> tDocumentDeserializer) {
		return ObjectBuilderDeserializer.createForObject((Supplier<Builder<TDocument>>) Builder::new,
				op -> CompletionSuggestOption.setupCompletionSuggestOptionDeserializer(op, tDocumentDeserializer));
	};

	protected static <TDocument> void setupCompletionSuggestOptionDeserializer(
			ObjectDeserializer<CompletionSuggestOption.Builder<TDocument>> op,
			JsonpDeserializer<TDocument> tDocumentDeserializer) {

		op.add(Builder::collateMatch, JsonpDeserializer.booleanDeserializer(), "collate_match");
		op.add(Builder::contexts,
				JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.arrayDeserializer(Context._DESERIALIZER)),
				"contexts");
		op.add(Builder::fields, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "fields");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "_id");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "_index");
		op.add(Builder::routing, JsonpDeserializer.stringDeserializer(), "_routing");
		op.add(Builder::score, JsonpDeserializer.doubleDeserializer(), "_score");
		op.add(Builder::source, tDocumentDeserializer, "_source");
		op.add(Builder::text, JsonpDeserializer.stringDeserializer(), "text");

	}

}
