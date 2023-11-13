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

package org.opensearch.client.opensearch.core.rank_eval;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.rank_eval.RankEvalRequestItem


@JsonpDeserializable
public class RankEvalRequestItem implements JsonpSerializable {
	private final String id;

	@Nullable
	private final RankEvalQuery request;

	private final List<DocumentRating> ratings;

	@Nullable
	private final String templateId;

	private final Map<String, JsonData> params;

	// ---------------------------------------------------------------------------------------------

	private RankEvalRequestItem(Builder builder) {

		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.request = builder.request;
		this.ratings = ApiTypeHelper.unmodifiableRequired(builder.ratings, this, "ratings");
		this.templateId = builder.templateId;
		this.params = ApiTypeHelper.unmodifiable(builder.params);

	}

	public static RankEvalRequestItem of(Function<Builder, ObjectBuilder<RankEvalRequestItem>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - The search request's ID, used to group result details later.
	 * <p>
	 * API name: {@code id}
	 */
	public final String id() {
		return this.id;
	}

	/**
	 * The query being evaluated.
	 * <p>
	 * API name: {@code request}
	 */
	@Nullable
	public final RankEvalQuery request() {
		return this.request;
	}

	/**
	 * Required - List of document ratings
	 * <p>
	 * API name: {@code ratings}
	 */
	public final List<DocumentRating> ratings() {
		return this.ratings;
	}

	/**
	 * The search template Id
	 * <p>
	 * API name: {@code template_id}
	 */
	@Nullable
	public final String templateId() {
		return this.templateId;
	}

	/**
	 * The search template parameters.
	 * <p>
	 * API name: {@code params}
	 */
	public final Map<String, JsonData> params() {
		return this.params;
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

		generator.writeKey("id");
		generator.write(this.id);

		if (this.request != null) {
			generator.writeKey("request");
			this.request.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.ratings)) {
			generator.writeKey("ratings");
			generator.writeStartArray();
			for (DocumentRating item0 : this.ratings) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.templateId != null) {
			generator.writeKey("template_id");
			generator.write(this.templateId);

		}
		if (ApiTypeHelper.isDefined(this.params)) {
			generator.writeKey("params");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.params.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RankEvalRequestItem}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RankEvalRequestItem> {
		private String id;

		@Nullable
		private RankEvalQuery request;

		private List<DocumentRating> ratings;

		@Nullable
		private String templateId;

		@Nullable
		private Map<String, JsonData> params;

		/**
		 * Required - The search request's ID, used to group result details later.
		 * <p>
		 * API name: {@code id}
		 */
		public final Builder id(String value) {
			this.id = value;
			return this;
		}

		/**
		 * The query being evaluated.
		 * <p>
		 * API name: {@code request}
		 */
		public final Builder request(@Nullable RankEvalQuery value) {
			this.request = value;
			return this;
		}

		/**
		 * The query being evaluated.
		 * <p>
		 * API name: {@code request}
		 */
		public final Builder request(Function<RankEvalQuery.Builder, ObjectBuilder<RankEvalQuery>> fn) {
			return this.request(fn.apply(new RankEvalQuery.Builder()).build());
		}

		/**
		 * Required - List of document ratings
		 * <p>
		 * API name: {@code ratings}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>ratings</code>.
		 */
		public final Builder ratings(List<DocumentRating> list) {
			this.ratings = _listAddAll(this.ratings, list);
			return this;
		}

		/**
		 * Required - List of document ratings
		 * <p>
		 * API name: {@code ratings}
		 * <p>
		 * Adds one or more values to <code>ratings</code>.
		 */
		public final Builder ratings(DocumentRating value, DocumentRating... values) {
			this.ratings = _listAdd(this.ratings, value, values);
			return this;
		}

		/**
		 * Required - List of document ratings
		 * <p>
		 * API name: {@code ratings}
		 * <p>
		 * Adds a value to <code>ratings</code> using a builder lambda.
		 */
		public final Builder ratings(Function<DocumentRating.Builder, ObjectBuilder<DocumentRating>> fn) {
			return ratings(fn.apply(new DocumentRating.Builder()).build());
		}

		/**
		 * The search template Id
		 * <p>
		 * API name: {@code template_id}
		 */
		public final Builder templateId(@Nullable String value) {
			this.templateId = value;
			return this;
		}

		/**
		 * The search template parameters.
		 * <p>
		 * API name: {@code params}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>params</code>.
		 */
		public final Builder params(Map<String, JsonData> map) {
			this.params = _mapPutAll(this.params, map);
			return this;
		}

		/**
		 * The search template parameters.
		 * <p>
		 * API name: {@code params}
		 * <p>
		 * Adds an entry to <code>params</code>.
		 */
		public final Builder params(String key, JsonData value) {
			this.params = _mapPut(this.params, key, value);
			return this;
		}

		/**
		 * Builds a {@link RankEvalRequestItem}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RankEvalRequestItem build() {
			_checkSingleUse();

			return new RankEvalRequestItem(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RankEvalRequestItem}
	 */
	public static final JsonpDeserializer<RankEvalRequestItem> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, RankEvalRequestItem::setupRankEvalRequestItemDeserializer);

	protected static void setupRankEvalRequestItemDeserializer(ObjectDeserializer<RankEvalRequestItem.Builder> op) {

		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "id");
		op.add(Builder::request, RankEvalQuery._DESERIALIZER, "request");
		op.add(Builder::ratings, JsonpDeserializer.arrayDeserializer(DocumentRating._DESERIALIZER), "ratings");
		op.add(Builder::templateId, JsonpDeserializer.stringDeserializer(), "template_id");
		op.add(Builder::params, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "params");

	}

}
