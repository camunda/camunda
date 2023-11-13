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

package org.opensearch.client.opensearch._types.query_dsl;

import org.opensearch.client.json.JsonEnum;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: _types.query_dsl.SpanQuery


@JsonpDeserializable
public class SpanQuery implements TaggedUnion<SpanQuery.Kind, Object>, JsonpSerializable {

	/**
	 * {@link SpanQuery} variant kinds.
	 */
	/**
	 * {@link SpanQuery} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		SpanContaining("span_containing"),

		FieldMaskingSpan("field_masking_span"),

		SpanFirst("span_first"),

		SpanGap("span_gap"),

		SpanMulti("span_multi"),

		SpanNear("span_near"),

		SpanNot("span_not"),

		SpanOr("span_or"),

		SpanTerm("span_term"),

		SpanWithin("span_within"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

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

	public SpanQuery(SpanQueryVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._spanQueryKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

	}

	private SpanQuery(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static SpanQuery of(Function<Builder, ObjectBuilder<SpanQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code span_containing}?
	 */
	public boolean isSpanContaining() {
		return _kind == Kind.SpanContaining;
	}

	/**
	 * Get the {@code span_containing} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_containing}
	 *             kind.
	 */
	public SpanContainingQuery spanContaining() {
		return TaggedUnionUtils.get(this, Kind.SpanContaining);
	}

	/**
	 * Is this variant instance of kind {@code field_masking_span}?
	 */
	public boolean isFieldMaskingSpan() {
		return _kind == Kind.FieldMaskingSpan;
	}

	/**
	 * Get the {@code field_masking_span} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code field_masking_span}
	 *             kind.
	 */
	public SpanFieldMaskingQuery fieldMaskingSpan() {
		return TaggedUnionUtils.get(this, Kind.FieldMaskingSpan);
	}

	/**
	 * Is this variant instance of kind {@code span_first}?
	 */
	public boolean isSpanFirst() {
		return _kind == Kind.SpanFirst;
	}

	/**
	 * Get the {@code span_first} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_first} kind.
	 */
	public SpanFirstQuery spanFirst() {
		return TaggedUnionUtils.get(this, Kind.SpanFirst);
	}

	/**
	 * Is this variant instance of kind {@code span_gap}?
	 */
	public boolean isSpanGap() {
		return _kind == Kind.SpanGap;
	}

	/**
	 * Get the {@code span_gap} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_gap} kind.
	 */
	public SpanGapQuery spanGap() {
		return TaggedUnionUtils.get(this, Kind.SpanGap);
	}

	/**
	 * Is this variant instance of kind {@code span_multi}?
	 */
	public boolean isSpanMulti() {
		return _kind == Kind.SpanMulti;
	}

	/**
	 * Get the {@code span_multi} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_multi} kind.
	 */
	public SpanMultiTermQuery spanMulti() {
		return TaggedUnionUtils.get(this, Kind.SpanMulti);
	}

	/**
	 * Is this variant instance of kind {@code span_near}?
	 */
	public boolean isSpanNear() {
		return _kind == Kind.SpanNear;
	}

	/**
	 * Get the {@code span_near} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_near} kind.
	 */
	public SpanNearQuery spanNear() {
		return TaggedUnionUtils.get(this, Kind.SpanNear);
	}

	/**
	 * Is this variant instance of kind {@code span_not}?
	 */
	public boolean isSpanNot() {
		return _kind == Kind.SpanNot;
	}

	/**
	 * Get the {@code span_not} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_not} kind.
	 */
	public SpanNotQuery spanNot() {
		return TaggedUnionUtils.get(this, Kind.SpanNot);
	}

	/**
	 * Is this variant instance of kind {@code span_or}?
	 */
	public boolean isSpanOr() {
		return _kind == Kind.SpanOr;
	}

	/**
	 * Get the {@code span_or} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_or} kind.
	 */
	public SpanOrQuery spanOr() {
		return TaggedUnionUtils.get(this, Kind.SpanOr);
	}

	/**
	 * Is this variant instance of kind {@code span_term}?
	 */
	public boolean isSpanTerm() {
		return _kind == Kind.SpanTerm;
	}

	/**
	 * Get the {@code span_term} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_term} kind.
	 */
	public SpanTermQuery spanTerm() {
		return TaggedUnionUtils.get(this, Kind.SpanTerm);
	}

	/**
	 * Is this variant instance of kind {@code span_within}?
	 */
	public boolean isSpanWithin() {
		return _kind == Kind.SpanWithin;
	}

