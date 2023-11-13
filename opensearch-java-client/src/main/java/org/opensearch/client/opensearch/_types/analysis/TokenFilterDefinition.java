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

import org.opensearch.client.json.JsonEnum;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;

// typedef: _types.analysis.TokenFilterDefinition


@JsonpDeserializable
public class TokenFilterDefinition
		implements
			TaggedUnion<TokenFilterDefinition.Kind, TokenFilterDefinitionVariant>,
			JsonpSerializable {

	/**
	 * {@link TokenFilterDefinition} variant kinds.
	 */
	/**
	 * {@link TokenFilterDefinition} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		Asciifolding("asciifolding"),

		CommonGrams("common_grams"),

		Condition("condition"),

		DelimitedPayload("delimited_payload"),

		DictionaryDecompounder("dictionary_decompounder"),

		EdgeNgram("edge_ngram"),

		Elision("elision"),

		Fingerprint("fingerprint"),

		Hunspell("hunspell"),

		HyphenationDecompounder("hyphenation_decompounder"),

		IcuCollation("icu_collation"),

		IcuFolding("icu_folding"),

		IcuNormalizer("icu_normalizer"),

		IcuTokenizer("icu_tokenizer"),

		IcuTransform("icu_transform"),

		Kstem("kstem"),

		KeepTypes("keep_types"),

		Keep("keep"),

		KeywordMarker("keyword_marker"),

		KuromojiPartOfSpeech("kuromoji_part_of_speech"),

		KuromojiReadingform("kuromoji_readingform"),

		KuromojiStemmer("kuromoji_stemmer"),

		Length("length"),

		Limit("limit"),

		Lowercase("lowercase"),

		Multiplexer("multiplexer"),

		Ngram("ngram"),

		NoriPartOfSpeech("nori_part_of_speech"),

		PatternCapture("pattern_capture"),

		PatternReplace("pattern_replace"),

		Phonetic("phonetic"),

		PorterStem("porter_stem"),

		PredicateTokenFilter("predicate_token_filter"),

		RemoveDuplicates("remove_duplicates"),

		Reverse("reverse"),

		Shingle("shingle"),

		Snowball("snowball"),

		StemmerOverride("stemmer_override"),

		Stemmer("stemmer"),

		Stop("stop"),

		SynonymGraph("synonym_graph"),

		Synonym("synonym"),

		Trim("trim"),

		Truncate("truncate"),

		Unique("unique"),

		Uppercase("uppercase"),

		WordDelimiterGraph("word_delimiter_graph"),

		WordDelimiter("word_delimiter"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

	}

	private final Kind _kind;
	private final TokenFilterDefinitionVariant _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final TokenFilterDefinitionVariant _get() {
		return _value;
	}

	public TokenFilterDefinition(TokenFilterDefinitionVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._tokenFilterDefinitionKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

	}

	private TokenFilterDefinition(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static TokenFilterDefinition of(Function<Builder, ObjectBuilder<TokenFilterDefinition>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code asciifolding}?
	 */
	public boolean isAsciifolding() {
		return _kind == Kind.Asciifolding;
	}

	/**
	 * Get the {@code asciifolding} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code asciifolding} kind.
	 */
	public AsciiFoldingTokenFilter asciifolding() {
		return TaggedUnionUtils.get(this, Kind.Asciifolding);
	}

	/**
	 * Is this variant instance of kind {@code common_grams}?
	 */
	public boolean isCommonGrams() {
		return _kind == Kind.CommonGrams;
	}

	/**
	 * Get the {@code common_grams} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code common_grams} kind.
	 */
	public CommonGramsTokenFilter commonGrams() {
		return TaggedUnionUtils.get(this, Kind.CommonGrams);
	}

	/**
	 * Is this variant instance of kind {@code condition}?
	 */
	public boolean isCondition() {
		return _kind == Kind.Condition;
	}

	/**
	 * Get the {@code condition} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code condition} kind.
	 */
	public ConditionTokenFilter condition() {
		return TaggedUnionUtils.get(this, Kind.Condition);
	}

	/**
	 * Is this variant instance of kind {@code delimited_payload}?
	 */
	public boolean isDelimitedPayload() {
		return _kind == Kind.DelimitedPayload;
	}

	/**
	 * Get the {@code delimited_payload} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code delimited_payload}
	 *             kind.
	 */
	public DelimitedPayloadTokenFilter delimitedPayload() {
		return TaggedUnionUtils.get(this, Kind.DelimitedPayload);
	}

	/**
	 * Is this variant instance of kind {@code dictionary_decompounder}?
	 */
	public boolean isDictionaryDecompounder() {
		return _kind == Kind.DictionaryDecompounder;
	}

	/**
	 * Get the {@code dictionary_decompounder} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code dictionary_decompounder} kind.
	 */
	public DictionaryDecompounderTokenFilter dictionaryDecompounder() {
		return TaggedUnionUtils.get(this, Kind.DictionaryDecompounder);
	}

	/**
	 * Is this variant instance of kind {@code edge_ngram}?
	 */
	public boolean isEdgeNgram() {
		return _kind == Kind.EdgeNgram;
	}

	/**
	 * Get the {@code edge_ngram} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code edge_ngram} kind.
	 */
	public EdgeNGramTokenFilter edgeNgram() {
		return TaggedUnionUtils.get(this, Kind.EdgeNgram);
	}

	/**
	 * Is this variant instance of kind {@code elision}?
	 */
	public boolean isElision() {
		return _kind == Kind.Elision;
	}

	/**
	 * Get the {@code elision} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code elision} kind.
	 */
	public ElisionTokenFilter elision() {
		return TaggedUnionUtils.get(this, Kind.Elision);
	}

	/**
	 * Is this variant instance of kind {@code fingerprint}?
	 */
	public boolean isFingerprint() {
		return _kind == Kind.Fingerprint;
	}

	/**
	 * Get the {@code fingerprint} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code fingerprint} kind.
	 */
	public FingerprintTokenFilter fingerprint() {
		return TaggedUnionUtils.get(this, Kind.Fingerprint);
	}

	/**
	 * Is this variant instance of kind {@code hunspell}?
	 */
	public boolean isHunspell() {
		return _kind == Kind.Hunspell;
	}

	/**
	 * Get the {@code hunspell} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code hunspell} kind.
	 */
	public HunspellTokenFilter hunspell() {
		return TaggedUnionUtils.get(this, Kind.Hunspell);
	}

	/**
	 * Is this variant instance of kind {@code hyphenation_decompounder}?
	 */
	public boolean isHyphenationDecompounder() {
		return _kind == Kind.HyphenationDecompounder;
	}

	/**
	 * Get the {@code hyphenation_decompounder} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code hyphenation_decompounder} kind.
	 */
	public HyphenationDecompounderTokenFilter hyphenationDecompounder() {
		return TaggedUnionUtils.get(this, Kind.HyphenationDecompounder);
	}

	/**
	 * Is this variant instance of kind {@code icu_collation}?
	 */
	public boolean isIcuCollation() {
		return _kind == Kind.IcuCollation;
	}

	/**
	 * Get the {@code icu_collation} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code icu_collation} kind.
	 */
	public IcuCollationTokenFilter icuCollation() {
		return TaggedUnionUtils.get(this, Kind.IcuCollation);
	}

	/**
	 * Is this variant instance of kind {@code icu_folding}?
	 */
	public boolean isIcuFolding() {
		return _kind == Kind.IcuFolding;
	}

	/**
	 * Get the {@code icu_folding} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code icu_folding} kind.
	 */
	public IcuFoldingTokenFilter icuFolding() {
		return TaggedUnionUtils.get(this, Kind.IcuFolding);
	}

	/**
	 * Is this variant instance of kind {@code icu_normalizer}?
	 */
	public boolean isIcuNormalizer() {
		return _kind == Kind.IcuNormalizer;
	}

	/**
	 * Get the {@code icu_normalizer} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code icu_normalizer} kind.
	 */
	public IcuNormalizationTokenFilter icuNormalizer() {
		return TaggedUnionUtils.get(this, Kind.IcuNormalizer);
	}

	/**
	 * Is this variant instance of kind {@code icu_tokenizer}?
	 */
	public boolean isIcuTokenizer() {
		return _kind == Kind.IcuTokenizer;
	}

	/**
	 * Get the {@code icu_tokenizer} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code icu_tokenizer} kind.
	 */
	public IcuTokenizer icuTokenizer() {
		return TaggedUnionUtils.get(this, Kind.IcuTokenizer);
	}

	/**
	 * Is this variant instance of kind {@code icu_transform}?
	 */
	public boolean isIcuTransform() {
		return _kind == Kind.IcuTransform;
	}

	/**
	 * Get the {@code icu_transform} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code icu_transform} kind.
	 */
	public IcuTransformTokenFilter icuTransform() {
		return TaggedUnionUtils.get(this, Kind.IcuTransform);
	}

	/**
	 * Is this variant instance of kind {@code kstem}?
	 */
	public boolean isKstem() {
		return _kind == Kind.Kstem;
	}

	/**
	 * Get the {@code kstem} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code kstem} kind.
	 */
	public KStemTokenFilter kstem() {
		return TaggedUnionUtils.get(this, Kind.Kstem);
	}

	/**
	 * Is this variant instance of kind {@code keep_types}?
	 */
	public boolean isKeepTypes() {
		return _kind == Kind.KeepTypes;
	}

	/**
	 * Get the {@code keep_types} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code keep_types} kind.
	 */
	public KeepTypesTokenFilter keepTypes() {
		return TaggedUnionUtils.get(this, Kind.KeepTypes);
	}

	/**
	 * Is this variant instance of kind {@code keep}?
	 */
	public boolean isKeep() {
		return _kind == Kind.Keep;
	}

	/**
	 * Get the {@code keep} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code keep} kind.
	 */
	public KeepWordsTokenFilter keep() {
		return TaggedUnionUtils.get(this, Kind.Keep);
	}

	/**
	 * Is this variant instance of kind {@code keyword_marker}?
	 */
	public boolean isKeywordMarker() {
		return _kind == Kind.KeywordMarker;
	}

	/**
	 * Get the {@code keyword_marker} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code keyword_marker} kind.
	 */
	public KeywordMarkerTokenFilter keywordMarker() {
		return TaggedUnionUtils.get(this, Kind.KeywordMarker);
	}

	/**
	 * Is this variant instance of kind {@code kuromoji_part_of_speech}?
	 */
	public boolean isKuromojiPartOfSpeech() {
		return _kind == Kind.KuromojiPartOfSpeech;
	}

	/**
	 * Get the {@code kuromoji_part_of_speech} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code kuromoji_part_of_speech} kind.
	 */
	public KuromojiPartOfSpeechTokenFilter kuromojiPartOfSpeech() {
		return TaggedUnionUtils.get(this, Kind.KuromojiPartOfSpeech);
	}

	/**
	 * Is this variant instance of kind {@code kuromoji_readingform}?
	 */
	public boolean isKuromojiReadingform() {
		return _kind == Kind.KuromojiReadingform;
	}

	/**
	 * Get the {@code kuromoji_readingform} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code kuromoji_readingform}
	 *             kind.
	 */
	public KuromojiReadingFormTokenFilter kuromojiReadingform() {
		return TaggedUnionUtils.get(this, Kind.KuromojiReadingform);
	}

	/**
	 * Is this variant instance of kind {@code kuromoji_stemmer}?
	 */
	public boolean isKuromojiStemmer() {
		return _kind == Kind.KuromojiStemmer;
	}

	/**
	 * Get the {@code kuromoji_stemmer} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code kuromoji_stemmer}
	 *             kind.
	 */
	public KuromojiStemmerTokenFilter kuromojiStemmer() {
		return TaggedUnionUtils.get(this, Kind.KuromojiStemmer);
	}

	/**
	 * Is this variant instance of kind {@code length}?
	 */
	public boolean isLength() {
		return _kind == Kind.Length;
	}

	/**
	 * Get the {@code length} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code length} kind.
	 */
	public LengthTokenFilter length() {
		return TaggedUnionUtils.get(this, Kind.Length);
	}

	/**
	 * Is this variant instance of kind {@code limit}?
	 */
	public boolean isLimit() {
		return _kind == Kind.Limit;
	}

	/**
	 * Get the {@code limit} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code limit} kind.
	 */
	public LimitTokenCountTokenFilter limit() {
		return TaggedUnionUtils.get(this, Kind.Limit);
	}

	/**
	 * Is this variant instance of kind {@code lowercase}?
	 */
	public boolean isLowercase() {
		return _kind == Kind.Lowercase;
	}

	/**
	 * Get the {@code lowercase} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code lowercase} kind.
	 */
	public LowercaseTokenFilter lowercase() {
		return TaggedUnionUtils.get(this, Kind.Lowercase);
	}

	/**
	 * Is this variant instance of kind {@code multiplexer}?
	 */
	public boolean isMultiplexer() {
		return _kind == Kind.Multiplexer;
	}

	/**
	 * Get the {@code multiplexer} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code multiplexer} kind.
	 */
	public MultiplexerTokenFilter multiplexer() {
		return TaggedUnionUtils.get(this, Kind.Multiplexer);
	}

	/**
	 * Is this variant instance of kind {@code ngram}?
	 */
	public boolean isNgram() {
		return _kind == Kind.Ngram;
	}

	/**
	 * Get the {@code ngram} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code ngram} kind.
	 */
	public NGramTokenFilter ngram() {
		return TaggedUnionUtils.get(this, Kind.Ngram);
	}

	/**
	 * Is this variant instance of kind {@code nori_part_of_speech}?
	 */
	public boolean isNoriPartOfSpeech() {
		return _kind == Kind.NoriPartOfSpeech;
	}

	/**
	 * Get the {@code nori_part_of_speech} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code nori_part_of_speech}
	 *             kind.
	 */
	public NoriPartOfSpeechTokenFilter noriPartOfSpeech() {
		return TaggedUnionUtils.get(this, Kind.NoriPartOfSpeech);
	}

	/**
	 * Is this variant instance of kind {@code pattern_capture}?
	 */
	public boolean isPatternCapture() {
		return _kind == Kind.PatternCapture;
	}

	/**
	 * Get the {@code pattern_capture} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code pattern_capture}
	 *             kind.
	 */
	public PatternCaptureTokenFilter patternCapture() {
		return TaggedUnionUtils.get(this, Kind.PatternCapture);
	}

	/**
	 * Is this variant instance of kind {@code pattern_replace}?
	 */
	public boolean isPatternReplace() {
		return _kind == Kind.PatternReplace;
	}

	/**
	 * Get the {@code pattern_replace} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code pattern_replace}
	 *             kind.
	 */
	public PatternReplaceTokenFilter patternReplace() {
		return TaggedUnionUtils.get(this, Kind.PatternReplace);
	}

	/**
	 * Is this variant instance of kind {@code phonetic}?
	 */
	public boolean isPhonetic() {
		return _kind == Kind.Phonetic;
	}

	/**
	 * Get the {@code phonetic} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code phonetic} kind.
	 */
	public PhoneticTokenFilter phonetic() {
		return TaggedUnionUtils.get(this, Kind.Phonetic);
	}

	/**
	 * Is this variant instance of kind {@code porter_stem}?
	 */
	public boolean isPorterStem() {
		return _kind == Kind.PorterStem;
	}

	/**
	 * Get the {@code porter_stem} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code porter_stem} kind.
	 */
	public PorterStemTokenFilter porterStem() {
		return TaggedUnionUtils.get(this, Kind.PorterStem);
	}

	/**
	 * Is this variant instance of kind {@code predicate_token_filter}?
	 */
	public boolean isPredicateTokenFilter() {
		return _kind == Kind.PredicateTokenFilter;
	}

	/**
	 * Get the {@code predicate_token_filter} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code predicate_token_filter} kind.
	 */
	public PredicateTokenFilter predicateTokenFilter() {
		return TaggedUnionUtils.get(this, Kind.PredicateTokenFilter);
	}

	/**
	 * Is this variant instance of kind {@code remove_duplicates}?
	 */
	public boolean isRemoveDuplicates() {
		return _kind == Kind.RemoveDuplicates;
	}

	/**
	 * Get the {@code remove_duplicates} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code remove_duplicates}
	 *             kind.
	 */
	public RemoveDuplicatesTokenFilter removeDuplicates() {
		return TaggedUnionUtils.get(this, Kind.RemoveDuplicates);
	}

	/**
	 * Is this variant instance of kind {@code reverse}?
	 */
	public boolean isReverse() {
		return _kind == Kind.Reverse;
	}

	/**
	 * Get the {@code reverse} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code reverse} kind.
	 */
	public ReverseTokenFilter reverse() {
		return TaggedUnionUtils.get(this, Kind.Reverse);
	}

	/**
	 * Is this variant instance of kind {@code shingle}?
	 */
	public boolean isShingle() {
		return _kind == Kind.Shingle;
	}

	/**
	 * Get the {@code shingle} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code shingle} kind.
	 */
	public ShingleTokenFilter shingle() {
		return TaggedUnionUtils.get(this, Kind.Shingle);
	}

	/**
	 * Is this variant instance of kind {@code snowball}?
	 */
	public boolean isSnowball() {
		return _kind == Kind.Snowball;
	}

	/**
	 * Get the {@code snowball} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code snowball} kind.
	 */
	public SnowballTokenFilter snowball() {
		return TaggedUnionUtils.get(this, Kind.Snowball);
	}

	/**
	 * Is this variant instance of kind {@code stemmer_override}?
	 */
	public boolean isStemmerOverride() {
		return _kind == Kind.StemmerOverride;
	}

	/**
	 * Get the {@code stemmer_override} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code stemmer_override}
	 *             kind.
	 */
	public StemmerOverrideTokenFilter stemmerOverride() {
		return TaggedUnionUtils.get(this, Kind.StemmerOverride);
	}

	/**
	 * Is this variant instance of kind {@code stemmer}?
	 */
	public boolean isStemmer() {
		return _kind == Kind.Stemmer;
	}

	/**
	 * Get the {@code stemmer} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code stemmer} kind.
	 */
	public StemmerTokenFilter stemmer() {
		return TaggedUnionUtils.get(this, Kind.Stemmer);
	}

	/**
	 * Is this variant instance of kind {@code stop}?
	 */
	public boolean isStop() {
		return _kind == Kind.Stop;
	}

	/**
	 * Get the {@code stop} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code stop} kind.
	 */
	public StopTokenFilter stop() {
		return TaggedUnionUtils.get(this, Kind.Stop);
	}

	/**
	 * Is this variant instance of kind {@code synonym_graph}?
	 */
	public boolean isSynonymGraph() {
		return _kind == Kind.SynonymGraph;
	}

	/**
	 * Get the {@code synonym_graph} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code synonym_graph} kind.
	 */
	public SynonymGraphTokenFilter synonymGraph() {
		return TaggedUnionUtils.get(this, Kind.SynonymGraph);
	}

	/**
	 * Is this variant instance of kind {@code synonym}?
	 */
	public boolean isSynonym() {
		return _kind == Kind.Synonym;
	}

	/**
	 * Get the {@code synonym} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code synonym} kind.
	 */
	public SynonymTokenFilter synonym() {
		return TaggedUnionUtils.get(this, Kind.Synonym);
	}

	/**
	 * Is this variant instance of kind {@code trim}?
	 */
	public boolean isTrim() {
		return _kind == Kind.Trim;
	}

	/**
	 * Get the {@code trim} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code trim} kind.
	 */
	public TrimTokenFilter trim() {
		return TaggedUnionUtils.get(this, Kind.Trim);
	}

	/**
	 * Is this variant instance of kind {@code truncate}?
	 */
	public boolean isTruncate() {
		return _kind == Kind.Truncate;
	}

	/**
	 * Get the {@code truncate} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code truncate} kind.
	 */
	public TruncateTokenFilter truncate() {
		return TaggedUnionUtils.get(this, Kind.Truncate);
	}

	/**
	 * Is this variant instance of kind {@code unique}?
	 */
	public boolean isUnique() {
		return _kind == Kind.Unique;
	}

	/**
	 * Get the {@code unique} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code unique} kind.
	 */
	public UniqueTokenFilter unique() {
		return TaggedUnionUtils.get(this, Kind.Unique);
	}

	/**
	 * Is this variant instance of kind {@code uppercase}?
	 */
	public boolean isUppercase() {
		return _kind == Kind.Uppercase;
	}

	/**
	 * Get the {@code uppercase} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code uppercase} kind.
	 */
	public UppercaseTokenFilter uppercase() {
		return TaggedUnionUtils.get(this, Kind.Uppercase);
	}

	/**
	 * Is this variant instance of kind {@code word_delimiter_graph}?
	 */
	public boolean isWordDelimiterGraph() {
		return _kind == Kind.WordDelimiterGraph;
	}

	/**
	 * Get the {@code word_delimiter_graph} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code word_delimiter_graph}
	 *             kind.
	 */
	public WordDelimiterGraphTokenFilter wordDelimiterGraph() {
		return TaggedUnionUtils.get(this, Kind.WordDelimiterGraph);
	}

	/**
	 * Is this variant instance of kind {@code word_delimiter}?
	 */
	public boolean isWordDelimiter() {
		return _kind == Kind.WordDelimiter;
	}

	/**
	 * Get the {@code word_delimiter} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code word_delimiter} kind.
	 */
	public WordDelimiterTokenFilter wordDelimiter() {
		return TaggedUnionUtils.get(this, Kind.WordDelimiter);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		mapper.serialize(_value, generator);

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<TokenFilterDefinition> {
		private Kind _kind;
		private TokenFilterDefinitionVariant _value;

		public ObjectBuilder<TokenFilterDefinition> asciifolding(AsciiFoldingTokenFilter v) {
			this._kind = Kind.Asciifolding;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> asciifolding(
				Function<AsciiFoldingTokenFilter.Builder, ObjectBuilder<AsciiFoldingTokenFilter>> fn) {
			return this.asciifolding(fn.apply(new AsciiFoldingTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> commonGrams(CommonGramsTokenFilter v) {
			this._kind = Kind.CommonGrams;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> commonGrams(
				Function<CommonGramsTokenFilter.Builder, ObjectBuilder<CommonGramsTokenFilter>> fn) {
			return this.commonGrams(fn.apply(new CommonGramsTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> condition(ConditionTokenFilter v) {
			this._kind = Kind.Condition;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> condition(
				Function<ConditionTokenFilter.Builder, ObjectBuilder<ConditionTokenFilter>> fn) {
			return this.condition(fn.apply(new ConditionTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> delimitedPayload(DelimitedPayloadTokenFilter v) {
			this._kind = Kind.DelimitedPayload;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> delimitedPayload(
				Function<DelimitedPayloadTokenFilter.Builder, ObjectBuilder<DelimitedPayloadTokenFilter>> fn) {
			return this.delimitedPayload(fn.apply(new DelimitedPayloadTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> dictionaryDecompounder(DictionaryDecompounderTokenFilter v) {
			this._kind = Kind.DictionaryDecompounder;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> dictionaryDecompounder(
				Function<DictionaryDecompounderTokenFilter.Builder, ObjectBuilder<DictionaryDecompounderTokenFilter>> fn) {
			return this.dictionaryDecompounder(fn.apply(new DictionaryDecompounderTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> edgeNgram(EdgeNGramTokenFilter v) {
			this._kind = Kind.EdgeNgram;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> edgeNgram(
				Function<EdgeNGramTokenFilter.Builder, ObjectBuilder<EdgeNGramTokenFilter>> fn) {
			return this.edgeNgram(fn.apply(new EdgeNGramTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> elision(ElisionTokenFilter v) {
			this._kind = Kind.Elision;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> elision(
				Function<ElisionTokenFilter.Builder, ObjectBuilder<ElisionTokenFilter>> fn) {
			return this.elision(fn.apply(new ElisionTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> fingerprint(FingerprintTokenFilter v) {
			this._kind = Kind.Fingerprint;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> fingerprint(
				Function<FingerprintTokenFilter.Builder, ObjectBuilder<FingerprintTokenFilter>> fn) {
			return this.fingerprint(fn.apply(new FingerprintTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> hunspell(HunspellTokenFilter v) {
			this._kind = Kind.Hunspell;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> hunspell(
				Function<HunspellTokenFilter.Builder, ObjectBuilder<HunspellTokenFilter>> fn) {
			return this.hunspell(fn.apply(new HunspellTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> hyphenationDecompounder(HyphenationDecompounderTokenFilter v) {
			this._kind = Kind.HyphenationDecompounder;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> hyphenationDecompounder(
				Function<HyphenationDecompounderTokenFilter.Builder,
						ObjectBuilder<HyphenationDecompounderTokenFilter>> fn) {
			return this.hyphenationDecompounder(fn.apply(new HyphenationDecompounderTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> icuCollation(IcuCollationTokenFilter v) {
			this._kind = Kind.IcuCollation;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> icuCollation(
				Function<IcuCollationTokenFilter.Builder, ObjectBuilder<IcuCollationTokenFilter>> fn) {
			return this.icuCollation(fn.apply(new IcuCollationTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> icuFolding(IcuFoldingTokenFilter v) {
			this._kind = Kind.IcuFolding;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> icuFolding(
				Function<IcuFoldingTokenFilter.Builder, ObjectBuilder<IcuFoldingTokenFilter>> fn) {
			return this.icuFolding(fn.apply(new IcuFoldingTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> icuNormalizer(IcuNormalizationTokenFilter v) {
			this._kind = Kind.IcuNormalizer;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> icuNormalizer(
				Function<IcuNormalizationTokenFilter.Builder, ObjectBuilder<IcuNormalizationTokenFilter>> fn) {
			return this.icuNormalizer(fn.apply(new IcuNormalizationTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> icuTokenizer(IcuTokenizer v) {
			this._kind = Kind.IcuTokenizer;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> icuTokenizer(
				Function<IcuTokenizer.Builder, ObjectBuilder<IcuTokenizer>> fn) {
			return this.icuTokenizer(fn.apply(new IcuTokenizer.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> icuTransform(IcuTransformTokenFilter v) {
			this._kind = Kind.IcuTransform;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> icuTransform(
				Function<IcuTransformTokenFilter.Builder, ObjectBuilder<IcuTransformTokenFilter>> fn) {
			return this.icuTransform(fn.apply(new IcuTransformTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> kstem(KStemTokenFilter v) {
			this._kind = Kind.Kstem;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> kstem(
				Function<KStemTokenFilter.Builder, ObjectBuilder<KStemTokenFilter>> fn) {
			return this.kstem(fn.apply(new KStemTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> keepTypes(KeepTypesTokenFilter v) {
			this._kind = Kind.KeepTypes;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> keepTypes(
				Function<KeepTypesTokenFilter.Builder, ObjectBuilder<KeepTypesTokenFilter>> fn) {
			return this.keepTypes(fn.apply(new KeepTypesTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> keep(KeepWordsTokenFilter v) {
			this._kind = Kind.Keep;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> keep(
				Function<KeepWordsTokenFilter.Builder, ObjectBuilder<KeepWordsTokenFilter>> fn) {
			return this.keep(fn.apply(new KeepWordsTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> keywordMarker(KeywordMarkerTokenFilter v) {
			this._kind = Kind.KeywordMarker;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> keywordMarker(
				Function<KeywordMarkerTokenFilter.Builder, ObjectBuilder<KeywordMarkerTokenFilter>> fn) {
			return this.keywordMarker(fn.apply(new KeywordMarkerTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> kuromojiPartOfSpeech(KuromojiPartOfSpeechTokenFilter v) {
			this._kind = Kind.KuromojiPartOfSpeech;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> kuromojiPartOfSpeech(
				Function<KuromojiPartOfSpeechTokenFilter.Builder, ObjectBuilder<KuromojiPartOfSpeechTokenFilter>> fn) {
			return this.kuromojiPartOfSpeech(fn.apply(new KuromojiPartOfSpeechTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> kuromojiReadingform(KuromojiReadingFormTokenFilter v) {
			this._kind = Kind.KuromojiReadingform;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> kuromojiReadingform(
				Function<KuromojiReadingFormTokenFilter.Builder, ObjectBuilder<KuromojiReadingFormTokenFilter>> fn) {
			return this.kuromojiReadingform(fn.apply(new KuromojiReadingFormTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> kuromojiStemmer(KuromojiStemmerTokenFilter v) {
			this._kind = Kind.KuromojiStemmer;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> kuromojiStemmer(
				Function<KuromojiStemmerTokenFilter.Builder, ObjectBuilder<KuromojiStemmerTokenFilter>> fn) {
			return this.kuromojiStemmer(fn.apply(new KuromojiStemmerTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> length(LengthTokenFilter v) {
			this._kind = Kind.Length;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> length(
				Function<LengthTokenFilter.Builder, ObjectBuilder<LengthTokenFilter>> fn) {
			return this.length(fn.apply(new LengthTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> limit(LimitTokenCountTokenFilter v) {
			this._kind = Kind.Limit;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> limit(
				Function<LimitTokenCountTokenFilter.Builder, ObjectBuilder<LimitTokenCountTokenFilter>> fn) {
			return this.limit(fn.apply(new LimitTokenCountTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> lowercase(LowercaseTokenFilter v) {
			this._kind = Kind.Lowercase;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> lowercase(
				Function<LowercaseTokenFilter.Builder, ObjectBuilder<LowercaseTokenFilter>> fn) {
			return this.lowercase(fn.apply(new LowercaseTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> multiplexer(MultiplexerTokenFilter v) {
			this._kind = Kind.Multiplexer;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> multiplexer(
				Function<MultiplexerTokenFilter.Builder, ObjectBuilder<MultiplexerTokenFilter>> fn) {
			return this.multiplexer(fn.apply(new MultiplexerTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> ngram(NGramTokenFilter v) {
			this._kind = Kind.Ngram;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> ngram(
				Function<NGramTokenFilter.Builder, ObjectBuilder<NGramTokenFilter>> fn) {
			return this.ngram(fn.apply(new NGramTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> noriPartOfSpeech(NoriPartOfSpeechTokenFilter v) {
			this._kind = Kind.NoriPartOfSpeech;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> noriPartOfSpeech(
				Function<NoriPartOfSpeechTokenFilter.Builder, ObjectBuilder<NoriPartOfSpeechTokenFilter>> fn) {
			return this.noriPartOfSpeech(fn.apply(new NoriPartOfSpeechTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> patternCapture(PatternCaptureTokenFilter v) {
			this._kind = Kind.PatternCapture;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> patternCapture(
				Function<PatternCaptureTokenFilter.Builder, ObjectBuilder<PatternCaptureTokenFilter>> fn) {
			return this.patternCapture(fn.apply(new PatternCaptureTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> patternReplace(PatternReplaceTokenFilter v) {
			this._kind = Kind.PatternReplace;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> patternReplace(
				Function<PatternReplaceTokenFilter.Builder, ObjectBuilder<PatternReplaceTokenFilter>> fn) {
			return this.patternReplace(fn.apply(new PatternReplaceTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> phonetic(PhoneticTokenFilter v) {
			this._kind = Kind.Phonetic;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> phonetic(
				Function<PhoneticTokenFilter.Builder, ObjectBuilder<PhoneticTokenFilter>> fn) {
			return this.phonetic(fn.apply(new PhoneticTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> porterStem(PorterStemTokenFilter v) {
			this._kind = Kind.PorterStem;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> porterStem(
				Function<PorterStemTokenFilter.Builder, ObjectBuilder<PorterStemTokenFilter>> fn) {
			return this.porterStem(fn.apply(new PorterStemTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> predicateTokenFilter(PredicateTokenFilter v) {
			this._kind = Kind.PredicateTokenFilter;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> predicateTokenFilter(
				Function<PredicateTokenFilter.Builder, ObjectBuilder<PredicateTokenFilter>> fn) {
			return this.predicateTokenFilter(fn.apply(new PredicateTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> removeDuplicates(RemoveDuplicatesTokenFilter v) {
			this._kind = Kind.RemoveDuplicates;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> removeDuplicates(
				Function<RemoveDuplicatesTokenFilter.Builder, ObjectBuilder<RemoveDuplicatesTokenFilter>> fn) {
			return this.removeDuplicates(fn.apply(new RemoveDuplicatesTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> reverse(ReverseTokenFilter v) {
			this._kind = Kind.Reverse;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> reverse(
				Function<ReverseTokenFilter.Builder, ObjectBuilder<ReverseTokenFilter>> fn) {
			return this.reverse(fn.apply(new ReverseTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> shingle(ShingleTokenFilter v) {
			this._kind = Kind.Shingle;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> shingle(
				Function<ShingleTokenFilter.Builder, ObjectBuilder<ShingleTokenFilter>> fn) {
			return this.shingle(fn.apply(new ShingleTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> snowball(SnowballTokenFilter v) {
			this._kind = Kind.Snowball;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> snowball(
				Function<SnowballTokenFilter.Builder, ObjectBuilder<SnowballTokenFilter>> fn) {
			return this.snowball(fn.apply(new SnowballTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> stemmerOverride(StemmerOverrideTokenFilter v) {
			this._kind = Kind.StemmerOverride;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> stemmerOverride(
				Function<StemmerOverrideTokenFilter.Builder, ObjectBuilder<StemmerOverrideTokenFilter>> fn) {
			return this.stemmerOverride(fn.apply(new StemmerOverrideTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> stemmer(StemmerTokenFilter v) {
			this._kind = Kind.Stemmer;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> stemmer(
				Function<StemmerTokenFilter.Builder, ObjectBuilder<StemmerTokenFilter>> fn) {
			return this.stemmer(fn.apply(new StemmerTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> stop(StopTokenFilter v) {
			this._kind = Kind.Stop;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> stop(
				Function<StopTokenFilter.Builder, ObjectBuilder<StopTokenFilter>> fn) {
			return this.stop(fn.apply(new StopTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> synonymGraph(SynonymGraphTokenFilter v) {
			this._kind = Kind.SynonymGraph;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> synonymGraph(
				Function<SynonymGraphTokenFilter.Builder, ObjectBuilder<SynonymGraphTokenFilter>> fn) {
			return this.synonymGraph(fn.apply(new SynonymGraphTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> synonym(SynonymTokenFilter v) {
			this._kind = Kind.Synonym;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> synonym(
				Function<SynonymTokenFilter.Builder, ObjectBuilder<SynonymTokenFilter>> fn) {
			return this.synonym(fn.apply(new SynonymTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> trim(TrimTokenFilter v) {
			this._kind = Kind.Trim;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> trim(
				Function<TrimTokenFilter.Builder, ObjectBuilder<TrimTokenFilter>> fn) {
			return this.trim(fn.apply(new TrimTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> truncate(TruncateTokenFilter v) {
			this._kind = Kind.Truncate;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> truncate(
				Function<TruncateTokenFilter.Builder, ObjectBuilder<TruncateTokenFilter>> fn) {
			return this.truncate(fn.apply(new TruncateTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> unique(UniqueTokenFilter v) {
			this._kind = Kind.Unique;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> unique(
				Function<UniqueTokenFilter.Builder, ObjectBuilder<UniqueTokenFilter>> fn) {
			return this.unique(fn.apply(new UniqueTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> uppercase(UppercaseTokenFilter v) {
			this._kind = Kind.Uppercase;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> uppercase(
				Function<UppercaseTokenFilter.Builder, ObjectBuilder<UppercaseTokenFilter>> fn) {
			return this.uppercase(fn.apply(new UppercaseTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> wordDelimiterGraph(WordDelimiterGraphTokenFilter v) {
			this._kind = Kind.WordDelimiterGraph;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> wordDelimiterGraph(
				Function<WordDelimiterGraphTokenFilter.Builder, ObjectBuilder<WordDelimiterGraphTokenFilter>> fn) {
			return this.wordDelimiterGraph(fn.apply(new WordDelimiterGraphTokenFilter.Builder()).build());
		}

		public ObjectBuilder<TokenFilterDefinition> wordDelimiter(WordDelimiterTokenFilter v) {
			this._kind = Kind.WordDelimiter;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TokenFilterDefinition> wordDelimiter(
				Function<WordDelimiterTokenFilter.Builder, ObjectBuilder<WordDelimiterTokenFilter>> fn) {
			return this.wordDelimiter(fn.apply(new WordDelimiterTokenFilter.Builder()).build());
		}

		public TokenFilterDefinition build() {
			_checkSingleUse();
			return new TokenFilterDefinition(this);
		}

	}

	protected static void setupTokenFilterDefinitionDeserializer(ObjectDeserializer<Builder> op) {

		op.add(Builder::asciifolding, AsciiFoldingTokenFilter._DESERIALIZER, "asciifolding");
		op.add(Builder::commonGrams, CommonGramsTokenFilter._DESERIALIZER, "common_grams");
		op.add(Builder::condition, ConditionTokenFilter._DESERIALIZER, "condition");
		op.add(Builder::delimitedPayload, DelimitedPayloadTokenFilter._DESERIALIZER, "delimited_payload");
		op.add(Builder::dictionaryDecompounder, DictionaryDecompounderTokenFilter._DESERIALIZER,
				"dictionary_decompounder");
		op.add(Builder::edgeNgram, EdgeNGramTokenFilter._DESERIALIZER, "edge_ngram");
		op.add(Builder::elision, ElisionTokenFilter._DESERIALIZER, "elision");
		op.add(Builder::fingerprint, FingerprintTokenFilter._DESERIALIZER, "fingerprint");
		op.add(Builder::hunspell, HunspellTokenFilter._DESERIALIZER, "hunspell");
		op.add(Builder::hyphenationDecompounder, HyphenationDecompounderTokenFilter._DESERIALIZER,
				"hyphenation_decompounder");
		op.add(Builder::icuCollation, IcuCollationTokenFilter._DESERIALIZER, "icu_collation");
		op.add(Builder::icuFolding, IcuFoldingTokenFilter._DESERIALIZER, "icu_folding");
		op.add(Builder::icuNormalizer, IcuNormalizationTokenFilter._DESERIALIZER, "icu_normalizer");
		op.add(Builder::icuTokenizer, IcuTokenizer._DESERIALIZER, "icu_tokenizer");
		op.add(Builder::icuTransform, IcuTransformTokenFilter._DESERIALIZER, "icu_transform");
		op.add(Builder::kstem, KStemTokenFilter._DESERIALIZER, "kstem");
		op.add(Builder::keepTypes, KeepTypesTokenFilter._DESERIALIZER, "keep_types");
		op.add(Builder::keep, KeepWordsTokenFilter._DESERIALIZER, "keep");
		op.add(Builder::keywordMarker, KeywordMarkerTokenFilter._DESERIALIZER, "keyword_marker");
		op.add(Builder::kuromojiPartOfSpeech, KuromojiPartOfSpeechTokenFilter._DESERIALIZER, "kuromoji_part_of_speech");
		op.add(Builder::kuromojiReadingform, KuromojiReadingFormTokenFilter._DESERIALIZER, "kuromoji_readingform");
		op.add(Builder::kuromojiStemmer, KuromojiStemmerTokenFilter._DESERIALIZER, "kuromoji_stemmer");
		op.add(Builder::length, LengthTokenFilter._DESERIALIZER, "length");
		op.add(Builder::limit, LimitTokenCountTokenFilter._DESERIALIZER, "limit");
		op.add(Builder::lowercase, LowercaseTokenFilter._DESERIALIZER, "lowercase");
		op.add(Builder::multiplexer, MultiplexerTokenFilter._DESERIALIZER, "multiplexer");
		op.add(Builder::ngram, NGramTokenFilter._DESERIALIZER, "ngram");
		op.add(Builder::noriPartOfSpeech, NoriPartOfSpeechTokenFilter._DESERIALIZER, "nori_part_of_speech");
		op.add(Builder::patternCapture, PatternCaptureTokenFilter._DESERIALIZER, "pattern_capture");
		op.add(Builder::patternReplace, PatternReplaceTokenFilter._DESERIALIZER, "pattern_replace");
		op.add(Builder::phonetic, PhoneticTokenFilter._DESERIALIZER, "phonetic");
		op.add(Builder::porterStem, PorterStemTokenFilter._DESERIALIZER, "porter_stem");
		op.add(Builder::predicateTokenFilter, PredicateTokenFilter._DESERIALIZER, "predicate_token_filter");
		op.add(Builder::removeDuplicates, RemoveDuplicatesTokenFilter._DESERIALIZER, "remove_duplicates");
		op.add(Builder::reverse, ReverseTokenFilter._DESERIALIZER, "reverse");
		op.add(Builder::shingle, ShingleTokenFilter._DESERIALIZER, "shingle");
		op.add(Builder::snowball, SnowballTokenFilter._DESERIALIZER, "snowball");
		op.add(Builder::stemmerOverride, StemmerOverrideTokenFilter._DESERIALIZER, "stemmer_override");
		op.add(Builder::stemmer, StemmerTokenFilter._DESERIALIZER, "stemmer");
		op.add(Builder::stop, StopTokenFilter._DESERIALIZER, "stop");
		op.add(Builder::synonymGraph, SynonymGraphTokenFilter._DESERIALIZER, "synonym_graph");
		op.add(Builder::synonym, SynonymTokenFilter._DESERIALIZER, "synonym");
		op.add(Builder::trim, TrimTokenFilter._DESERIALIZER, "trim");
		op.add(Builder::truncate, TruncateTokenFilter._DESERIALIZER, "truncate");
		op.add(Builder::unique, UniqueTokenFilter._DESERIALIZER, "unique");
		op.add(Builder::uppercase, UppercaseTokenFilter._DESERIALIZER, "uppercase");
		op.add(Builder::wordDelimiterGraph, WordDelimiterGraphTokenFilter._DESERIALIZER, "word_delimiter_graph");
		op.add(Builder::wordDelimiter, WordDelimiterTokenFilter._DESERIALIZER, "word_delimiter");

		op.setTypeProperty("type", null);

	}

	public static final JsonpDeserializer<TokenFilterDefinition> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, TokenFilterDefinition::setupTokenFilterDefinitionDeserializer, Builder::build);
}
