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

import org.opensearch.client.opensearch._types.SuggestMode;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.search._types.DirectGenerator


@JsonpDeserializable
public class DirectGenerator implements JsonpSerializable {
	private final String field;

	@Nullable
	private final Integer maxEdits;

	@Nullable
	private final Float maxInspections;

	@Nullable
	private final Float maxTermFreq;

	@Nullable
	private final Float minDocFreq;

	@Nullable
	private final Integer minWordLength;

	@Nullable
	private final String postFilter;

	@Nullable
	private final String preFilter;

	@Nullable
	private final Integer prefixLength;

	@Nullable
	private final Integer size;

	@Nullable
	private final SuggestMode suggestMode;

	// ---------------------------------------------------------------------------------------------

	private DirectGenerator(Builder builder) {

		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");
		this.maxEdits = builder.maxEdits;
		this.maxInspections = builder.maxInspections;
		this.maxTermFreq = builder.maxTermFreq;
		this.minDocFreq = builder.minDocFreq;
		this.minWordLength = builder.minWordLength;
		this.postFilter = builder.postFilter;
		this.preFilter = builder.preFilter;
		this.prefixLength = builder.prefixLength;
		this.size = builder.size;
		this.suggestMode = builder.suggestMode;

	}

	public static DirectGenerator of(Function<Builder, ObjectBuilder<DirectGenerator>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code field}
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code max_edits}
	 */
	@Nullable
	public final Integer maxEdits() {
		return this.maxEdits;
	}

	/**
	 * API name: {@code max_inspections}
	 */
	@Nullable
	public final Float maxInspections() {
		return this.maxInspections;
	}

	/**
	 * API name: {@code max_term_freq}
	 */
	@Nullable
	public final Float maxTermFreq() {
		return this.maxTermFreq;
	}

	/**
	 * API name: {@code min_doc_freq}
	 */
	@Nullable
	public final Float minDocFreq() {
		return this.minDocFreq;
	}

	/**
	 * API name: {@code min_word_length}
	 */
	@Nullable
	public final Integer minWordLength() {
		return this.minWordLength;
	}

	/**
	 * API name: {@code post_filter}
	 */
	@Nullable
	public final String postFilter() {
		return this.postFilter;
	}

	/**
	 * API name: {@code pre_filter}
	 */
	@Nullable
	public final String preFilter() {
		return this.preFilter;
	}

	/**
	 * API name: {@code prefix_length}
	 */
	@Nullable
	public final Integer prefixLength() {
		return this.prefixLength;
	}

	/**
	 * API name: {@code size}
	 */
	@Nullable
	public final Integer size() {
		return this.size;
	}

	/**
	 * API name: {@code suggest_mode}
	 */
	@Nullable
	public final SuggestMode suggestMode() {
		return this.suggestMode;
	}

	/**
	 * Serialize this object to JSON.
	 */
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		serializeInternal(generator, mapper);
		generator.writeEnd();
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeKey("field");
		generator.write(this.field);

		if (this.maxEdits != null) {
			generator.writeKey("max_edits");
			generator.write(this.maxEdits);

		}
		if (this.maxInspections != null) {
			generator.writeKey("max_inspections");
			generator.write(this.maxInspections);

		}
		if (this.maxTermFreq != null) {
			generator.writeKey("max_term_freq");
			generator.write(this.maxTermFreq);

		}
		if (this.minDocFreq != null) {
			generator.writeKey("min_doc_freq");
			generator.write(this.minDocFreq);

		}
		if (this.minWordLength != null) {
			generator.writeKey("min_word_length");
			generator.write(this.minWordLength);

		}
		if (this.postFilter != null) {
			generator.writeKey("post_filter");
			generator.write(this.postFilter);

		}
		if (this.preFilter != null) {
			generator.writeKey("pre_filter");
			generator.write(this.preFilter);

		}
		if (this.prefixLength != null) {
			generator.writeKey("prefix_length");
			generator.write(this.prefixLength);

		}
		if (this.size != null) {
			generator.writeKey("size");
			generator.write(this.size);

		}
		if (this.suggestMode != null) {
			generator.writeKey("suggest_mode");
			this.suggestMode.serialize(generator, mapper);
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DirectGenerator}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DirectGenerator> {
		private String field;

		@Nullable
		private Integer maxEdits;

		@Nullable
		private Float maxInspections;

		@Nullable
		private Float maxTermFreq;

		@Nullable
		private Float minDocFreq;

		@Nullable
		private Integer minWordLength;

		@Nullable
		private String postFilter;

		@Nullable
		private String preFilter;

		@Nullable
		private Integer prefixLength;

		@Nullable
		private Integer size;

		@Nullable
		private SuggestMode suggestMode;

		/**
		 * Required - API name: {@code field}
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		/**
		 * API name: {@code max_edits}
		 */
		public final Builder maxEdits(@Nullable Integer value) {
			this.maxEdits = value;
			return this;
		}

		/**
		 * API name: {@code max_inspections}
		 */
		public final Builder maxInspections(@Nullable Float value) {
			this.maxInspections = value;
			return this;
		}

		/**
		 * API name: {@code max_term_freq}
		 */
		public final Builder maxTermFreq(@Nullable Float value) {
			this.maxTermFreq = value;
			return this;
		}

		/**
		 * API name: {@code min_doc_freq}
		 */
		public final Builder minDocFreq(@Nullable Float value) {
			this.minDocFreq = value;
			return this;
		}

		/**
		 * API name: {@code min_word_length}
		 */
		public final Builder minWordLength(@Nullable Integer value) {
			this.minWordLength = value;
			return this;
		}

		/**
		 * API name: {@code post_filter}
		 */
		public final Builder postFilter(@Nullable String value) {
			this.postFilter = value;
			return this;
		}

		/**
		 * API name: {@code pre_filter}
		 */
		public final Builder preFilter(@Nullable String value) {
			this.preFilter = value;
			return this;
		}

		/**
		 * API name: {@code prefix_length}
		 */
		public final Builder prefixLength(@Nullable Integer value) {
			this.prefixLength = value;
			return this;
		}

		/**
		 * API name: {@code size}
		 */
		public final Builder size(@Nullable Integer value) {
			this.size = value;
			return this;
		}

		/**
		 * API name: {@code suggest_mode}
		 */
		public final Builder suggestMode(@Nullable SuggestMode value) {
			this.suggestMode = value;
			return this;
		}

		/**
		 * Builds a {@link DirectGenerator}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DirectGenerator build() {
			_checkSingleUse();

			return new DirectGenerator(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link DirectGenerator}
	 */
	public static final JsonpDeserializer<DirectGenerator> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			DirectGenerator::setupDirectGeneratorDeserializer);

	protected static void setupDirectGeneratorDeserializer(ObjectDeserializer<DirectGenerator.Builder> op) {

		op.add(Builder::field, JsonpDeserializer.stringDeserializer(), "field");
		op.add(Builder::maxEdits, JsonpDeserializer.integerDeserializer(), "max_edits");
		op.add(Builder::maxInspections, JsonpDeserializer.floatDeserializer(), "max_inspections");
		op.add(Builder::maxTermFreq, JsonpDeserializer.floatDeserializer(), "max_term_freq");
		op.add(Builder::minDocFreq, JsonpDeserializer.floatDeserializer(), "min_doc_freq");
		op.add(Builder::minWordLength, JsonpDeserializer.integerDeserializer(), "min_word_length");
		op.add(Builder::postFilter, JsonpDeserializer.stringDeserializer(), "post_filter");
		op.add(Builder::preFilter, JsonpDeserializer.stringDeserializer(), "pre_filter");
		op.add(Builder::prefixLength, JsonpDeserializer.integerDeserializer(), "prefix_length");
		op.add(Builder::size, JsonpDeserializer.integerDeserializer(), "size");
		op.add(Builder::suggestMode, SuggestMode._DESERIALIZER, "suggest_mode");

	}

}
