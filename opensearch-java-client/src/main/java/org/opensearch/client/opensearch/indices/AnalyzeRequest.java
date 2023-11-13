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

package org.opensearch.client.opensearch.indices;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.analysis.CharFilter;
import org.opensearch.client.opensearch._types.analysis.TokenFilter;
import org.opensearch.client.opensearch._types.analysis.Tokenizer;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: indices.analyze.Request

/**
 * Performs the analysis process on a text and return the tokens breakdown of
 * the text.
 * 
 */
@JsonpDeserializable
public class AnalyzeRequest extends RequestBase implements JsonpSerializable {
	@Nullable
	private final String analyzer;

	private final List<String> attributes;

	private final List<CharFilter> charFilter;

	@Nullable
	private final Boolean explain;

	@Nullable
	private final String field;

	private final List<TokenFilter> filter;

	@Nullable
	private final String index;

	@Nullable
	private final String normalizer;

	private final List<String> text;

	@Nullable
	private final Tokenizer tokenizer;

	// ---------------------------------------------------------------------------------------------

	private AnalyzeRequest(Builder builder) {

		this.analyzer = builder.analyzer;
		this.attributes = ApiTypeHelper.unmodifiable(builder.attributes);
		this.charFilter = ApiTypeHelper.unmodifiable(builder.charFilter);
		this.explain = builder.explain;
		this.field = builder.field;
		this.filter = ApiTypeHelper.unmodifiable(builder.filter);
		this.index = builder.index;
		this.normalizer = builder.normalizer;
		this.text = ApiTypeHelper.unmodifiable(builder.text);
		this.tokenizer = builder.tokenizer;

	}

	public static AnalyzeRequest of(Function<Builder, ObjectBuilder<AnalyzeRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code analyzer}
	 */
	@Nullable
	public final String analyzer() {
		return this.analyzer;
	}

	/**
	 * API name: {@code attributes}
	 */
	public final List<String> attributes() {
		return this.attributes;
	}

	/**
	 * API name: {@code char_filter}
	 */
	public final List<CharFilter> charFilter() {
		return this.charFilter;
	}

	/**
	 * API name: {@code explain}
	 */
	@Nullable
	public final Boolean explain() {
		return this.explain;
	}

