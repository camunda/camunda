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
import org.opensearch.client.json.UnionDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: _global.search._types.SuggestOption



public class SuggestOption<TDocument> implements TaggedUnion<SuggestOption.Kind, Object>, JsonpSerializable {

	public enum Kind {
		Completion, Phrase, Term

	}

	private final Kind _kind;
	private final Object _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final Object _get() {
		return _value;
	}

	private final JsonpSerializer<TDocument> tDocumentSerializer = null;

	private SuggestOption(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	private SuggestOption(Builder<TDocument> builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static <TDocument> SuggestOption<TDocument> of(
			Function<Builder<TDocument>, ObjectBuilder<SuggestOption<TDocument>>> fn) {
		return fn.apply(new Builder<>()).build();
	}

	/**
	 * Is this variant instance of kind {@code completion}?
	 */
	public boolean isCompletion() {
		return _kind == Kind.Completion;
	}

	/**
	 * Get the {@code completion} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code completion} kind.
	 */
	public CompletionSuggestOption<TDocument> completion() {
		return TaggedUnionUtils.get(this, Kind.Completion);
	}

	/**
	 * Is this variant instance of kind {@code phrase}?
	 */
	public boolean isPhrase() {
		return _kind == Kind.Phrase;
	}

	/**
	 * Get the {@code phrase} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code phrase} kind.
	 */
	public PhraseSuggestOption phrase() {
		return TaggedUnionUtils.get(this, Kind.Phrase);
	}

	/**
	 * Is this variant instance of kind {@code term}?
	 */
	public boolean isTerm() {
		return _kind == Kind.Term;
	}

	/**
	 * Get the {@code term} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code term} kind.
	 */
	public TermSuggestOption term() {
		return TaggedUnionUtils.get(this, Kind.Term);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		}

	}

	public static class Builder<TDocument> extends ObjectBuilderBase
			implements
				ObjectBuilder<SuggestOption<TDocument>> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<SuggestOption<TDocument>> completion(CompletionSuggestOption<TDocument> v) {
			this._kind = Kind.Completion;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SuggestOption<TDocument>> completion(
				Function<CompletionSuggestOption.Builder<TDocument>,
						ObjectBuilder<CompletionSuggestOption<TDocument>>> fn) {
			return this.completion(fn.apply(new CompletionSuggestOption.Builder<TDocument>()).build());
		}

		public ObjectBuilder<SuggestOption<TDocument>> phrase(PhraseSuggestOption v) {
			this._kind = Kind.Phrase;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SuggestOption<TDocument>> phrase(
				Function<PhraseSuggestOption.Builder, ObjectBuilder<PhraseSuggestOption>> fn) {
			return this.phrase(fn.apply(new PhraseSuggestOption.Builder()).build());
		}

		public ObjectBuilder<SuggestOption<TDocument>> term(TermSuggestOption v) {
			this._kind = Kind.Term;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SuggestOption<TDocument>> term(
				Function<TermSuggestOption.Builder, ObjectBuilder<TermSuggestOption>> fn) {
			return this.term(fn.apply(new TermSuggestOption.Builder()).build());
		}

		public SuggestOption<TDocument> build() {
			_checkSingleUse();
			return new SuggestOption<>(this);
		}

	}

	public static <TDocument> JsonpDeserializer<SuggestOption<TDocument>> createSuggestOptionDeserializer(
			JsonpDeserializer<TDocument> tDocumentDeserializer) {
		return new UnionDeserializer.Builder<SuggestOption<TDocument>, Kind, Object>(SuggestOption<TDocument>::new,
				false).addMember(Kind.Completion,
						CompletionSuggestOption.createCompletionSuggestOptionDeserializer(tDocumentDeserializer))
						.addMember(Kind.Phrase, PhraseSuggestOption._DESERIALIZER)
						.addMember(Kind.Term, TermSuggestOption._DESERIALIZER).build();
	}

}
