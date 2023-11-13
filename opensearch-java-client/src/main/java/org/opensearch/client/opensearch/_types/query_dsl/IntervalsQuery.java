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
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: _types.query_dsl.IntervalsQuery


@JsonpDeserializable
public class IntervalsQuery extends QueryBase
		implements
			TaggedUnion<IntervalsQuery.Kind, Object>,
			QueryVariant,
			JsonpSerializable {

	/**
	 * {@link IntervalsQuery} variant kinds.
	 */
	/**
	 * {@link IntervalsQuery} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		AllOf("all_of"),

		AnyOf("any_of"),

		Fuzzy("fuzzy"),

		Match("match"),

		Prefix("prefix"),

		Wildcard("wildcard"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.Intervals;
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

	// Single key dictionary
	private final String field;

	private IntervalsQuery(Builder builder) {
		super(builder);
		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static IntervalsQuery of(Function<Builder, ObjectBuilder<IntervalsQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - The target field
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * Is this variant instance of kind {@code all_of}?
	 */
	public boolean isAllOf() {
		return _kind == Kind.AllOf;
	}

	/**
	 * Get the {@code all_of} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code all_of} kind.
	 */
	public IntervalsAllOf allOf() {
		return TaggedUnionUtils.get(this, Kind.AllOf);
	}

	/**
	 * Is this variant instance of kind {@code any_of}?
	 */
	public boolean isAnyOf() {
		return _kind == Kind.AnyOf;
	}

	/**
	 * Get the {@code any_of} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code any_of} kind.
	 */
	public IntervalsAnyOf anyOf() {
		return TaggedUnionUtils.get(this, Kind.AnyOf);
	}

	/**
	 * Is this variant instance of kind {@code fuzzy}?
	 */
	public boolean isFuzzy() {
		return _kind == Kind.Fuzzy;
	}

	/**
	 * Get the {@code fuzzy} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code fuzzy} kind.
	 */
	public IntervalsFuzzy fuzzy() {
		return TaggedUnionUtils.get(this, Kind.Fuzzy);
	}

	/**
	 * Is this variant instance of kind {@code match}?
	 */
	public boolean isMatch() {
		return _kind == Kind.Match;
	}

	/**
	 * Get the {@code match} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code match} kind.
	 */
	public IntervalsMatch match() {
		return TaggedUnionUtils.get(this, Kind.Match);
	}

	/**
	 * Is this variant instance of kind {@code prefix}?
	 */
	public boolean isPrefix() {
		return _kind == Kind.Prefix;
	}

	/**
	 * Get the {@code prefix} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code prefix} kind.
	 */
	public IntervalsPrefix prefix() {
		return TaggedUnionUtils.get(this, Kind.Prefix);
	}

	/**
	 * Is this variant instance of kind {@code wildcard}?
	 */
	public boolean isWildcard() {
		return _kind == Kind.Wildcard;
	}

	/**
	 * Get the {@code wildcard} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code wildcard} kind.
	 */
	public IntervalsWildcard wildcard() {
		return TaggedUnionUtils.get(this, Kind.Wildcard);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeStartObject();
		generator.writeStartObject(this.field);

		super.serializeInternal(generator, mapper);

		generator.writeKey(_kind.jsonValue());
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		}

		generator.writeEnd();

		generator.writeEnd();

	}

	public static class Builder extends QueryBase.AbstractBuilder<Builder> implements ObjectBuilder<IntervalsQuery> {
		private Kind _kind;
		private Object _value;

		private String field;

		/**
		 * Required - The target field
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}
		public ObjectBuilder<IntervalsQuery> allOf(IntervalsAllOf v) {
			this._kind = Kind.AllOf;
			this._value = v;
			return this;
		}

		public ObjectBuilder<IntervalsQuery> allOf(Function<IntervalsAllOf.Builder, ObjectBuilder<IntervalsAllOf>> fn) {
			return this.allOf(fn.apply(new IntervalsAllOf.Builder()).build());
		}

		public ObjectBuilder<IntervalsQuery> anyOf(IntervalsAnyOf v) {
			this._kind = Kind.AnyOf;
			this._value = v;
			return this;
		}

		public ObjectBuilder<IntervalsQuery> anyOf(Function<IntervalsAnyOf.Builder, ObjectBuilder<IntervalsAnyOf>> fn) {
			return this.anyOf(fn.apply(new IntervalsAnyOf.Builder()).build());
		}

		public ObjectBuilder<IntervalsQuery> fuzzy(IntervalsFuzzy v) {
			this._kind = Kind.Fuzzy;
			this._value = v;
			return this;
		}

		public ObjectBuilder<IntervalsQuery> fuzzy(Function<IntervalsFuzzy.Builder, ObjectBuilder<IntervalsFuzzy>> fn) {
			return this.fuzzy(fn.apply(new IntervalsFuzzy.Builder()).build());
		}

		public ObjectBuilder<IntervalsQuery> match(IntervalsMatch v) {
			this._kind = Kind.Match;
			this._value = v;
			return this;
		}

		public ObjectBuilder<IntervalsQuery> match(Function<IntervalsMatch.Builder, ObjectBuilder<IntervalsMatch>> fn) {
			return this.match(fn.apply(new IntervalsMatch.Builder()).build());
		}

		public ObjectBuilder<IntervalsQuery> prefix(IntervalsPrefix v) {
			this._kind = Kind.Prefix;
			this._value = v;
			return this;
		}

		public ObjectBuilder<IntervalsQuery> prefix(
				Function<IntervalsPrefix.Builder, ObjectBuilder<IntervalsPrefix>> fn) {
			return this.prefix(fn.apply(new IntervalsPrefix.Builder()).build());
		}

		public ObjectBuilder<IntervalsQuery> wildcard(IntervalsWildcard v) {
			this._kind = Kind.Wildcard;
			this._value = v;
			return this;
		}

		public ObjectBuilder<IntervalsQuery> wildcard(
				Function<IntervalsWildcard.Builder, ObjectBuilder<IntervalsWildcard>> fn) {
			return this.wildcard(fn.apply(new IntervalsWildcard.Builder()).build());
		}

		public IntervalsQuery build() {
			_checkSingleUse();
			return new IntervalsQuery(this);
		}

	}

	protected static void setupIntervalsQueryDeserializer(ObjectDeserializer<Builder> op) {
		QueryBase.setupQueryBaseDeserializer(op);
		op.add(Builder::allOf, IntervalsAllOf._DESERIALIZER, "all_of");
		op.add(Builder::anyOf, IntervalsAnyOf._DESERIALIZER, "any_of");
		op.add(Builder::fuzzy, IntervalsFuzzy._DESERIALIZER, "fuzzy");
		op.add(Builder::match, IntervalsMatch._DESERIALIZER, "match");
		op.add(Builder::prefix, IntervalsPrefix._DESERIALIZER, "prefix");
		op.add(Builder::wildcard, IntervalsWildcard._DESERIALIZER, "wildcard");

		op.setKey(Builder::field, JsonpDeserializer.stringDeserializer());

	}

	public static final JsonpDeserializer<IntervalsQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IntervalsQuery::setupIntervalsQueryDeserializer, Builder::build);
}
