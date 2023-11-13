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

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import javax.annotation.Nullable;

// typedef: _types.analysis.CompoundWordTokenFilterBase


public abstract class CompoundWordTokenFilterBase extends TokenFilterBase {
	@Nullable
	private final String hyphenationPatternsPath;

	@Nullable
	private final Integer maxSubwordSize;

	@Nullable
	private final Integer minSubwordSize;

	@Nullable
	private final Integer minWordSize;

	@Nullable
	private final Boolean onlyLongestMatch;

	private final List<String> wordList;

	@Nullable
	private final String wordListPath;

	// ---------------------------------------------------------------------------------------------

	protected CompoundWordTokenFilterBase(AbstractBuilder<?> builder) {
		super(builder);

		this.hyphenationPatternsPath = builder.hyphenationPatternsPath;
		this.maxSubwordSize = builder.maxSubwordSize;
		this.minSubwordSize = builder.minSubwordSize;
		this.minWordSize = builder.minWordSize;
		this.onlyLongestMatch = builder.onlyLongestMatch;
		this.wordList = ApiTypeHelper.unmodifiable(builder.wordList);
		this.wordListPath = builder.wordListPath;

	}

	/**
	 * API name: {@code hyphenation_patterns_path}
	 */
	@Nullable
	public final String hyphenationPatternsPath() {
		return this.hyphenationPatternsPath;
	}

	/**
	 * API name: {@code max_subword_size}
	 */
	@Nullable
	public final Integer maxSubwordSize() {
		return this.maxSubwordSize;
	}

	/**
	 * API name: {@code min_subword_size}
	 */
	@Nullable
	public final Integer minSubwordSize() {
		return this.minSubwordSize;
	}

	/**
	 * API name: {@code min_word_size}
	 */
	@Nullable
	public final Integer minWordSize() {
		return this.minWordSize;
	}

	/**
	 * API name: {@code only_longest_match}
	 */
	@Nullable
	public final Boolean onlyLongestMatch() {
		return this.onlyLongestMatch;
	}

	/**
	 * API name: {@code word_list}
	 */
	public final List<String> wordList() {
		return this.wordList;
	}

	/**
	 * API name: {@code word_list_path}
	 */
	@Nullable
	public final String wordListPath() {
		return this.wordListPath;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.hyphenationPatternsPath != null) {
			generator.writeKey("hyphenation_patterns_path");
			generator.write(this.hyphenationPatternsPath);

		}
		if (this.maxSubwordSize != null) {
			generator.writeKey("max_subword_size");
			generator.write(this.maxSubwordSize);

		}
		if (this.minSubwordSize != null) {
			generator.writeKey("min_subword_size");
			generator.write(this.minSubwordSize);

		}
		if (this.minWordSize != null) {
			generator.writeKey("min_word_size");
			generator.write(this.minWordSize);

		}
		if (this.onlyLongestMatch != null) {
			generator.writeKey("only_longest_match");
			generator.write(this.onlyLongestMatch);

		}
		if (ApiTypeHelper.isDefined(this.wordList)) {
			generator.writeKey("word_list");
			generator.writeStartArray();
			for (String item0 : this.wordList) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.wordListPath != null) {
			generator.writeKey("word_list_path");
			generator.write(this.wordListPath);

		}

	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				TokenFilterBase.AbstractBuilder<BuilderT> {
		@Nullable
		private String hyphenationPatternsPath;

		@Nullable
		private Integer maxSubwordSize;

		@Nullable
		private Integer minSubwordSize;

		@Nullable
		private Integer minWordSize;

		@Nullable
		private Boolean onlyLongestMatch;

		@Nullable
		private List<String> wordList;

		@Nullable
		private String wordListPath;

		/**
		 * API name: {@code hyphenation_patterns_path}
		 */
		public final BuilderT hyphenationPatternsPath(@Nullable String value) {
			this.hyphenationPatternsPath = value;
			return self();
		}

		/**
		 * API name: {@code max_subword_size}
		 */
		public final BuilderT maxSubwordSize(@Nullable Integer value) {
			this.maxSubwordSize = value;
			return self();
		}

		/**
		 * API name: {@code min_subword_size}
		 */
		public final BuilderT minSubwordSize(@Nullable Integer value) {
			this.minSubwordSize = value;
			return self();
		}

		/**
		 * API name: {@code min_word_size}
		 */
		public final BuilderT minWordSize(@Nullable Integer value) {
			this.minWordSize = value;
			return self();
		}

		/**
		 * API name: {@code only_longest_match}
		 */
		public final BuilderT onlyLongestMatch(@Nullable Boolean value) {
			this.onlyLongestMatch = value;
			return self();
		}

		/**
		 * API name: {@code word_list}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>wordList</code>.
		 */
		public final BuilderT wordList(List<String> list) {
			this.wordList = _listAddAll(this.wordList, list);
			return self();
		}

		/**
		 * API name: {@code word_list}
		 * <p>
		 * Adds one or more values to <code>wordList</code>.
		 */
		public final BuilderT wordList(String value, String... values) {
			this.wordList = _listAdd(this.wordList, value, values);
			return self();
		}

		/**
		 * API name: {@code word_list_path}
		 */
		public final BuilderT wordListPath(@Nullable String value) {
			this.wordListPath = value;
			return self();
		}

	}

	// ---------------------------------------------------------------------------------------------
	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupCompoundWordTokenFilterBaseDeserializer(
			ObjectDeserializer<BuilderT> op) {
		TokenFilterBase.setupTokenFilterBaseDeserializer(op);
		op.add(AbstractBuilder::hyphenationPatternsPath, JsonpDeserializer.stringDeserializer(),
				"hyphenation_patterns_path");
		op.add(AbstractBuilder::maxSubwordSize, JsonpDeserializer.integerDeserializer(), "max_subword_size");
		op.add(AbstractBuilder::minSubwordSize, JsonpDeserializer.integerDeserializer(), "min_subword_size");
		op.add(AbstractBuilder::minWordSize, JsonpDeserializer.integerDeserializer(), "min_word_size");
		op.add(AbstractBuilder::onlyLongestMatch, JsonpDeserializer.booleanDeserializer(), "only_longest_match");
		op.add(AbstractBuilder::wordList, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"word_list");
		op.add(AbstractBuilder::wordListPath, JsonpDeserializer.stringDeserializer(), "word_list_path");

	}

}