	/**
	 * Get the {@code span_within} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code span_within} kind.
	 */
	public SpanWithinQuery spanWithin() {
		return TaggedUnionUtils.get(this, Kind.SpanWithin);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeStartObject();

		generator.writeKey(_kind.jsonValue());
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		}

		generator.writeEnd();

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<SpanQuery> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<SpanQuery> spanContaining(SpanContainingQuery v) {
			this._kind = Kind.SpanContaining;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanContaining(
				Function<SpanContainingQuery.Builder, ObjectBuilder<SpanContainingQuery>> fn) {
			return this.spanContaining(fn.apply(new SpanContainingQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> fieldMaskingSpan(SpanFieldMaskingQuery v) {
			this._kind = Kind.FieldMaskingSpan;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> fieldMaskingSpan(
				Function<SpanFieldMaskingQuery.Builder, ObjectBuilder<SpanFieldMaskingQuery>> fn) {
			return this.fieldMaskingSpan(fn.apply(new SpanFieldMaskingQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> spanFirst(SpanFirstQuery v) {
			this._kind = Kind.SpanFirst;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanFirst(Function<SpanFirstQuery.Builder, ObjectBuilder<SpanFirstQuery>> fn) {
			return this.spanFirst(fn.apply(new SpanFirstQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> spanGap(SpanGapQuery v) {
			this._kind = Kind.SpanGap;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanGap(Function<SpanGapQuery.Builder, ObjectBuilder<SpanGapQuery>> fn) {
			return this.spanGap(fn.apply(new SpanGapQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> spanMulti(SpanMultiTermQuery v) {
			this._kind = Kind.SpanMulti;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanMulti(
				Function<SpanMultiTermQuery.Builder, ObjectBuilder<SpanMultiTermQuery>> fn) {
			return this.spanMulti(fn.apply(new SpanMultiTermQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> spanNear(SpanNearQuery v) {
			this._kind = Kind.SpanNear;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanNear(Function<SpanNearQuery.Builder, ObjectBuilder<SpanNearQuery>> fn) {
			return this.spanNear(fn.apply(new SpanNearQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> spanNot(SpanNotQuery v) {
			this._kind = Kind.SpanNot;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanNot(Function<SpanNotQuery.Builder, ObjectBuilder<SpanNotQuery>> fn) {
			return this.spanNot(fn.apply(new SpanNotQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> spanOr(SpanOrQuery v) {
			this._kind = Kind.SpanOr;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanOr(Function<SpanOrQuery.Builder, ObjectBuilder<SpanOrQuery>> fn) {
			return this.spanOr(fn.apply(new SpanOrQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> spanTerm(SpanTermQuery v) {
			this._kind = Kind.SpanTerm;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanTerm(Function<SpanTermQuery.Builder, ObjectBuilder<SpanTermQuery>> fn) {
			return this.spanTerm(fn.apply(new SpanTermQuery.Builder()).build());
		}

		public ObjectBuilder<SpanQuery> spanWithin(SpanWithinQuery v) {
			this._kind = Kind.SpanWithin;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SpanQuery> spanWithin(
				Function<SpanWithinQuery.Builder, ObjectBuilder<SpanWithinQuery>> fn) {
			return this.spanWithin(fn.apply(new SpanWithinQuery.Builder()).build());
		}

		public SpanQuery build() {
			_checkSingleUse();
			return new SpanQuery(this);
		}

	}

	protected static void setupSpanQueryDeserializer(ObjectDeserializer<Builder> op) {

		op.add(Builder::spanContaining, SpanContainingQuery._DESERIALIZER, "span_containing");
		op.add(Builder::fieldMaskingSpan, SpanFieldMaskingQuery._DESERIALIZER, "field_masking_span");
		op.add(Builder::spanFirst, SpanFirstQuery._DESERIALIZER, "span_first");
		op.add(Builder::spanGap, SpanGapQuery._DESERIALIZER, "span_gap");
		op.add(Builder::spanMulti, SpanMultiTermQuery._DESERIALIZER, "span_multi");
		op.add(Builder::spanNear, SpanNearQuery._DESERIALIZER, "span_near");
		op.add(Builder::spanNot, SpanNotQuery._DESERIALIZER, "span_not");
		op.add(Builder::spanOr, SpanOrQuery._DESERIALIZER, "span_or");
		op.add(Builder::spanTerm, SpanTermQuery._DESERIALIZER, "span_term");
		op.add(Builder::spanWithin, SpanWithinQuery._DESERIALIZER, "span_within");

	}

	public static final JsonpDeserializer<SpanQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			SpanQuery::setupSpanQueryDeserializer, Builder::build);
}
