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
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.termvectors.Term


@JsonpDeserializable
public class Term implements JsonpSerializable {
	@Nullable
	private final Integer docFreq;

	@Nullable
	private final Double score;

	private final int termFreq;

	private final List<Token> tokens;

	@Nullable
	private final Integer ttf;

	// ---------------------------------------------------------------------------------------------

	private Term(Builder builder) {

		this.docFreq = builder.docFreq;
		this.score = builder.score;
		this.termFreq = ApiTypeHelper.requireNonNull(builder.termFreq, this, "termFreq");
		this.tokens = ApiTypeHelper.unmodifiableRequired(builder.tokens, this, "tokens");
		this.ttf = builder.ttf;

	}

	public static Term of(Function<Builder, ObjectBuilder<Term>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code doc_freq}
	 */
	@Nullable
	public final Integer docFreq() {
		return this.docFreq;
	}

	/**
	 * API name: {@code score}
	 */
	@Nullable
	public final Double score() {
		return this.score;
	}

	/**
	 * Required - API name: {@code term_freq}
	 */
	public final int termFreq() {
		return this.termFreq;
	}

	/**
	 * Required - API name: {@code tokens}
	 */
	public final List<Token> tokens() {
		return this.tokens;
	}

	/**
	 * API name: {@code ttf}
	 */
	@Nullable
	public final Integer ttf() {
		return this.ttf;
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

		if (this.docFreq != null) {
			generator.writeKey("doc_freq");
			generator.write(this.docFreq);

		}
		if (this.score != null) {
			generator.writeKey("score");
			generator.write(this.score);

		}
		generator.writeKey("term_freq");
		generator.write(this.termFreq);

		if (ApiTypeHelper.isDefined(this.tokens)) {
			generator.writeKey("tokens");
			generator.writeStartArray();
			for (Token item0 : this.tokens) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.ttf != null) {
			generator.writeKey("ttf");
			generator.write(this.ttf);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Term}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Term> {
		@Nullable
		private Integer docFreq;

		@Nullable
		private Double score;

		private Integer termFreq;

		private List<Token> tokens;

		@Nullable
		private Integer ttf;

		/**
		 * API name: {@code doc_freq}
		 */
		public final Builder docFreq(@Nullable Integer value) {
			this.docFreq = value;
			return this;
		}

		/**
		 * API name: {@code score}
		 */
		public final Builder score(@Nullable Double value) {
			this.score = value;
			return this;
		}

		/**
		 * Required - API name: {@code term_freq}
		 */
		public final Builder termFreq(int value) {
			this.termFreq = value;
			return this;
		}

		/**
		 * Required - API name: {@code tokens}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>tokens</code>.
		 */
		public final Builder tokens(List<Token> list) {
			this.tokens = _listAddAll(this.tokens, list);
			return this;
		}

		/**
		 * Required - API name: {@code tokens}
		 * <p>
		 * Adds one or more values to <code>tokens</code>.
		 */
		public final Builder tokens(Token value, Token... values) {
			this.tokens = _listAdd(this.tokens, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code tokens}
		 * <p>
		 * Adds a value to <code>tokens</code> using a builder lambda.
		 */
		public final Builder tokens(Function<Token.Builder, ObjectBuilder<Token>> fn) {
			return tokens(fn.apply(new Token.Builder()).build());
		}

		/**
		 * API name: {@code ttf}
		 */
		public final Builder ttf(@Nullable Integer value) {
			this.ttf = value;
			return this;
		}

		/**
		 * Builds a {@link Term}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Term build() {
			_checkSingleUse();

			return new Term(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Term}
	 */
	public static final JsonpDeserializer<Term> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Term::setupTermDeserializer);

	protected static void setupTermDeserializer(ObjectDeserializer<Term.Builder> op) {

		op.add(Builder::docFreq, JsonpDeserializer.integerDeserializer(), "doc_freq");
		op.add(Builder::score, JsonpDeserializer.doubleDeserializer(), "score");
		op.add(Builder::termFreq, JsonpDeserializer.integerDeserializer(), "term_freq");
		op.add(Builder::tokens, JsonpDeserializer.arrayDeserializer(Token._DESERIALIZER), "tokens");
		op.add(Builder::ttf, JsonpDeserializer.integerDeserializer(), "ttf");

	}

}
