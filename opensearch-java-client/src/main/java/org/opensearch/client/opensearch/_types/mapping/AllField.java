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

package org.opensearch.client.opensearch._types.mapping;

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
import java.util.function.Function;

// typedef: _types.mapping.AllField


@JsonpDeserializable
public class AllField implements JsonpSerializable {
	private final String analyzer;

	private final boolean enabled;

	private final boolean omitNorms;

	private final String searchAnalyzer;

	private final String similarity;

	private final boolean store;

	private final boolean storeTermVectorOffsets;

	private final boolean storeTermVectorPayloads;

	private final boolean storeTermVectorPositions;

	private final boolean storeTermVectors;

	// ---------------------------------------------------------------------------------------------

	private AllField(Builder builder) {

		this.analyzer = ApiTypeHelper.requireNonNull(builder.analyzer, this, "analyzer");
		this.enabled = ApiTypeHelper.requireNonNull(builder.enabled, this, "enabled");
		this.omitNorms = ApiTypeHelper.requireNonNull(builder.omitNorms, this, "omitNorms");
		this.searchAnalyzer = ApiTypeHelper.requireNonNull(builder.searchAnalyzer, this, "searchAnalyzer");
		this.similarity = ApiTypeHelper.requireNonNull(builder.similarity, this, "similarity");
		this.store = ApiTypeHelper.requireNonNull(builder.store, this, "store");
		this.storeTermVectorOffsets = ApiTypeHelper.requireNonNull(builder.storeTermVectorOffsets, this,
				"storeTermVectorOffsets");
		this.storeTermVectorPayloads = ApiTypeHelper.requireNonNull(builder.storeTermVectorPayloads, this,
				"storeTermVectorPayloads");
		this.storeTermVectorPositions = ApiTypeHelper.requireNonNull(builder.storeTermVectorPositions, this,
				"storeTermVectorPositions");
		this.storeTermVectors = ApiTypeHelper.requireNonNull(builder.storeTermVectors, this, "storeTermVectors");

	}

	public static AllField of(Function<Builder, ObjectBuilder<AllField>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code analyzer}
	 */
	public final String analyzer() {
		return this.analyzer;
	}

	/**
	 * Required - API name: {@code enabled}
	 */
	public final boolean enabled() {
		return this.enabled;
	}

	/**
	 * Required - API name: {@code omit_norms}
	 */
	public final boolean omitNorms() {
		return this.omitNorms;
	}

	/**
	 * Required - API name: {@code search_analyzer}
	 */
	public final String searchAnalyzer() {
		return this.searchAnalyzer;
	}

	/**
	 * Required - API name: {@code similarity}
	 */
	public final String similarity() {
		return this.similarity;
	}

	/**
	 * Required - API name: {@code store}
	 */
	public final boolean store() {
		return this.store;
	}

	/**
	 * Required - API name: {@code store_term_vector_offsets}
	 */
	public final boolean storeTermVectorOffsets() {
		return this.storeTermVectorOffsets;
	}

	/**
	 * Required - API name: {@code store_term_vector_payloads}
	 */
	public final boolean storeTermVectorPayloads() {
		return this.storeTermVectorPayloads;
	}

	/**
	 * Required - API name: {@code store_term_vector_positions}
	 */
	public final boolean storeTermVectorPositions() {
		return this.storeTermVectorPositions;
	}

