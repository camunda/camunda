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

import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.UnionDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;

// typedef: _types.query_dsl.TermsQueryField


@JsonpDeserializable
public class TermsQueryField implements TaggedUnion<TermsQueryField.Kind, Object>, JsonpSerializable {

	public enum Kind {
		Value, Lookup

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

	private TermsQueryField(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	private TermsQueryField(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static TermsQueryField of(Function<Builder, ObjectBuilder<TermsQueryField>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code value}?
	 */
	public boolean isValue() {
		return _kind == Kind.Value;
	}

	/**
	 * Get the {@code value} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code value} kind.
	 */
	public List<FieldValue> value() {
		return TaggedUnionUtils.get(this, Kind.Value);
	}

	/**
	 * Is this variant instance of kind {@code lookup}?
	 */
	public boolean isLookup() {
		return _kind == Kind.Lookup;
	}

	/**
	 * Get the {@code lookup} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code lookup} kind.
	 */
	public TermsLookup lookup() {
		return TaggedUnionUtils.get(this, Kind.Lookup);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		} else {
			switch (_kind) {
				case Value :
					generator.writeStartArray();
					for (FieldValue item0 : ((List<FieldValue>) this._value)) {
						item0.serialize(generator, mapper);

					}
					generator.writeEnd();

					break;
			}
		}

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<TermsQueryField> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<TermsQueryField> value(List<FieldValue> v) {
			this._kind = Kind.Value;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TermsQueryField> lookup(TermsLookup v) {
			this._kind = Kind.Lookup;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TermsQueryField> lookup(Function<TermsLookup.Builder, ObjectBuilder<TermsLookup>> fn) {
			return this.lookup(fn.apply(new TermsLookup.Builder()).build());
		}

		public TermsQueryField build() {
			_checkSingleUse();
			return new TermsQueryField(this);
		}

	}

	private static JsonpDeserializer<TermsQueryField> buildTermsQueryFieldDeserializer() {
		return new UnionDeserializer.Builder<TermsQueryField, Kind, Object>(TermsQueryField::new, false)
				.addMember(Kind.Value, JsonpDeserializer.arrayDeserializer(FieldValue._DESERIALIZER))
				.addMember(Kind.Lookup, TermsLookup._DESERIALIZER).build();
	}

	public static final JsonpDeserializer<TermsQueryField> _DESERIALIZER = JsonpDeserializer
			.lazy(TermsQueryField::buildTermsQueryFieldDeserializer);
}
