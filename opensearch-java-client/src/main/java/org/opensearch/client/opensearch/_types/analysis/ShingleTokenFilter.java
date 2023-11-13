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

package org.opensearch.client.opensearch._types.analysis;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.analysis.ShingleTokenFilter

@JsonpDeserializable
public class ShingleTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	@Nullable
	private final String fillerToken;

	@Nullable
	private final String maxShingleSize;

	@Nullable
	private final String minShingleSize;

	@Nullable
	private final Boolean outputUnigrams;

	@Nullable
	private final Boolean outputUnigramsIfNoShingles;

	@Nullable
	private final String tokenSeparator;

	// ---------------------------------------------------------------------------------------------

	private ShingleTokenFilter(Builder builder) {
		super(builder);

		this.fillerToken = builder.fillerToken;
		this.maxShingleSize = builder.maxShingleSize;
		this.minShingleSize = builder.minShingleSize;
		this.outputUnigrams = builder.outputUnigrams;
		this.outputUnigramsIfNoShingles = builder.outputUnigramsIfNoShingles;
		this.tokenSeparator = builder.tokenSeparator;

	}

	public static ShingleTokenFilter of(Function<Builder, ObjectBuilder<ShingleTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.Shingle;
	}

	/**
	 * API name: {@code filler_token}
	 */
	@Nullable
	public final String fillerToken() {
		return this.fillerToken;
	}

	/**
	 * API name: {@code max_shingle_size}
	 */
	@Nullable
	public final String maxShingleSize() {
		return this.maxShingleSize;
	}

	/**
	 * API name: {@code min_shingle_size}
	 */
	@Nullable
	public final String minShingleSize() {
		return this.minShingleSize;
	}

	/**
	 * API name: {@code output_unigrams}
	 */
	@Nullable
	public final Boolean outputUnigrams() {
		return this.outputUnigrams;
	}

	/**
	 * API name: {@code output_unigrams_if_no_shingles}
	 */
	@Nullable
	public final Boolean outputUnigramsIfNoShingles() {
		return this.outputUnigramsIfNoShingles;
	}

	/**
	 * API name: {@code token_separator}
	 */
	@Nullable
	public final String tokenSeparator() {
		return this.tokenSeparator;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "shingle");
		super.serializeInternal(generator, mapper);
		if (this.fillerToken != null) {
			generator.writeKey("filler_token");
			generator.write(this.fillerToken);

		}
		if (this.maxShingleSize != null) {
			generator.writeKey("max_shingle_size");
			generator.write(this.maxShingleSize);

		}
		if (this.minShingleSize != null) {
			generator.writeKey("min_shingle_size");
			generator.write(this.minShingleSize);

		}
		if (this.outputUnigrams != null) {
			generator.writeKey("output_unigrams");
			generator.write(this.outputUnigrams);

		}
		if (this.outputUnigramsIfNoShingles != null) {
			generator.writeKey("output_unigrams_if_no_shingles");
			generator.write(this.outputUnigramsIfNoShingles);

		}
		if (this.tokenSeparator != null) {
			generator.writeKey("token_separator");
			generator.write(this.tokenSeparator);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ShingleTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<ShingleTokenFilter> {
		@Nullable
		private String fillerToken;

		@Nullable
		private String maxShingleSize;

		@Nullable
		private String minShingleSize;

		@Nullable
		private Boolean outputUnigrams;

		@Nullable
		private Boolean outputUnigramsIfNoShingles;

		@Nullable
		private String tokenSeparator;

		/**
		 * API name: {@code filler_token}
		 */
		public final Builder fillerToken(@Nullable String value) {
			this.fillerToken = value;
			return this;
		}

		/**
		 * API name: {@code max_shingle_size}
		 */
		public final Builder maxShingleSize(@Nullable String value) {
			this.maxShingleSize = value;
			return this;
		}

		/**
		 * API name: {@code min_shingle_size}
		 */
		public final Builder minShingleSize(@Nullable String value) {
			this.minShingleSize = value;
			return this;
		}

		/**
		 * API name: {@code output_unigrams}
		 */
		public final Builder outputUnigrams(@Nullable Boolean value) {
			this.outputUnigrams = value;
			return this;
		}

		/**
		 * API name: {@code output_unigrams_if_no_shingles}
		 */
		public final Builder outputUnigramsIfNoShingles(@Nullable Boolean value) {
			this.outputUnigramsIfNoShingles = value;
			return this;
		}

		/**
		 * API name: {@code token_separator}
		 */
		public final Builder tokenSeparator(@Nullable String value) {
			this.tokenSeparator = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link ShingleTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ShingleTokenFilter build() {
			_checkSingleUse();

			return new ShingleTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ShingleTokenFilter}
	 */
	public static final JsonpDeserializer<ShingleTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ShingleTokenFilter::setupShingleTokenFilterDeserializer);

	protected static void setupShingleTokenFilterDeserializer(ObjectDeserializer<ShingleTokenFilter.Builder> op) {
		setupTokenFilterBaseDeserializer(op);
		op.add(Builder::fillerToken, JsonpDeserializer.stringDeserializer(), "filler_token");
		op.add(Builder::maxShingleSize, JsonpDeserializer.stringDeserializer(), "max_shingle_size");
		op.add(Builder::minShingleSize, JsonpDeserializer.stringDeserializer(), "min_shingle_size");
		op.add(Builder::outputUnigrams, JsonpDeserializer.booleanDeserializer(), "output_unigrams");
		op.add(Builder::outputUnigramsIfNoShingles, JsonpDeserializer.booleanDeserializer(),
				"output_unigrams_if_no_shingles");
		op.add(Builder::tokenSeparator, JsonpDeserializer.stringDeserializer(), "token_separator");

		op.ignore("type");
	}

}
