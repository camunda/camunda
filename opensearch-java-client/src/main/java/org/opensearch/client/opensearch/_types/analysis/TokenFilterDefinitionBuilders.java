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

/**
 * Builders for {@link TokenFilterDefinition} variants.
 */
public class TokenFilterDefinitionBuilders {
	private TokenFilterDefinitionBuilders() {
	}

	/**
	 * Creates a builder for the {@link AsciiFoldingTokenFilter asciifolding}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static AsciiFoldingTokenFilter.Builder asciifolding() {
		return new AsciiFoldingTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link CommonGramsTokenFilter common_grams}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static CommonGramsTokenFilter.Builder commonGrams() {
		return new CommonGramsTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link ConditionTokenFilter condition}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static ConditionTokenFilter.Builder condition() {
		return new ConditionTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link DelimitedPayloadTokenFilter
	 * delimited_payload} {@code TokenFilterDefinition} variant.
	 */
	public static DelimitedPayloadTokenFilter.Builder delimitedPayload() {
		return new DelimitedPayloadTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link DictionaryDecompounderTokenFilter
	 * dictionary_decompounder} {@code TokenFilterDefinition} variant.
	 */
	public static DictionaryDecompounderTokenFilter.Builder dictionaryDecompounder() {
		return new DictionaryDecompounderTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link EdgeNGramTokenFilter edge_ngram}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static EdgeNGramTokenFilter.Builder edgeNgram() {
		return new EdgeNGramTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link ElisionTokenFilter elision}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static ElisionTokenFilter.Builder elision() {
		return new ElisionTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link FingerprintTokenFilter fingerprint}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static FingerprintTokenFilter.Builder fingerprint() {
		return new FingerprintTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link HunspellTokenFilter hunspell}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static HunspellTokenFilter.Builder hunspell() {
		return new HunspellTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link HyphenationDecompounderTokenFilter
	 * hyphenation_decompounder} {@code TokenFilterDefinition} variant.
	 */
	public static HyphenationDecompounderTokenFilter.Builder hyphenationDecompounder() {
		return new HyphenationDecompounderTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link IcuCollationTokenFilter icu_collation}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static IcuCollationTokenFilter.Builder icuCollation() {
		return new IcuCollationTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link IcuFoldingTokenFilter icu_folding}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static IcuFoldingTokenFilter.Builder icuFolding() {
		return new IcuFoldingTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link IcuNormalizationTokenFilter icu_normalizer}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static IcuNormalizationTokenFilter.Builder icuNormalizer() {
		return new IcuNormalizationTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link IcuTokenizer icu_tokenizer}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static IcuTokenizer.Builder icuTokenizer() {
		return new IcuTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link IcuTransformTokenFilter icu_transform}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static IcuTransformTokenFilter.Builder icuTransform() {
		return new IcuTransformTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link KStemTokenFilter kstem}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static KStemTokenFilter.Builder kstem() {
		return new KStemTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link KeepTypesTokenFilter keep_types}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static KeepTypesTokenFilter.Builder keepTypes() {
		return new KeepTypesTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link KeepWordsTokenFilter keep}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static KeepWordsTokenFilter.Builder keep() {
		return new KeepWordsTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link KeywordMarkerTokenFilter keyword_marker}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static KeywordMarkerTokenFilter.Builder keywordMarker() {
		return new KeywordMarkerTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link KuromojiPartOfSpeechTokenFilter
	 * kuromoji_part_of_speech} {@code TokenFilterDefinition} variant.
	 */
	public static KuromojiPartOfSpeechTokenFilter.Builder kuromojiPartOfSpeech() {
		return new KuromojiPartOfSpeechTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link KuromojiReadingFormTokenFilter
	 * kuromoji_readingform} {@code TokenFilterDefinition} variant.
	 */
	public static KuromojiReadingFormTokenFilter.Builder kuromojiReadingform() {
		return new KuromojiReadingFormTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link KuromojiStemmerTokenFilter kuromoji_stemmer}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static KuromojiStemmerTokenFilter.Builder kuromojiStemmer() {
		return new KuromojiStemmerTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link LengthTokenFilter length}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static LengthTokenFilter.Builder length() {
		return new LengthTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link LimitTokenCountTokenFilter limit}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static LimitTokenCountTokenFilter.Builder limit() {
		return new LimitTokenCountTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link LowercaseTokenFilter lowercase}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static LowercaseTokenFilter.Builder lowercase() {
		return new LowercaseTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link MultiplexerTokenFilter multiplexer}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static MultiplexerTokenFilter.Builder multiplexer() {
		return new MultiplexerTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link NGramTokenFilter ngram}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static NGramTokenFilter.Builder ngram() {
		return new NGramTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link NoriPartOfSpeechTokenFilter
	 * nori_part_of_speech} {@code TokenFilterDefinition} variant.
	 */
	public static NoriPartOfSpeechTokenFilter.Builder noriPartOfSpeech() {
		return new NoriPartOfSpeechTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link PatternCaptureTokenFilter pattern_capture}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static PatternCaptureTokenFilter.Builder patternCapture() {
		return new PatternCaptureTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link PatternReplaceTokenFilter pattern_replace}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static PatternReplaceTokenFilter.Builder patternReplace() {
		return new PatternReplaceTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link PhoneticTokenFilter phonetic}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static PhoneticTokenFilter.Builder phonetic() {
		return new PhoneticTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link PorterStemTokenFilter porter_stem}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static PorterStemTokenFilter.Builder porterStem() {
		return new PorterStemTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link PredicateTokenFilter predicate_token_filter}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static PredicateTokenFilter.Builder predicateTokenFilter() {
		return new PredicateTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link RemoveDuplicatesTokenFilter
	 * remove_duplicates} {@code TokenFilterDefinition} variant.
	 */
	public static RemoveDuplicatesTokenFilter.Builder removeDuplicates() {
		return new RemoveDuplicatesTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link ReverseTokenFilter reverse}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static ReverseTokenFilter.Builder reverse() {
		return new ReverseTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link ShingleTokenFilter shingle}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static ShingleTokenFilter.Builder shingle() {
		return new ShingleTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link SnowballTokenFilter snowball}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static SnowballTokenFilter.Builder snowball() {
		return new SnowballTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link StemmerOverrideTokenFilter stemmer_override}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static StemmerOverrideTokenFilter.Builder stemmerOverride() {
		return new StemmerOverrideTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link StemmerTokenFilter stemmer}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static StemmerTokenFilter.Builder stemmer() {
		return new StemmerTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link StopTokenFilter stop}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static StopTokenFilter.Builder stop() {
		return new StopTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link SynonymGraphTokenFilter synonym_graph}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static SynonymGraphTokenFilter.Builder synonymGraph() {
		return new SynonymGraphTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link SynonymTokenFilter synonym}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static SynonymTokenFilter.Builder synonym() {
		return new SynonymTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link TrimTokenFilter trim}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static TrimTokenFilter.Builder trim() {
		return new TrimTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link TruncateTokenFilter truncate}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static TruncateTokenFilter.Builder truncate() {
		return new TruncateTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link UniqueTokenFilter unique}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static UniqueTokenFilter.Builder unique() {
		return new UniqueTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link UppercaseTokenFilter uppercase}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static UppercaseTokenFilter.Builder uppercase() {
		return new UppercaseTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link WordDelimiterGraphTokenFilter
	 * word_delimiter_graph} {@code TokenFilterDefinition} variant.
	 */
	public static WordDelimiterGraphTokenFilter.Builder wordDelimiterGraph() {
		return new WordDelimiterGraphTokenFilter.Builder();
	}

	/**
	 * Creates a builder for the {@link WordDelimiterTokenFilter word_delimiter}
	 * {@code TokenFilterDefinition} variant.
	 */
	public static WordDelimiterTokenFilter.Builder wordDelimiter() {
		return new WordDelimiterTokenFilter.Builder();
	}

}
