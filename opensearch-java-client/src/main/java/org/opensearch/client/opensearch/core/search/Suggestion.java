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

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.JsonpSerializer;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

// typedef: _global.search._types.Suggest



public class Suggestion<T> implements JsonpSerializable {
	private final int length;

	private final int offset;

	private final List<SuggestOption<T>> options;

	private final String text;

	@Nullable
	private final JsonpSerializer<T> tSerializer;

	// ---------------------------------------------------------------------------------------------

	private Suggestion(Builder<T> builder) {

		this.length = ApiTypeHelper.requireNonNull(builder.length, this, "length");
		this.offset = ApiTypeHelper.requireNonNull(builder.offset, this, "offset");
		this.options = ApiTypeHelper.unmodifiableRequired(builder.options, this, "options");
		this.text = ApiTypeHelper.requireNonNull(builder.text, this, "text");
		this.tSerializer = builder.tSerializer;

	}

	public static <T> Suggestion<T> of(Function<Builder<T>, ObjectBuilder<Suggestion<T>>> fn) {
		return fn.apply(new Builder<>()).build();
	}

	/**
	 * Required - API name: {@code length}
	 */
	public final int length() {
		return this.length;
	}

	/**
	 * Required - API name: {@code offset}
	 */
	public final int offset() {
		return this.offset;
	}

	/**
	 * Required - API name: {@code options}
	 */
	public final List<SuggestOption<T>> options() {
		return this.options;
	}

	/**
	 * Required - API name: {@code text}
	 */
	public final String text() {
		return this.text;
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

		generator.writeKey("length");
		generator.write(this.length);

		generator.writeKey("offset");
		generator.write(this.offset);

		if (ApiTypeHelper.isDefined(this.options)) {
			generator.writeKey("options");
			generator.writeStartArray();
			for (SuggestOption<T> item0 : this.options) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("text");
		generator.write(this.text);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Suggestion}.
	 */

	public static class Builder<T> extends ObjectBuilderBase implements ObjectBuilder<Suggestion<T>> {
		private Integer length;

		private Integer offset;

		private List<SuggestOption<T>> options;

		private String text;

		@Nullable
		private JsonpSerializer<T> tSerializer;

		/**
		 * Required - API name: {@code length}
		 */
		public final Builder<T> length(int value) {
			this.length = value;
			return this;
		}

		/**
		 * Required - API name: {@code offset}
		 */
		public final Builder<T> offset(int value) {
			this.offset = value;
			return this;
		}

		/**
		 * Required - API name: {@code options}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>options</code>.
		 */
		public final Builder<T> options(List<SuggestOption<T>> list) {
			this.options = _listAddAll(this.options, list);
			return this;
		}

		/**
		 * Required - API name: {@code options}
		 * <p>
		 * Adds one or more values to <code>options</code>.
		 */
		public final Builder<T> options(SuggestOption<T> value, SuggestOption<T>... values) {
			this.options = _listAdd(this.options, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code options}
		 * <p>
		 * Adds a value to <code>options</code> using a builder lambda.
		 */
		public final Builder<T> options(Function<SuggestOption.Builder<T>, ObjectBuilder<SuggestOption<T>>> fn) {
			return options(fn.apply(new SuggestOption.Builder<T>()).build());
		}

		/**
		 * Required - API name: {@code text}
		 */
		public final Builder<T> text(String value) {
			this.text = value;
			return this;
		}

		/**
		 * Serializer for T. If not set, an attempt will be made to find a serializer
		 * from the JSON context.
		 */
		public final Builder<T> tSerializer(@Nullable JsonpSerializer<T> value) {
			this.tSerializer = value;
			return this;
		}

		/**
		 * Builds a {@link Suggestion}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Suggestion<T> build() {
			_checkSingleUse();

			return new Suggestion<T>(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Create a JSON deserializer for Suggestion
	 */
	public static <T> JsonpDeserializer<Suggestion<T>> createSuggestionDeserializer(
			JsonpDeserializer<T> tDeserializer) {
		return ObjectBuilderDeserializer.createForObject((Supplier<Builder<T>>) Builder::new,
				op -> Suggestion.setupSuggestionDeserializer(op, tDeserializer));
	};

	protected static <T> void setupSuggestionDeserializer(ObjectDeserializer<Suggestion.Builder<T>> op,
			JsonpDeserializer<T> tDeserializer) {

		op.add(Builder::length, JsonpDeserializer.integerDeserializer(), "length");
		op.add(Builder::offset, JsonpDeserializer.integerDeserializer(), "offset");
		op.add(Builder::options,
				JsonpDeserializer.arrayDeserializer(SuggestOption.createSuggestOptionDeserializer(tDeserializer)),
				"options");
		op.add(Builder::text, JsonpDeserializer.stringDeserializer(), "text");

	}

}
