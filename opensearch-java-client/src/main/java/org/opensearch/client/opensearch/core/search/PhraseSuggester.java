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

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.search._types.PhraseSuggester


@JsonpDeserializable
public class PhraseSuggester extends SuggesterBase implements FieldSuggesterVariant {
	@Nullable
	private final PhraseSuggestCollate collate;

	@Nullable
	private final Double confidence;

	private final List<DirectGenerator> directGenerator;

	@Nullable
	private final Boolean forceUnigrams;

	@Nullable
	private final Integer gramSize;

	@Nullable
	private final PhraseSuggestHighlight highlight;

	@Nullable
	private final Double maxErrors;

	@Nullable
	private final Double realWordErrorLikelihood;

	@Nullable
	private final String separator;

	@Nullable
	private final Integer shardSize;

	@Nullable
	private final SmoothingModel smoothing;

	@Nullable
	private final String text;

	@Nullable
	private final Integer tokenLimit;

	// ---------------------------------------------------------------------------------------------

	private PhraseSuggester(Builder builder) {
		super(builder);

		this.collate = builder.collate;
		this.confidence = builder.confidence;
		this.directGenerator = ApiTypeHelper.unmodifiable(builder.directGenerator);
		this.forceUnigrams = builder.forceUnigrams;
		this.gramSize = builder.gramSize;
		this.highlight = builder.highlight;
		this.maxErrors = builder.maxErrors;
		this.realWordErrorLikelihood = builder.realWordErrorLikelihood;
		this.separator = builder.separator;
		this.shardSize = builder.shardSize;
		this.smoothing = builder.smoothing;
		this.text = builder.text;
		this.tokenLimit = builder.tokenLimit;

	}