	/**
	 * API name: {@code field}
	 */
	@Nullable
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code filter}
	 */
	public final List<TokenFilter> filter() {
		return this.filter;
	}

	/**
	 * The name of the index to scope the operation
	 * <p>
	 * API name: {@code index}
	 */
	@Nullable
	public final String index() {
		return this.index;
	}

	/**
	 * API name: {@code normalizer}
	 */
	@Nullable
	public final String normalizer() {
		return this.normalizer;
	}

	/**
	 * API name: {@code text}
	 */
	public final List<String> text() {
		return this.text;
	}

	/**
	 * API name: {@code tokenizer}
	 */
	@Nullable
	public final Tokenizer tokenizer() {
		return this.tokenizer;
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

		if (this.analyzer != null) {
			generator.writeKey("analyzer");
			generator.write(this.analyzer);

		}
		if (ApiTypeHelper.isDefined(this.attributes)) {
			generator.writeKey("attributes");
			generator.writeStartArray();
			for (String item0 : this.attributes) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.charFilter)) {
			generator.writeKey("char_filter");
			generator.writeStartArray();
			for (CharFilter item0 : this.charFilter) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.explain != null) {
			generator.writeKey("explain");
			generator.write(this.explain);

		}
		if (this.field != null) {
			generator.writeKey("field");
			generator.write(this.field);

		}
		if (ApiTypeHelper.isDefined(this.filter)) {
			generator.writeKey("filter");
			generator.writeStartArray();
			for (TokenFilter item0 : this.filter) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.normalizer != null) {
			generator.writeKey("normalizer");
			generator.write(this.normalizer);

		}
		if (ApiTypeHelper.isDefined(this.text)) {
			generator.writeKey("text");
			generator.writeStartArray();
			for (String item0 : this.text) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.tokenizer != null) {
			generator.writeKey("tokenizer");
			this.tokenizer.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link AnalyzeRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<AnalyzeRequest> {
		@Nullable
		private String analyzer;

		@Nullable
		private List<String> attributes;

		@Nullable
		private List<CharFilter> charFilter;

		@Nullable
		private Boolean explain;

		@Nullable
		private String field;

		@Nullable
		private List<TokenFilter> filter;

		@Nullable
		private String index;

		@Nullable
		private String normalizer;

		@Nullable
		private List<String> text;

		@Nullable
		private Tokenizer tokenizer;

		/**
		 * API name: {@code analyzer}
		 */
		public final Builder analyzer(@Nullable String value) {
			this.analyzer = value;
			return this;
		}

		/**
		 * API name: {@code attributes}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>attributes</code>.
		 */
		public final Builder attributes(List<String> list) {
			this.attributes = _listAddAll(this.attributes, list);
			return this;
		}

		/**
		 * API name: {@code attributes}
		 * <p>
		 * Adds one or more values to <code>attributes</code>.
		 */
		public final Builder attributes(String value, String... values) {
			this.attributes = _listAdd(this.attributes, value, values);
			return this;
		}

		/**
		 * API name: {@code char_filter}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>charFilter</code>.
		 */
		public final Builder charFilter(List<CharFilter> list) {
			this.charFilter = _listAddAll(this.charFilter, list);
			return this;
		}

		/**
		 * API name: {@code char_filter}
		 * <p>
		 * Adds one or more values to <code>charFilter</code>.
		 */
		public final Builder charFilter(CharFilter value, CharFilter... values) {
			this.charFilter = _listAdd(this.charFilter, value, values);
			return this;
		}

		/**
		 * API name: {@code char_filter}
		 * <p>
		 * Adds a value to <code>charFilter</code> using a builder lambda.
		 */
		public final Builder charFilter(Function<CharFilter.Builder, ObjectBuilder<CharFilter>> fn) {
			return charFilter(fn.apply(new CharFilter.Builder()).build());
		}

		/**
		 * API name: {@code explain}
		 */
		public final Builder explain(@Nullable Boolean value) {
			this.explain = value;
			return this;
		}

		/**
		 * API name: {@code field}
		 */
		public final Builder field(@Nullable String value) {
			this.field = value;
			return this;
		}

		/**
		 * API name: {@code filter}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>filter</code>.
		 */
		public final Builder filter(List<TokenFilter> list) {
			this.filter = _listAddAll(this.filter, list);
			return this;
		}

		/**
		 * API name: {@code filter}
		 * <p>
		 * Adds one or more values to <code>filter</code>.
		 */
		public final Builder filter(TokenFilter value, TokenFilter... values) {
			this.filter = _listAdd(this.filter, value, values);
			return this;
		}

		/**
		 * API name: {@code filter}
		 * <p>
		 * Adds a value to <code>filter</code> using a builder lambda.
		 */
		public final Builder filter(Function<TokenFilter.Builder, ObjectBuilder<TokenFilter>> fn) {
			return filter(fn.apply(new TokenFilter.Builder()).build());
		}

		/**
		 * The name of the index to scope the operation
		 * <p>
		 * API name: {@code index}
		 */
		public final Builder index(@Nullable String value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code normalizer}
		 */
		public final Builder normalizer(@Nullable String value) {
			this.normalizer = value;
			return this;
		}

		/**
		 * API name: {@code text}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>text</code>.
		 */
		public final Builder text(List<String> list) {
			this.text = _listAddAll(this.text, list);
			return this;
		}

		/**
		 * API name: {@code text}
		 * <p>
		 * Adds one or more values to <code>text</code>.
		 */
		public final Builder text(String value, String... values) {
			this.text = _listAdd(this.text, value, values);
			return this;
		}

		/**
		 * API name: {@code tokenizer}
		 */
		public final Builder tokenizer(@Nullable Tokenizer value) {
			this.tokenizer = value;
			return this;
		}

		/**
		 * API name: {@code tokenizer}
		 */
		public final Builder tokenizer(Function<Tokenizer.Builder, ObjectBuilder<Tokenizer>> fn) {
			return this.tokenizer(fn.apply(new Tokenizer.Builder()).build());
		}

		/**
		 * Builds a {@link AnalyzeRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public AnalyzeRequest build() {
			_checkSingleUse();

			return new AnalyzeRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link AnalyzeRequest}
	 */
	public static final JsonpDeserializer<AnalyzeRequest> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			AnalyzeRequest::setupAnalyzeRequestDeserializer);

	protected static void setupAnalyzeRequestDeserializer(ObjectDeserializer<AnalyzeRequest.Builder> op) {

		op.add(Builder::analyzer, JsonpDeserializer.stringDeserializer(), "analyzer");
		op.add(Builder::attributes, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"attributes");
		op.add(Builder::charFilter, JsonpDeserializer.arrayDeserializer(CharFilter._DESERIALIZER), "char_filter");
		op.add(Builder::explain, JsonpDeserializer.booleanDeserializer(), "explain");
		op.add(Builder::field, JsonpDeserializer.stringDeserializer(), "field");
		op.add(Builder::filter, JsonpDeserializer.arrayDeserializer(TokenFilter._DESERIALIZER), "filter");
		op.add(Builder::normalizer, JsonpDeserializer.stringDeserializer(), "normalizer");
		op.add(Builder::text, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "text");
		op.add(Builder::tokenizer, Tokenizer._DESERIALIZER, "tokenizer");

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code indices.analyze}".
	 */
	public static final Endpoint<AnalyzeRequest, AnalyzeResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "POST";

			},

			// Request path
			request -> {
				final int _index = 1 << 0;

				int propsSet = 0;

				if (request.index() != null)
					propsSet |= _index;

				if (propsSet == 0) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_analyze");
					return buf.toString();
				}
				if (propsSet == (_index)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/");
					SimpleEndpoint.pathEncode(request.index, buf);
					buf.append("/_analyze");
					return buf.toString();
				}
				throw SimpleEndpoint.noPathTemplateFound("path");

			},

			// Request parameters
			request -> {
				return Collections.emptyMap();

			}, SimpleEndpoint.emptyMap(), true, AnalyzeResponse._DESERIALIZER);
}
