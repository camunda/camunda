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
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.analysis.EdgeNGramTokenFilter


@JsonpDeserializable
public class EdgeNGramTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	private final int maxGram;

	private final int minGram;

	@Nullable
	private final EdgeNGramSide side;

	@Nullable
	private final Boolean preserveOriginal;

	// ---------------------------------------------------------------------------------------------

	private EdgeNGramTokenFilter(Builder builder) {
		super(builder);

		this.maxGram = ApiTypeHelper.requireNonNull(builder.maxGram, this, "maxGram");
		this.minGram = ApiTypeHelper.requireNonNull(builder.minGram, this, "minGram");
		this.side = builder.side;
		this.preserveOriginal = builder.preserveOriginal;

	}

	public static EdgeNGramTokenFilter of(Function<Builder, ObjectBuilder<EdgeNGramTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.EdgeNgram;
	}

	/**
	 * Required - API name: {@code max_gram}
	 */
	public final int maxGram() {
		return this.maxGram;
	}

	/**
	 * Required - API name: {@code min_gram}
	 */
	public final int minGram() {
		return this.minGram;
	}

	/**
	 * API name: {@code side}
	 */
	@Nullable
	public final EdgeNGramSide side() {
		return this.side;
	}

	/**
	 * API name: {@code preserve_original}
	 */
	@Nullable
	public final Boolean preserveOriginal() {
		return this.preserveOriginal;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "edge_ngram");
		super.serializeInternal(generator, mapper);
		generator.writeKey("max_gram");
		generator.write(this.maxGram);

		generator.writeKey("min_gram");
		generator.write(this.minGram);

		if (this.side != null) {
			generator.writeKey("side");
			this.side.serialize(generator, mapper);
		}
		if (this.preserveOriginal != null) {
			generator.writeKey("preserve_original");
			generator.write(this.preserveOriginal);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link EdgeNGramTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<EdgeNGramTokenFilter> {
		private Integer maxGram;

		private Integer minGram;

		@Nullable
		private EdgeNGramSide side;

		@Nullable
		private Boolean preserveOriginal;

		/**
		 * Required - API name: {@code max_gram}
		 */
		public final Builder maxGram(int value) {
			this.maxGram = value;
			return this;
		}

		/**
		 * Required - API name: {@code min_gram}
		 */
		public final Builder minGram(int value) {
			this.minGram = value;
			return this;
		}

		/**
		 * API name: {@code side}
		 */
		public final Builder side(@Nullable EdgeNGramSide value) {
			this.side = value;
			return this;
		}

		/**
		 * API name: {@code preserve_original}
		 */
		public final Builder preserveOriginal(@Nullable Boolean value) {
			this.preserveOriginal = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link EdgeNGramTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public EdgeNGramTokenFilter build() {
			_checkSingleUse();

			return new EdgeNGramTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link EdgeNGramTokenFilter}
	 */
	public static final JsonpDeserializer<EdgeNGramTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, EdgeNGramTokenFilter::setupEdgeNGramTokenFilterDeserializer);

	protected static void setupEdgeNGramTokenFilterDeserializer(ObjectDeserializer<EdgeNGramTokenFilter.Builder> op) {
		setupTokenFilterBaseDeserializer(op);
		op.add(Builder::maxGram, JsonpDeserializer.integerDeserializer(), "max_gram");
		op.add(Builder::minGram, JsonpDeserializer.integerDeserializer(), "min_gram");
		op.add(Builder::side, EdgeNGramSide._DESERIALIZER, "side");
		op.add(Builder::preserveOriginal, JsonpDeserializer.booleanDeserializer(), "preserve_original");

		op.ignore("type");
	}

}
