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

// typedef: _types.analysis.WordDelimiterGraphTokenFilter

@JsonpDeserializable
public class WordDelimiterGraphTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	@Nullable
	private final Boolean adjustOffsets;

	@Nullable
	private final Boolean catenateAll;

	@Nullable
	private final Boolean catenateNumbers;

	@Nullable
	private final Boolean catenateWords;

	@Nullable
	private final Boolean generateNumberParts;

	@Nullable
	private final Boolean generateWordParts;

	@Nullable
	private final Boolean ignoreKeywords;

	@Nullable
	private final Boolean preserveOriginal;

	private final List<String> protectedWords;

	@Nullable
	private final String protectedWordsPath;

	@Nullable
	private final Boolean splitOnCaseChange;

	@Nullable
	private final Boolean splitOnNumerics;

	@Nullable
	private final Boolean stemEnglishPossessive;

	private final List<String> typeTable;

	@Nullable
	private final String typeTablePath;

	// ---------------------------------------------------------------------------------------------

	private WordDelimiterGraphTokenFilter(Builder builder) {
		super(builder);

		this.adjustOffsets = builder.adjustOffsets;
		this.catenateAll = builder.catenateAll;
		this.catenateNumbers = builder.catenateNumbers;
		this.catenateWords = builder.catenateWords;
		this.generateNumberParts = builder.generateNumberParts;
		this.generateWordParts = builder.generateWordParts;
		this.ignoreKeywords = builder.ignoreKeywords;
		this.preserveOriginal = builder.preserveOriginal;
		this.protectedWords = ApiTypeHelper.unmodifiable(builder.protectedWords);
		this.protectedWordsPath = builder.protectedWordsPath;
		this.splitOnCaseChange = builder.splitOnCaseChange;
		this.splitOnNumerics = builder.splitOnNumerics;
		this.stemEnglishPossessive = builder.stemEnglishPossessive;
		this.typeTable = ApiTypeHelper.unmodifiable(builder.typeTable);
		this.typeTablePath = builder.typeTablePath;

	}

	public static WordDelimiterGraphTokenFilter of(Function<Builder, ObjectBuilder<WordDelimiterGraphTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.WordDelimiterGraph;
	}

	/**
	 * API name: {@code adjust_offsets}
	 */
	@Nullable
	public final Boolean adjustOffsets() {
		return this.adjustOffsets;
	}

	/**
	 * API name: {@code catenate_all}
	 */
	@Nullable
	public final Boolean catenateAll() {
		return this.catenateAll;
	}

	/**
	 * API name: {@code catenate_numbers}
	 */
	@Nullable
	public final Boolean catenateNumbers() {
		return this.catenateNumbers;
	}

	/**
	 * API name: {@code catenate_words}
	 */
	@Nullable
	public final Boolean catenateWords() {
		return this.catenateWords;
	}

	/**
	 * API name: {@code generate_number_parts}
	 */
	@Nullable
	public final Boolean generateNumberParts() {
		return this.generateNumberParts;
	}

	/**
	 * API name: {@code generate_word_parts}
	 */
	@Nullable
	public final Boolean generateWordParts() {
		return this.generateWordParts;
	}

	/**
	 * API name: {@code ignore_keywords}
	 */
	@Nullable
	public final Boolean ignoreKeywords() {
		return this.ignoreKeywords;
	}

	/**
	 * API name: {@code preserve_original}
	 */
	@Nullable
	public final Boolean preserveOriginal() {
		return this.preserveOriginal;
	}

	/**
	 * API name: {@code protected_words}
	 */
	public final List<String> protectedWords() {
		return this.protectedWords;
	}

	/**
	 * API name: {@code protected_words_path}
	 */
	@Nullable
	public final String protectedWordsPath() {
		return this.protectedWordsPath;
	}

	/**
	 * API name: {@code split_on_case_change}
	 */
	@Nullable
	public final Boolean splitOnCaseChange() {
		return this.splitOnCaseChange;
	}

	/**
	 * API name: {@code split_on_numerics}
	 */
	@Nullable
	public final Boolean splitOnNumerics() {
		return this.splitOnNumerics;
	}

	/**
	 * API name: {@code stem_english_possessive}
	 */
	@Nullable
	public final Boolean stemEnglishPossessive() {
		return this.stemEnglishPossessive;
	}

	/**
	 * API name: {@code type_table}
	 */
	public final List<String> typeTable() {
		return this.typeTable;
	}

	/**
	 * API name: {@code type_table_path}
	 */
	@Nullable
	public final String typeTablePath() {
		return this.typeTablePath;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "word_delimiter_graph");
		super.serializeInternal(generator, mapper);
		if (this.adjustOffsets != null) {
			generator.writeKey("adjust_offsets");
			generator.write(this.adjustOffsets);

		}
		if (this.catenateAll != null) {
			generator.writeKey("catenate_all");
			generator.write(this.catenateAll);

		}
		if (this.catenateNumbers != null) {
			generator.writeKey("catenate_numbers");
			generator.write(this.catenateNumbers);

		}
		if (this.catenateWords != null) {
			generator.writeKey("catenate_words");
			generator.write(this.catenateWords);

		}
		if (this.generateNumberParts != null) {
			generator.writeKey("generate_number_parts");
			generator.write(this.generateNumberParts);

		}
		if (this.generateWordParts != null) {
			generator.writeKey("generate_word_parts");
			generator.write(this.generateWordParts);

		}
		if (this.ignoreKeywords != null) {
			generator.writeKey("ignore_keywords");
			generator.write(this.ignoreKeywords);

		}
		if (this.preserveOriginal != null) {
			generator.writeKey("preserve_original");
			generator.write(this.preserveOriginal);

		}
		if (ApiTypeHelper.isDefined(this.protectedWords)) {
			generator.writeKey("protected_words");
			generator.writeStartArray();
			for (String item0 : this.protectedWords) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.protectedWordsPath != null) {
			generator.writeKey("protected_words_path");
			generator.write(this.protectedWordsPath);

		}
		if (this.splitOnCaseChange != null) {
			generator.writeKey("split_on_case_change");
			generator.write(this.splitOnCaseChange);

		}
		if (this.splitOnNumerics != null) {
			generator.writeKey("split_on_numerics");
			generator.write(this.splitOnNumerics);

		}
		if (this.stemEnglishPossessive != null) {
			generator.writeKey("stem_english_possessive");
			generator.write(this.stemEnglishPossessive);

		}
		if (ApiTypeHelper.isDefined(this.typeTable)) {
			generator.writeKey("type_table");
			generator.writeStartArray();
			for (String item0 : this.typeTable) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.typeTablePath != null) {
			generator.writeKey("type_table_path");
			generator.write(this.typeTablePath);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link WordDelimiterGraphTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<WordDelimiterGraphTokenFilter> {
		@Nullable
		private Boolean adjustOffsets;

		@Nullable
		private Boolean catenateAll;

		@Nullable
		private Boolean catenateNumbers;

		@Nullable
		private Boolean catenateWords;

		@Nullable
		private Boolean generateNumberParts;

		@Nullable
		private Boolean generateWordParts;

		@Nullable
		private Boolean ignoreKeywords;

		@Nullable
		private Boolean preserveOriginal;

		@Nullable
		private List<String> protectedWords;

		@Nullable
		private String protectedWordsPath;

		@Nullable
		private Boolean splitOnCaseChange;

		@Nullable
		private Boolean splitOnNumerics;

		@Nullable
		private Boolean stemEnglishPossessive;

		@Nullable
		private List<String> typeTable;

		@Nullable
		private String typeTablePath;

		/**
		 * API name: {@code adjust_offsets}
		 */
		public final Builder adjustOffsets(@Nullable Boolean value) {
			this.adjustOffsets = value;
			return this;
		}

		/**
		 * API name: {@code catenate_all}
		 */
		public final Builder catenateAll(@Nullable Boolean value) {
			this.catenateAll = value;
			return this;
		}

		/**
		 * API name: {@code catenate_numbers}
		 */
		public final Builder catenateNumbers(@Nullable Boolean value) {
			this.catenateNumbers = value;
			return this;
		}

		/**
		 * API name: {@code catenate_words}
		 */
		public final Builder catenateWords(@Nullable Boolean value) {
			this.catenateWords = value;
			return this;
		}

		/**
		 * API name: {@code generate_number_parts}
		 */
		public final Builder generateNumberParts(@Nullable Boolean value) {
			this.generateNumberParts = value;
			return this;
		}

		/**
		 * API name: {@code generate_word_parts}
		 */
		public final Builder generateWordParts(@Nullable Boolean value) {
			this.generateWordParts = value;
			return this;
		}

		/**
		 * API name: {@code ignore_keywords}
		 */
		public final Builder ignoreKeywords(@Nullable Boolean value) {
			this.ignoreKeywords = value;
			return this;
		}

		/**
		 * API name: {@code preserve_original}
		 */
		public final Builder preserveOriginal(@Nullable Boolean value) {
			this.preserveOriginal = value;
			return this;
		}

		/**
		 * API name: {@code protected_words}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>protectedWords</code>.
		 */
		public final Builder protectedWords(List<String> list) {
			this.protectedWords = _listAddAll(this.protectedWords, list);
			return this;
		}

		/**
		 * API name: {@code protected_words}
		 * <p>
		 * Adds one or more values to <code>protectedWords</code>.
		 */
		public final Builder protectedWords(String value, String... values) {
			this.protectedWords = _listAdd(this.protectedWords, value, values);
			return this;
		}

		/**
		 * API name: {@code protected_words_path}
		 */
		public final Builder protectedWordsPath(@Nullable String value) {
			this.protectedWordsPath = value;
			return this;
		}

		/**
		 * API name: {@code split_on_case_change}
		 */
		public final Builder splitOnCaseChange(@Nullable Boolean value) {
			this.splitOnCaseChange = value;
			return this;
		}

		/**
		 * API name: {@code split_on_numerics}
		 */
		public final Builder splitOnNumerics(@Nullable Boolean value) {
			this.splitOnNumerics = value;
			return this;
		}

		/**
		 * API name: {@code stem_english_possessive}
		 */
		public final Builder stemEnglishPossessive(@Nullable Boolean value) {
			this.stemEnglishPossessive = value;
			return this;
		}

		/**
		 * API name: {@code type_table}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>typeTable</code>.
		 */
		public final Builder typeTable(List<String> list) {
			this.typeTable = _listAddAll(this.typeTable, list);
			return this;
		}

		/**
		 * API name: {@code type_table}
		 * <p>
		 * Adds one or more values to <code>typeTable</code>.
		 */
		public final Builder typeTable(String value, String... values) {
			this.typeTable = _listAdd(this.typeTable, value, values);
			return this;
		}

		/**
		 * API name: {@code type_table_path}
		 */
		public final Builder typeTablePath(@Nullable String value) {
			this.typeTablePath = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link WordDelimiterGraphTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public WordDelimiterGraphTokenFilter build() {
			_checkSingleUse();

			return new WordDelimiterGraphTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link WordDelimiterGraphTokenFilter}
	 */
	public static final JsonpDeserializer<WordDelimiterGraphTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, WordDelimiterGraphTokenFilter::setupWordDelimiterGraphTokenFilterDeserializer);

	protected static void setupWordDelimiterGraphTokenFilterDeserializer(
			ObjectDeserializer<WordDelimiterGraphTokenFilter.Builder> op) {
		TokenFilterBase.setupTokenFilterBaseDeserializer(op);
		op.add(Builder::adjustOffsets, JsonpDeserializer.booleanDeserializer(), "adjust_offsets");
		op.add(Builder::catenateAll, JsonpDeserializer.booleanDeserializer(), "catenate_all");
		op.add(Builder::catenateNumbers, JsonpDeserializer.booleanDeserializer(), "catenate_numbers");
		op.add(Builder::catenateWords, JsonpDeserializer.booleanDeserializer(), "catenate_words");
		op.add(Builder::generateNumberParts, JsonpDeserializer.booleanDeserializer(), "generate_number_parts");
		op.add(Builder::generateWordParts, JsonpDeserializer.booleanDeserializer(), "generate_word_parts");
		op.add(Builder::ignoreKeywords, JsonpDeserializer.booleanDeserializer(), "ignore_keywords");
		op.add(Builder::preserveOriginal, JsonpDeserializer.booleanDeserializer(), "preserve_original");
		op.add(Builder::protectedWords, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"protected_words");
		op.add(Builder::protectedWordsPath, JsonpDeserializer.stringDeserializer(), "protected_words_path");
		op.add(Builder::splitOnCaseChange, JsonpDeserializer.booleanDeserializer(), "split_on_case_change");
		op.add(Builder::splitOnNumerics, JsonpDeserializer.booleanDeserializer(), "split_on_numerics");
		op.add(Builder::stemEnglishPossessive, JsonpDeserializer.booleanDeserializer(), "stem_english_possessive");
		op.add(Builder::typeTable, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"type_table");
		op.add(Builder::typeTablePath, JsonpDeserializer.stringDeserializer(), "type_table_path");

		op.ignore("type");
	}

}
