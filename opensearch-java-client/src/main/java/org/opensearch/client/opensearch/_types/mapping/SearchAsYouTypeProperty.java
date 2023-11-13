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
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.mapping.SearchAsYouTypeProperty


@JsonpDeserializable
public class SearchAsYouTypeProperty extends CorePropertyBase implements PropertyVariant {
	@Nullable
	private final String analyzer;

	@Nullable
	private final Boolean index;

	@Nullable
	private final IndexOptions indexOptions;

	@Nullable
	private final Integer maxShingleSize;

	@Nullable
	private final Boolean norms;

	@Nullable
	private final String searchAnalyzer;

	@Nullable
	private final String searchQuoteAnalyzer;

	@Nullable
	private final TermVectorOption termVector;

	// ---------------------------------------------------------------------------------------------

	private SearchAsYouTypeProperty(Builder builder) {
		super(builder);

		this.analyzer = builder.analyzer;
		this.index = builder.index;
		this.indexOptions = builder.indexOptions;
		this.maxShingleSize = builder.maxShingleSize;
		this.norms = builder.norms;
		this.searchAnalyzer = builder.searchAnalyzer;
		this.searchQuoteAnalyzer = builder.searchQuoteAnalyzer;
		this.termVector = builder.termVector;

	}

	public static SearchAsYouTypeProperty of(Function<Builder, ObjectBuilder<SearchAsYouTypeProperty>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Property variant kind.
	 */
	@Override
	public Property.Kind _propertyKind() {
		return Property.Kind.SearchAsYouType;
	}

	/**
	 * API name: {@code analyzer}
	 */
	@Nullable
	public final String analyzer() {
		return this.analyzer;
	}

	/**
	 * API name: {@code index}
	 */
	@Nullable
	public final Boolean index() {
		return this.index;
	}

	/**
	 * API name: {@code index_options}
	 */
	@Nullable
	public final IndexOptions indexOptions() {
		return this.indexOptions;
	}

	/**
	 * API name: {@code max_shingle_size}
	 */
	@Nullable
	public final Integer maxShingleSize() {
		return this.maxShingleSize;
	}

	/**
	 * API name: {@code norms}
	 */
	@Nullable
	public final Boolean norms() {
		return this.norms;
	}

	/**
	 * API name: {@code search_analyzer}
	 */
	@Nullable
	public final String searchAnalyzer() {
		return this.searchAnalyzer;
	}

	/**
	 * API name: {@code search_quote_analyzer}
	 */
	@Nullable
	public final String searchQuoteAnalyzer() {
		return this.searchQuoteAnalyzer;
	}

	/**
	 * API name: {@code term_vector}
	 */
	@Nullable
	public final TermVectorOption termVector() {
		return this.termVector;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "search_as_you_type");
		super.serializeInternal(generator, mapper);
		if (this.analyzer != null) {
			generator.writeKey("analyzer");
			generator.write(this.analyzer);

		}
		if (this.index != null) {
			generator.writeKey("index");
			generator.write(this.index);

		}
		if (this.indexOptions != null) {
			generator.writeKey("index_options");
			this.indexOptions.serialize(generator, mapper);
		}
		if (this.maxShingleSize != null) {
			generator.writeKey("max_shingle_size");
			generator.write(this.maxShingleSize);

		}
		if (this.norms != null) {
			generator.writeKey("norms");
			generator.write(this.norms);

		}
		if (this.searchAnalyzer != null) {
			generator.writeKey("search_analyzer");
			generator.write(this.searchAnalyzer);

		}
		if (this.searchQuoteAnalyzer != null) {
			generator.writeKey("search_quote_analyzer");
			generator.write(this.searchQuoteAnalyzer);

		}
		if (this.termVector != null) {
			generator.writeKey("term_vector");
			this.termVector.serialize(generator, mapper);
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SearchAsYouTypeProperty}.
	 */

	public static class Builder extends CorePropertyBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<SearchAsYouTypeProperty> {
		@Nullable
		private String analyzer;

		@Nullable
		private Boolean index;

		@Nullable
		private IndexOptions indexOptions;

		@Nullable
		private Integer maxShingleSize;

		@Nullable
		private Boolean norms;

		@Nullable
		private String searchAnalyzer;

		@Nullable
		private String searchQuoteAnalyzer;

		@Nullable
		private TermVectorOption termVector;

		/**
		 * API name: {@code analyzer}
		 */
		public final Builder analyzer(@Nullable String value) {
			this.analyzer = value;
			return this;
		}

		/**
		 * API name: {@code index}
		 */
		public final Builder index(@Nullable Boolean value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code index_options}
		 */
		public final Builder indexOptions(@Nullable IndexOptions value) {
			this.indexOptions = value;
			return this;
		}

		/**
		 * API name: {@code max_shingle_size}
		 */
		public final Builder maxShingleSize(@Nullable Integer value) {
			this.maxShingleSize = value;
			return this;
		}

		/**
		 * API name: {@code norms}
		 */
		public final Builder norms(@Nullable Boolean value) {
			this.norms = value;
			return this;
		}

		/**
		 * API name: {@code search_analyzer}
		 */
		public final Builder searchAnalyzer(@Nullable String value) {
			this.searchAnalyzer = value;
			return this;
		}

		/**
		 * API name: {@code search_quote_analyzer}
		 */
		public final Builder searchQuoteAnalyzer(@Nullable String value) {
			this.searchQuoteAnalyzer = value;
			return this;
		}

		/**
		 * API name: {@code term_vector}
		 */
		public final Builder termVector(@Nullable TermVectorOption value) {
			this.termVector = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link SearchAsYouTypeProperty}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public SearchAsYouTypeProperty build() {
			_checkSingleUse();

			return new SearchAsYouTypeProperty(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link SearchAsYouTypeProperty}
	 */
	public static final JsonpDeserializer<SearchAsYouTypeProperty> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, SearchAsYouTypeProperty::setupSearchAsYouTypePropertyDeserializer);

	protected static void setupSearchAsYouTypePropertyDeserializer(
			ObjectDeserializer<SearchAsYouTypeProperty.Builder> op) {
		setupCorePropertyBaseDeserializer(op);
		op.add(Builder::analyzer, JsonpDeserializer.stringDeserializer(), "analyzer");
		op.add(Builder::index, JsonpDeserializer.booleanDeserializer(), "index");
		op.add(Builder::indexOptions, IndexOptions._DESERIALIZER, "index_options");
		op.add(Builder::maxShingleSize, JsonpDeserializer.integerDeserializer(), "max_shingle_size");
		op.add(Builder::norms, JsonpDeserializer.booleanDeserializer(), "norms");
		op.add(Builder::searchAnalyzer, JsonpDeserializer.stringDeserializer(), "search_analyzer");
		op.add(Builder::searchQuoteAnalyzer, JsonpDeserializer.stringDeserializer(), "search_quote_analyzer");
		op.add(Builder::termVector, TermVectorOption._DESERIALIZER, "term_vector");

		op.ignore("type");
	}

}