	public static PhraseSuggester of(Function<Builder, ObjectBuilder<PhraseSuggester>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * FieldSuggester variant kind.
	 */
	@Override
	public FieldSuggester.Kind _fieldSuggesterKind() {
		return FieldSuggester.Kind.Phrase;
	}

	/**
	 * API name: {@code collate}
	 */
	@Nullable
	public final PhraseSuggestCollate collate() {
		return this.collate;
	}

	/**
	 * API name: {@code confidence}
	 */
	@Nullable
	public final Double confidence() {
		return this.confidence;
	}

	/**
	 * API name: {@code direct_generator}
	 */
	public final List<DirectGenerator> directGenerator() {
		return this.directGenerator;
	}

	/**
	 * API name: {@code force_unigrams}
	 */
	@Nullable
	public final Boolean forceUnigrams() {
		return this.forceUnigrams;
	}

	/**
	 * API name: {@code gram_size}
	 */
	@Nullable
	public final Integer gramSize() {
		return this.gramSize;
	}

	/**
	 * API name: {@code highlight}
	 */
	@Nullable
	public final PhraseSuggestHighlight highlight() {
		return this.highlight;
	}

	/**
	 * API name: {@code max_errors}
	 */
	@Nullable
	public final Double maxErrors() {
		return this.maxErrors;
	}

	/**
	 * API name: {@code real_word_error_likelihood}
	 */
	@Nullable
	public final Double realWordErrorLikelihood() {
		return this.realWordErrorLikelihood;
	}

	/**
	 * API name: {@code separator}
	 */
	@Nullable
	public final String separator() {
		return this.separator;
	}

	/**
	 * API name: {@code shard_size}
	 */
	@Nullable
	public final Integer shardSize() {
		return this.shardSize;
	}

	/**
	 * API name: {@code smoothing}
	 */
	@Nullable
	public final SmoothingModel smoothing() {
		return this.smoothing;
	}

	/**
	 * API name: {@code text}
	 */
	@Nullable
	public final String text() {
		return this.text;
	}

	/**
	 * API name: {@code token_limit}
	 */
	@Nullable
	public final Integer tokenLimit() {
		return this.tokenLimit;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.collate != null) {
			generator.writeKey("collate");
			this.collate.serialize(generator, mapper);

		}
		if (this.confidence != null) {
			generator.writeKey("confidence");
			generator.write(this.confidence);

		}
		if (ApiTypeHelper.isDefined(this.directGenerator)) {
			generator.writeKey("direct_generator");
			generator.writeStartArray();
			for (DirectGenerator item0 : this.directGenerator) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.forceUnigrams != null) {
			generator.writeKey("force_unigrams");
			generator.write(this.forceUnigrams);

		}
		if (this.gramSize != null) {
			generator.writeKey("gram_size");
			generator.write(this.gramSize);

		}
		if (this.highlight != null) {
			generator.writeKey("highlight");
			this.highlight.serialize(generator, mapper);

		}
		if (this.maxErrors != null) {
			generator.writeKey("max_errors");
			generator.write(this.maxErrors);

		}
		if (this.realWordErrorLikelihood != null) {
			generator.writeKey("real_word_error_likelihood");
			generator.write(this.realWordErrorLikelihood);

		}
		if (this.separator != null) {
			generator.writeKey("separator");
			generator.write(this.separator);

		}
		if (this.shardSize != null) {
			generator.writeKey("shard_size");
			generator.write(this.shardSize);

		}
		if (this.smoothing != null) {
			generator.writeKey("smoothing");
			this.smoothing.serialize(generator, mapper);

		}
		if (this.text != null) {
			generator.writeKey("text");
			generator.write(this.text);

		}
		if (this.tokenLimit != null) {
			generator.writeKey("token_limit");
			generator.write(this.tokenLimit);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link PhraseSuggester}.
	 */

	public static class Builder extends SuggesterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<PhraseSuggester> {
		@Nullable
		private PhraseSuggestCollate collate;

		@Nullable
		private Double confidence;

		@Nullable
		private List<DirectGenerator> directGenerator;

		@Nullable
		private Boolean forceUnigrams;

		@Nullable
		private Integer gramSize;

		@Nullable
		private PhraseSuggestHighlight highlight;

		@Nullable
		private Double maxErrors;

		@Nullable
		private Double realWordErrorLikelihood;

		@Nullable
		private String separator;

		@Nullable
		private Integer shardSize;

		@Nullable
		private SmoothingModel smoothing;

		@Nullable
		private String text;

		@Nullable
		private Integer tokenLimit;

		/**
		 * API name: {@code collate}
		 */
		public final Builder collate(@Nullable PhraseSuggestCollate value) {
			this.collate = value;
			return this;
		}

		/**
		 * API name: {@code collate}
		 */
		public final Builder collate(Function<PhraseSuggestCollate.Builder, ObjectBuilder<PhraseSuggestCollate>> fn) {
			return this.collate(fn.apply(new PhraseSuggestCollate.Builder()).build());
		}

		/**
		 * API name: {@code confidence}
		 */
		public final Builder confidence(@Nullable Double value) {
			this.confidence = value;
			return this;
		}

		/**
		 * API name: {@code direct_generator}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>directGenerator</code>.
		 */
		public final Builder directGenerator(List<DirectGenerator> list) {
			this.directGenerator = _listAddAll(this.directGenerator, list);
			return this;
		}

		/**
		 * API name: {@code direct_generator}
		 * <p>
		 * Adds one or more values to <code>directGenerator</code>.
		 */
		public final Builder directGenerator(DirectGenerator value, DirectGenerator... values) {
			this.directGenerator = _listAdd(this.directGenerator, value, values);
			return this;
		}

		/**
		 * API name: {@code direct_generator}
		 * <p>
		 * Adds a value to <code>directGenerator</code> using a builder lambda.
		 */
		public final Builder directGenerator(Function<DirectGenerator.Builder, ObjectBuilder<DirectGenerator>> fn) {
			return directGenerator(fn.apply(new DirectGenerator.Builder()).build());
		}

		/**
		 * API name: {@code force_unigrams}
		 */
		public final Builder forceUnigrams(@Nullable Boolean value) {
			this.forceUnigrams = value;
			return this;
		}

		/**
		 * API name: {@code gram_size}
		 */
		public final Builder gramSize(@Nullable Integer value) {
			this.gramSize = value;
			return this;
		}

		/**
		 * API name: {@code highlight}
		 */
		public final Builder highlight(@Nullable PhraseSuggestHighlight value) {
			this.highlight = value;
			return this;
		}

		/**
		 * API name: {@code highlight}
		 */
		public final Builder highlight(
				Function<PhraseSuggestHighlight.Builder, ObjectBuilder<PhraseSuggestHighlight>> fn) {
			return this.highlight(fn.apply(new PhraseSuggestHighlight.Builder()).build());
		}

		/**
		 * API name: {@code max_errors}
		 */
		public final Builder maxErrors(@Nullable Double value) {
			this.maxErrors = value;
			return this;
		}

		/**
		 * API name: {@code real_word_error_likelihood}
		 */
		public final Builder realWordErrorLikelihood(@Nullable Double value) {
			this.realWordErrorLikelihood = value;
			return this;
		}

		/**
		 * API name: {@code separator}
		 */
		public final Builder separator(@Nullable String value) {
			this.separator = value;
			return this;
		}

		/**
		 * API name: {@code shard_size}
		 */
		public final Builder shardSize(@Nullable Integer value) {
			this.shardSize = value;
			return this;
		}

		/**
		 * API name: {@code smoothing}
		 */
		public final Builder smoothing(@Nullable SmoothingModel value) {
			this.smoothing = value;
			return this;
		}

		/**
		 * API name: {@code smoothing}
		 */
		public final Builder smoothing(Function<SmoothingModel.Builder, ObjectBuilder<SmoothingModel>> fn) {
			return this.smoothing(fn.apply(new SmoothingModel.Builder()).build());
		}

		/**
		 * API name: {@code text}
		 */
		public final Builder text(@Nullable String value) {
			this.text = value;
			return this;
		}

		/**
		 * API name: {@code token_limit}
		 */
		public final Builder tokenLimit(@Nullable Integer value) {
			this.tokenLimit = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link PhraseSuggester}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public PhraseSuggester build() {
			_checkSingleUse();

			return new PhraseSuggester(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link PhraseSuggester}
	 */
	public static final JsonpDeserializer<PhraseSuggester> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			PhraseSuggester::setupPhraseSuggesterDeserializer);

	protected static void setupPhraseSuggesterDeserializer(ObjectDeserializer<PhraseSuggester.Builder> op) {
		SuggesterBase.setupSuggesterBaseDeserializer(op);
		op.add(Builder::collate, PhraseSuggestCollate._DESERIALIZER, "collate");
		op.add(Builder::confidence, JsonpDeserializer.doubleDeserializer(), "confidence");
		op.add(Builder::directGenerator, JsonpDeserializer.arrayDeserializer(DirectGenerator._DESERIALIZER),
				"direct_generator");
		op.add(Builder::forceUnigrams, JsonpDeserializer.booleanDeserializer(), "force_unigrams");
		op.add(Builder::gramSize, JsonpDeserializer.integerDeserializer(), "gram_size");
		op.add(Builder::highlight, PhraseSuggestHighlight._DESERIALIZER, "highlight");
		op.add(Builder::maxErrors, JsonpDeserializer.doubleDeserializer(), "max_errors");
		op.add(Builder::realWordErrorLikelihood, JsonpDeserializer.doubleDeserializer(), "real_word_error_likelihood");
		op.add(Builder::separator, JsonpDeserializer.stringDeserializer(), "separator");
		op.add(Builder::shardSize, JsonpDeserializer.integerDeserializer(), "shard_size");
		op.add(Builder::smoothing, SmoothingModel._DESERIALIZER, "smoothing");
		op.add(Builder::text, JsonpDeserializer.stringDeserializer(), "text");
		op.add(Builder::tokenLimit, JsonpDeserializer.integerDeserializer(), "token_limit");

	}

}
