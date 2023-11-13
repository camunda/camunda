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

package org.opensearch.client.opensearch.core.termvectors;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.termvectors.Filter

@JsonpDeserializable
public class Filter implements JsonpSerializable {
	@Nullable
	private final Integer maxDocFreq;

	@Nullable
	private final Integer maxNumTerms;

	@Nullable
	private final Integer maxTermFreq;

	@Nullable
	private final Integer maxWordLength;

	@Nullable
	private final Integer minDocFreq;

	@Nullable
	private final Integer minTermFreq;

	@Nullable
	private final Integer minWordLength;

	// ---------------------------------------------------------------------------------------------

	private Filter(Builder builder) {

		this.maxDocFreq = builder.maxDocFreq;
		this.maxNumTerms = builder.maxNumTerms;
		this.maxTermFreq = builder.maxTermFreq;
		this.maxWordLength = builder.maxWordLength;
		this.minDocFreq = builder.minDocFreq;
		this.minTermFreq = builder.minTermFreq;
		this.minWordLength = builder.minWordLength;

	}

	public static Filter of(Function<Builder, ObjectBuilder<Filter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code max_doc_freq}
	 */
	@Nullable
	public final Integer maxDocFreq() {
		return this.maxDocFreq;
	}

	/**
	 * API name: {@code max_num_terms}
	 */
	@Nullable
	public final Integer maxNumTerms() {
		return this.maxNumTerms;
	}

	/**
	 * API name: {@code max_term_freq}
	 */
	@Nullable
	public final Integer maxTermFreq() {
		return this.maxTermFreq;
	}

	/**
	 * API name: {@code max_word_length}
	 */
	@Nullable
	public final Integer maxWordLength() {
		return this.maxWordLength;
	}

	/**
	 * API name: {@code min_doc_freq}
	 */
	@Nullable
	public final Integer minDocFreq() {
		return this.minDocFreq;
	}

	/**
	 * API name: {@code min_term_freq}
	 */
	@Nullable
	public final Integer minTermFreq() {
		return this.minTermFreq;
	}

	/**
	 * API name: {@code min_word_length}
	 */
	@Nullable
	public final Integer minWordLength() {
		return this.minWordLength;
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

		if (this.maxDocFreq != null) {
			generator.writeKey("max_doc_freq");
			generator.write(this.maxDocFreq);

		}
		if (this.maxNumTerms != null) {
			generator.writeKey("max_num_terms");
			generator.write(this.maxNumTerms);

		}
		if (this.maxTermFreq != null) {
			generator.writeKey("max_term_freq");
			generator.write(this.maxTermFreq);

		}
		if (this.maxWordLength != null) {
			generator.writeKey("max_word_length");
			generator.write(this.maxWordLength);

		}
		if (this.minDocFreq != null) {
			generator.writeKey("min_doc_freq");
			generator.write(this.minDocFreq);

		}
		if (this.minTermFreq != null) {
			generator.writeKey("min_term_freq");
			generator.write(this.minTermFreq);

		}
		if (this.minWordLength != null) {
			generator.writeKey("min_word_length");
			generator.write(this.minWordLength);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Filter}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Filter> {
		@Nullable
		private Integer maxDocFreq;

		@Nullable
		private Integer maxNumTerms;

		@Nullable
		private Integer maxTermFreq;

		@Nullable
		private Integer maxWordLength;

		@Nullable
		private Integer minDocFreq;

		@Nullable
		private Integer minTermFreq;

		@Nullable
		private Integer minWordLength;

		/**
		 * API name: {@code max_doc_freq}
		 */
		public final Builder maxDocFreq(@Nullable Integer value) {
			this.maxDocFreq = value;
			return this;
		}

		/**
		 * API name: {@code max_num_terms}
		 */
		public final Builder maxNumTerms(@Nullable Integer value) {
			this.maxNumTerms = value;
			return this;
		}

		/**
		 * API name: {@code max_term_freq}
		 */
		public final Builder maxTermFreq(@Nullable Integer value) {
			this.maxTermFreq = value;
			return this;
		}

		/**
		 * API name: {@code max_word_length}
		 */
		public final Builder maxWordLength(@Nullable Integer value) {
			this.maxWordLength = value;
			return this;
		}

		/**
		 * API name: {@code min_doc_freq}
		 */
		public final Builder minDocFreq(@Nullable Integer value) {
			this.minDocFreq = value;
			return this;
		}

		/**
		 * API name: {@code min_term_freq}
		 */
		public final Builder minTermFreq(@Nullable Integer value) {
			this.minTermFreq = value;
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
		 * Builds a {@link Filter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Filter build() {
			_checkSingleUse();

			return new Filter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Filter}
	 */
	public static final JsonpDeserializer<Filter> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Filter::setupFilterDeserializer);

	protected static void setupFilterDeserializer(ObjectDeserializer<Filter.Builder> op) {

		op.add(Builder::maxDocFreq, JsonpDeserializer.integerDeserializer(), "max_doc_freq");
		op.add(Builder::maxNumTerms, JsonpDeserializer.integerDeserializer(), "max_num_terms");
		op.add(Builder::maxTermFreq, JsonpDeserializer.integerDeserializer(), "max_term_freq");
		op.add(Builder::maxWordLength, JsonpDeserializer.integerDeserializer(), "max_word_length");
		op.add(Builder::minDocFreq, JsonpDeserializer.integerDeserializer(), "min_doc_freq");
		op.add(Builder::minTermFreq, JsonpDeserializer.integerDeserializer(), "min_term_freq");
		op.add(Builder::minWordLength, JsonpDeserializer.integerDeserializer(), "min_word_length");

	}

}
