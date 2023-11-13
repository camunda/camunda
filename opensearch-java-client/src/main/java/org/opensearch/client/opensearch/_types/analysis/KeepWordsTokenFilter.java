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

// typedef: _types.analysis.KeepWordsTokenFilter


@JsonpDeserializable
public class KeepWordsTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	private final List<String> keepWords;

	@Nullable
	private final Boolean keepWordsCase;

	@Nullable
	private final String keepWordsPath;

	// ---------------------------------------------------------------------------------------------

	private KeepWordsTokenFilter(Builder builder) {
		super(builder);

		this.keepWords = ApiTypeHelper.unmodifiable(builder.keepWords);
		this.keepWordsCase = builder.keepWordsCase;
		this.keepWordsPath = builder.keepWordsPath;

	}

	public static KeepWordsTokenFilter of(Function<Builder, ObjectBuilder<KeepWordsTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.Keep;
	}

	/**
	 * API name: {@code keep_words}
	 */
	public final List<String> keepWords() {
		return this.keepWords;
	}

	/**
	 * API name: {@code keep_words_case}
	 */
	@Nullable
	public final Boolean keepWordsCase() {
		return this.keepWordsCase;
	}

	/**
	 * API name: {@code keep_words_path}
	 */
	@Nullable
	public final String keepWordsPath() {
		return this.keepWordsPath;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "keep");
		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.keepWords)) {
			generator.writeKey("keep_words");
			generator.writeStartArray();
			for (String item0 : this.keepWords) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.keepWordsCase != null) {
			generator.writeKey("keep_words_case");
			generator.write(this.keepWordsCase);

		}
		if (this.keepWordsPath != null) {
			generator.writeKey("keep_words_path");
			generator.write(this.keepWordsPath);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link KeepWordsTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<KeepWordsTokenFilter> {
		@Nullable
		private List<String> keepWords;

		@Nullable
		private Boolean keepWordsCase;

		@Nullable
		private String keepWordsPath;

		/**
		 * API name: {@code keep_words}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>keepWords</code>.
		 */
		public final Builder keepWords(List<String> list) {
			this.keepWords = _listAddAll(this.keepWords, list);
			return this;
		}

		/**
		 * API name: {@code keep_words}
		 * <p>
		 * Adds one or more values to <code>keepWords</code>.
		 */
		public final Builder keepWords(String value, String... values) {
			this.keepWords = _listAdd(this.keepWords, value, values);
			return this;
		}

		/**
		 * API name: {@code keep_words_case}
		 */
		public final Builder keepWordsCase(@Nullable Boolean value) {
			this.keepWordsCase = value;
			return this;
		}

		/**
		 * API name: {@code keep_words_path}
		 */
		public final Builder keepWordsPath(@Nullable String value) {
			this.keepWordsPath = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link KeepWordsTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public KeepWordsTokenFilter build() {
			_checkSingleUse();

			return new KeepWordsTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link KeepWordsTokenFilter}
	 */
	public static final JsonpDeserializer<KeepWordsTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, KeepWordsTokenFilter::setupKeepWordsTokenFilterDeserializer);

	protected static void setupKeepWordsTokenFilterDeserializer(ObjectDeserializer<KeepWordsTokenFilter.Builder> op) {
		setupTokenFilterBaseDeserializer(op);
		op.add(Builder::keepWords, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"keep_words");
		op.add(Builder::keepWordsCase, JsonpDeserializer.booleanDeserializer(), "keep_words_case");
		op.add(Builder::keepWordsPath, JsonpDeserializer.stringDeserializer(), "keep_words_path");

		op.ignore("type");
	}

}
