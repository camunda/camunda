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

// typedef: _types.analysis.KuromojiIterationMarkCharFilter


@JsonpDeserializable
public class KuromojiIterationMarkCharFilter extends CharFilterBase implements CharFilterDefinitionVariant {
	private final boolean normalizeKana;

	private final boolean normalizeKanji;

	// ---------------------------------------------------------------------------------------------

	private KuromojiIterationMarkCharFilter(Builder builder) {
		super(builder);

		this.normalizeKana = ApiTypeHelper.requireNonNull(builder.normalizeKana, this, "normalizeKana");
		this.normalizeKanji = ApiTypeHelper.requireNonNull(builder.normalizeKanji, this, "normalizeKanji");

	}

	public static KuromojiIterationMarkCharFilter of(
			Function<Builder, ObjectBuilder<KuromojiIterationMarkCharFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * CharFilterDefinition variant kind.
	 */
	@Override
	public CharFilterDefinition.Kind _charFilterDefinitionKind() {
		return CharFilterDefinition.Kind.KuromojiIterationMark;
	}

	/**
	 * Required - API name: {@code normalize_kana}
	 */
	public final boolean normalizeKana() {
		return this.normalizeKana;
	}

	/**
	 * Required - API name: {@code normalize_kanji}
	 */
	public final boolean normalizeKanji() {
		return this.normalizeKanji;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "kuromoji_iteration_mark");
		super.serializeInternal(generator, mapper);
		generator.writeKey("normalize_kana");
		generator.write(this.normalizeKana);

		generator.writeKey("normalize_kanji");
		generator.write(this.normalizeKanji);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link KuromojiIterationMarkCharFilter}.
	 */

	public static class Builder extends CharFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<KuromojiIterationMarkCharFilter> {
		private Boolean normalizeKana;

		private Boolean normalizeKanji;

		/**
		 * Required - API name: {@code normalize_kana}
		 */
		public final Builder normalizeKana(boolean value) {
			this.normalizeKana = value;
			return this;
		}

		/**
		 * Required - API name: {@code normalize_kanji}
		 */
		public final Builder normalizeKanji(boolean value) {
			this.normalizeKanji = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link KuromojiIterationMarkCharFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public KuromojiIterationMarkCharFilter build() {
			_checkSingleUse();

			return new KuromojiIterationMarkCharFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link KuromojiIterationMarkCharFilter}
	 */
	public static final JsonpDeserializer<KuromojiIterationMarkCharFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, KuromojiIterationMarkCharFilter::setupKuromojiIterationMarkCharFilterDeserializer);

	protected static void setupKuromojiIterationMarkCharFilterDeserializer(
			ObjectDeserializer<KuromojiIterationMarkCharFilter.Builder> op) {
		CharFilterBase.setupCharFilterBaseDeserializer(op);
		op.add(Builder::normalizeKana, JsonpDeserializer.booleanDeserializer(), "normalize_kana");
		op.add(Builder::normalizeKanji, JsonpDeserializer.booleanDeserializer(), "normalize_kanji");

		op.ignore("type");
	}

}
