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
import java.util.function.Function;

// typedef: _types.query_dsl.SimpleQueryStringFlags

/**
 * Query flags can be either a single flag or a combination of flags, e.g.
 * <code>OR|AND|PREFIX</code>
 */
@JsonpDeserializable
public class SimpleQueryStringFlags implements TaggedUnion<SimpleQueryStringFlags.Kind, Object>, JsonpSerializable {

	public enum Kind {
		Single, Multiple

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

	private SimpleQueryStringFlags(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	public String _toJsonString() {
		switch (_kind) {
			case Single :
				return this.single().jsonValue();
			case Multiple :
				return this.multiple();

			default :
				throw new IllegalStateException("Unknown kind " + _kind);
		}
	}

	private SimpleQueryStringFlags(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static SimpleQueryStringFlags of(Function<Builder, ObjectBuilder<SimpleQueryStringFlags>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code single}?
	 */
	public boolean isSingle() {
		return _kind == Kind.Single;
	}

	/**
	 * Get the {@code single} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code single} kind.
	 */
	public SimpleQueryStringFlag single() {
		return TaggedUnionUtils.get(this, Kind.Single);
	}

	/**
	 * Is this variant instance of kind {@code multiple}?
	 */
	public boolean isMultiple() {
		return _kind == Kind.Multiple;
	}

	/**
	 * Get the {@code multiple} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code multiple} kind.
	 */
	public String multiple() {
		return TaggedUnionUtils.get(this, Kind.Multiple);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		} else {
			switch (_kind) {
				case Multiple :
					generator.write(((String) this._value));

					break;
			}
		}

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<SimpleQueryStringFlags> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<SimpleQueryStringFlags> single(SimpleQueryStringFlag v) {
			this._kind = Kind.Single;
			this._value = v;
			return this;
		}

		public ObjectBuilder<SimpleQueryStringFlags> multiple(String v) {
			this._kind = Kind.Multiple;
			this._value = v;
			return this;
		}

		public SimpleQueryStringFlags build() {
			_checkSingleUse();
			return new SimpleQueryStringFlags(this);
		}

	}

	private static JsonpDeserializer<SimpleQueryStringFlags> buildSimpleQueryStringFlagsDeserializer() {
		return new UnionDeserializer.Builder<SimpleQueryStringFlags, Kind, Object>(SimpleQueryStringFlags::new, true)
				.addMember(Kind.Single, SimpleQueryStringFlag._DESERIALIZER)
				.addMember(Kind.Multiple, JsonpDeserializer.stringDeserializer()).build();
	}

	public static final JsonpDeserializer<SimpleQueryStringFlags> _DESERIALIZER = JsonpDeserializer
			.lazy(SimpleQueryStringFlags::buildSimpleQueryStringFlagsDeserializer);
}
