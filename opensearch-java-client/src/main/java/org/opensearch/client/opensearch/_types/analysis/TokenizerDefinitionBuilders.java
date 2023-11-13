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
 * Builders for {@link TokenizerDefinition} variants.
 */
public class TokenizerDefinitionBuilders {
	private TokenizerDefinitionBuilders() {
	}

	/**
	 * Creates a builder for the {@link CharGroupTokenizer char_group}
	 * {@code TokenizerDefinition} variant.
	 */
	public static CharGroupTokenizer.Builder charGroup() {
		return new CharGroupTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link EdgeNGramTokenizer edge_ngram}
	 * {@code TokenizerDefinition} variant.
	 */
	public static EdgeNGramTokenizer.Builder edgeNgram() {
		return new EdgeNGramTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link IcuTokenizer icu_tokenizer}
	 * {@code TokenizerDefinition} variant.
	 */
	public static IcuTokenizer.Builder icuTokenizer() {
		return new IcuTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link KeywordTokenizer keyword}
	 * {@code TokenizerDefinition} variant.
	 */
	public static KeywordTokenizer.Builder keyword() {
		return new KeywordTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link KuromojiTokenizer kuromoji_tokenizer}
	 * {@code TokenizerDefinition} variant.
	 */
	public static KuromojiTokenizer.Builder kuromojiTokenizer() {
		return new KuromojiTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link LetterTokenizer letter}
	 * {@code TokenizerDefinition} variant.
	 */
	public static LetterTokenizer.Builder letter() {
		return new LetterTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link LowercaseTokenizer lowercase}
	 * {@code TokenizerDefinition} variant.
	 */
	public static LowercaseTokenizer.Builder lowercase() {
		return new LowercaseTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link NGramTokenizer ngram}
	 * {@code TokenizerDefinition} variant.
	 */
	public static NGramTokenizer.Builder ngram() {
		return new NGramTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link NoriTokenizer nori_tokenizer}
	 * {@code TokenizerDefinition} variant.
	 */
	public static NoriTokenizer.Builder noriTokenizer() {
		return new NoriTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link PathHierarchyTokenizer path_hierarchy}
	 * {@code TokenizerDefinition} variant.
	 */
	public static PathHierarchyTokenizer.Builder pathHierarchy() {
		return new PathHierarchyTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link PatternTokenizer pattern}
	 * {@code TokenizerDefinition} variant.
	 */
	public static PatternTokenizer.Builder pattern() {
		return new PatternTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link StandardTokenizer standard}
	 * {@code TokenizerDefinition} variant.
	 */
	public static StandardTokenizer.Builder standard() {
		return new StandardTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link UaxEmailUrlTokenizer uax_url_email}
	 * {@code TokenizerDefinition} variant.
	 */
	public static UaxEmailUrlTokenizer.Builder uaxUrlEmail() {
		return new UaxEmailUrlTokenizer.Builder();
	}

	/**
	 * Creates a builder for the {@link WhitespaceTokenizer whitespace}
	 * {@code TokenizerDefinition} variant.
	 */
	public static WhitespaceTokenizer.Builder whitespace() {
		return new WhitespaceTokenizer.Builder();
	}

}
