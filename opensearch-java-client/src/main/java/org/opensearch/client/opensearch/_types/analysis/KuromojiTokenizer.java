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
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.analysis.KuromojiTokenizer


@JsonpDeserializable
public class KuromojiTokenizer extends TokenizerBase implements TokenizerDefinitionVariant {
	@Nullable
	private final Boolean discardPunctuation;

	private final KuromojiTokenizationMode mode;

	@Nullable
	private final Integer nbestCost;

	@Nullable
	private final String nbestExamples;

	@Nullable
	private final String userDictionary;

	private final List<String> userDictionaryRules;

	@Nullable
	private final Boolean discardCompoundToken;

	// ---------------------------------------------------------------------------------------------

	private KuromojiTokenizer(Builder builder) {
		super(builder);

		this.discardPunctuation = builder.discardPunctuation;
		this.mode = ApiTypeHelper.requireNonNull(builder.mode, this, "mode");
		this.nbestCost = builder.nbestCost;
		this.nbestExamples = builder.nbestExamples;
		this.userDictionary = builder.userDictionary;
		this.userDictionaryRules = ApiTypeHelper.unmodifiable(builder.userDictionaryRules);
		this.discardCompoundToken = builder.discardCompoundToken;

	}

	public static KuromojiTokenizer of(Function<Builder, ObjectBuilder<KuromojiTokenizer>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenizerDefinition variant kind.
	 */
	@Override
	public TokenizerDefinition.Kind _tokenizerDefinitionKind() {
		return TokenizerDefinition.Kind.KuromojiTokenizer;
	}

	/**
	 * API name: {@code discard_punctuation}
	 */
	@Nullable
	public final Boolean discardPunctuation() {
		return this.discardPunctuation;
	}

	/**
	 * Required - API name: {@code mode}
	 */
	public final KuromojiTokenizationMode mode() {
		return this.mode;
	}

	/**
	 * API name: {@code nbest_cost}
	 */
	@Nullable
	public final Integer nbestCost() {
		return this.nbestCost;
	}

	/**
	 * API name: {@code nbest_examples}
	 */
	@Nullable
	public final String nbestExamples() {
		return this.nbestExamples;
	}

	/**
	 * API name: {@code user_dictionary}
	 */
	@Nullable
	public final String userDictionary() {
		return this.userDictionary;
	}

	/**
	 * API name: {@code user_dictionary_rules}
	 */
	public final List<String> userDictionaryRules() {
		return this.userDictionaryRules;
	}

	/**
	 * API name: {@code discard_compound_token}
	 */
	@Nullable
	public final Boolean discardCompoundToken() {
		return this.discardCompoundToken;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "kuromoji_tokenizer");
		super.serializeInternal(generator, mapper);
		if (this.discardPunctuation != null) {
			generator.writeKey("discard_punctuation");
			generator.write(this.discardPunctuation);

		}
		generator.writeKey("mode");
		this.mode.serialize(generator, mapper);
		if (this.nbestCost != null) {
			generator.writeKey("nbest_cost");
			generator.write(this.nbestCost);

		}
		if (this.nbestExamples != null) {
			generator.writeKey("nbest_examples");
			generator.write(this.nbestExamples);

		}
		if (this.userDictionary != null) {
			generator.writeKey("user_dictionary");
			generator.write(this.userDictionary);

		}
		if (ApiTypeHelper.isDefined(this.userDictionaryRules)) {
			generator.writeKey("user_dictionary_rules");
			generator.writeStartArray();
			for (String item0 : this.userDictionaryRules) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.discardCompoundToken != null) {
			generator.writeKey("discard_compound_token");
			generator.write(this.discardCompoundToken);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link KuromojiTokenizer}.
	 */

	public static class Builder extends TokenizerBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<KuromojiTokenizer> {
		@Nullable
		private Boolean discardPunctuation;

		private KuromojiTokenizationMode mode;

		@Nullable
		private Integer nbestCost;

		@Nullable
		private String nbestExamples;

		@Nullable
		private String userDictionary;

		@Nullable
		private List<String> userDictionaryRules;

		@Nullable
		private Boolean discardCompoundToken;

		/**
		 * API name: {@code discard_punctuation}
		 */
		public final Builder discardPunctuation(@Nullable Boolean value) {
			this.discardPunctuation = value;
			return this;
		}

		/**
		 * Required - API name: {@code mode}
		 */
		public final Builder mode(KuromojiTokenizationMode value) {
			this.mode = value;
			return this;
		}

		/**
		 * API name: {@code nbest_cost}
		 */
		public final Builder nbestCost(@Nullable Integer value) {
			this.nbestCost = value;
			return this;
		}

		/**
		 * API name: {@code nbest_examples}
		 */
		public final Builder nbestExamples(@Nullable String value) {
			this.nbestExamples = value;
			return this;
		}

		/**
		 * API name: {@code user_dictionary}
		 */
		public final Builder userDictionary(@Nullable String value) {
			this.userDictionary = value;
			return this;
		}

		/**
		 * API name: {@code user_dictionary_rules}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>userDictionaryRules</code>.
		 */
		public final Builder userDictionaryRules(List<String> list) {
			this.userDictionaryRules = _listAddAll(this.userDictionaryRules, list);
			return this;
		}

		/**
		 * API name: {@code user_dictionary_rules}
		 * <p>
		 * Adds one or more values to <code>userDictionaryRules</code>.
		 */
		public final Builder userDictionaryRules(String value, String... values) {
			this.userDictionaryRules = _listAdd(this.userDictionaryRules, value, values);
			return this;
		}

		/**
		 * API name: {@code discard_compound_token}
		 */
		public final Builder discardCompoundToken(@Nullable Boolean value) {
			this.discardCompoundToken = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link KuromojiTokenizer}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public KuromojiTokenizer build() {
			_checkSingleUse();

			return new KuromojiTokenizer(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link KuromojiTokenizer}
	 */
	public static final JsonpDeserializer<KuromojiTokenizer> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, KuromojiTokenizer::setupKuromojiTokenizerDeserializer);

	protected static void setupKuromojiTokenizerDeserializer(ObjectDeserializer<KuromojiTokenizer.Builder> op) {
		TokenizerBase.setupTokenizerBaseDeserializer(op);
		op.add(Builder::discardPunctuation, JsonpDeserializer.booleanDeserializer(), "discard_punctuation");
		op.add(Builder::mode, KuromojiTokenizationMode._DESERIALIZER, "mode");
		op.add(Builder::nbestCost, JsonpDeserializer.integerDeserializer(), "nbest_cost");
		op.add(Builder::nbestExamples, JsonpDeserializer.stringDeserializer(), "nbest_examples");
		op.add(Builder::userDictionary, JsonpDeserializer.stringDeserializer(), "user_dictionary");
		op.add(Builder::userDictionaryRules,
				JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "user_dictionary_rules");
		op.add(Builder::discardCompoundToken, JsonpDeserializer.booleanDeserializer(), "discard_compound_token");

		op.ignore("type");
	}

}