	/**
	 * Required - API name: {@code store_term_vectors}
	 */
	public final boolean storeTermVectors() {
		return this.storeTermVectors;
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

		generator.writeKey("analyzer");
		generator.write(this.analyzer);

		generator.writeKey("enabled");
		generator.write(this.enabled);

		generator.writeKey("omit_norms");
		generator.write(this.omitNorms);

		generator.writeKey("search_analyzer");
		generator.write(this.searchAnalyzer);

		generator.writeKey("similarity");
		generator.write(this.similarity);

		generator.writeKey("store");
		generator.write(this.store);

		generator.writeKey("store_term_vector_offsets");
		generator.write(this.storeTermVectorOffsets);

		generator.writeKey("store_term_vector_payloads");
		generator.write(this.storeTermVectorPayloads);

		generator.writeKey("store_term_vector_positions");
		generator.write(this.storeTermVectorPositions);

		generator.writeKey("store_term_vectors");
		generator.write(this.storeTermVectors);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link AllField}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<AllField> {
		private String analyzer;

		private Boolean enabled;

		private Boolean omitNorms;

		private String searchAnalyzer;

		private String similarity;

		private Boolean store;

		private Boolean storeTermVectorOffsets;

		private Boolean storeTermVectorPayloads;

		private Boolean storeTermVectorPositions;

		private Boolean storeTermVectors;

		/**
		 * Required - API name: {@code analyzer}
		 */
		public final Builder analyzer(String value) {
			this.analyzer = value;
			return this;
		}

		/**
		 * Required - API name: {@code enabled}
		 */
		public final Builder enabled(boolean value) {
			this.enabled = value;
			return this;
		}

		/**
		 * Required - API name: {@code omit_norms}
		 */
		public final Builder omitNorms(boolean value) {
			this.omitNorms = value;
			return this;
		}

		/**
		 * Required - API name: {@code search_analyzer}
		 */
		public final Builder searchAnalyzer(String value) {
			this.searchAnalyzer = value;
			return this;
		}

		/**
		 * Required - API name: {@code similarity}
		 */
		public final Builder similarity(String value) {
			this.similarity = value;
			return this;
		}

		/**
		 * Required - API name: {@code store}
		 */
		public final Builder store(boolean value) {
			this.store = value;
			return this;
		}

		/**
		 * Required - API name: {@code store_term_vector_offsets}
		 */
		public final Builder storeTermVectorOffsets(boolean value) {
			this.storeTermVectorOffsets = value;
			return this;
		}

		/**
		 * Required - API name: {@code store_term_vector_payloads}
		 */
		public final Builder storeTermVectorPayloads(boolean value) {
			this.storeTermVectorPayloads = value;
			return this;
		}

		/**
		 * Required - API name: {@code store_term_vector_positions}
		 */
		public final Builder storeTermVectorPositions(boolean value) {
			this.storeTermVectorPositions = value;
			return this;
		}

		/**
		 * Required - API name: {@code store_term_vectors}
		 */
		public final Builder storeTermVectors(boolean value) {
			this.storeTermVectors = value;
			return this;
		}

		/**
		 * Builds a {@link AllField}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public AllField build() {
			_checkSingleUse();

			return new AllField(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link AllField}
	 */
	public static final JsonpDeserializer<AllField> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			AllField::setupAllFieldDeserializer);

	protected static void setupAllFieldDeserializer(ObjectDeserializer<AllField.Builder> op) {

		op.add(Builder::analyzer, JsonpDeserializer.stringDeserializer(), "analyzer");
		op.add(Builder::enabled, JsonpDeserializer.booleanDeserializer(), "enabled");
		op.add(Builder::omitNorms, JsonpDeserializer.booleanDeserializer(), "omit_norms");
		op.add(Builder::searchAnalyzer, JsonpDeserializer.stringDeserializer(), "search_analyzer");
		op.add(Builder::similarity, JsonpDeserializer.stringDeserializer(), "similarity");
		op.add(Builder::store, JsonpDeserializer.booleanDeserializer(), "store");
		op.add(Builder::storeTermVectorOffsets, JsonpDeserializer.booleanDeserializer(), "store_term_vector_offsets");
		op.add(Builder::storeTermVectorPayloads, JsonpDeserializer.booleanDeserializer(), "store_term_vector_payloads");
		op.add(Builder::storeTermVectorPositions, JsonpDeserializer.booleanDeserializer(),
				"store_term_vector_positions");
		op.add(Builder::storeTermVectors, JsonpDeserializer.booleanDeserializer(), "store_term_vectors");

	}

}
