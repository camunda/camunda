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

package org.opensearch.client.opensearch._types.aggregations;

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
import java.util.Map;
import java.util.function.Function;

// typedef: _types.aggregations.Percentiles

@JsonpDeserializable
public class Percentiles implements TaggedUnion<Percentiles.Kind, Object>, JsonpSerializable {

	public enum Kind {
		Array, Keyed

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

	private Percentiles(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	private Percentiles(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static Percentiles of(Function<Builder, ObjectBuilder<Percentiles>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code array}?
	 */
	public boolean isArray() {
		return _kind == Kind.Array;
	}

	/**
	 * Get the {@code array} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code array} kind.
	 */
	public List<ArrayPercentilesItem> array() {
		return TaggedUnionUtils.get(this, Kind.Array);
	}

	/**
	 * Is this variant instance of kind {@code keyed}?
	 */
	public boolean isKeyed() {
		return _kind == Kind.Keyed;
	}

	/**
	 * Get the {@code keyed} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code keyed} kind.
	 */
	public Map<String, String> keyed() {
		return TaggedUnionUtils.get(this, Kind.Keyed);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		} else {
			switch (_kind) {
				case Array :
					generator.writeStartArray();
					for (ArrayPercentilesItem item0 : ((List<ArrayPercentilesItem>) this._value)) {
						item0.serialize(generator, mapper);

					}
					generator.writeEnd();

					break;
				case Keyed :
					generator.writeStartObject();
					for (Map.Entry<String, String> item0 : ((Map<String, String>) this._value).entrySet()) {
						generator.writeKey(item0.getKey());
						generator.write(item0.getValue());

					}
					generator.writeEnd();

					break;
			}
		}

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Percentiles> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<Percentiles> array(List<ArrayPercentilesItem> v) {
			this._kind = Kind.Array;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Percentiles> keyed(Map<String, String> v) {
			this._kind = Kind.Keyed;
			this._value = v;
			return this;
		}

		public Percentiles build() {
			_checkSingleUse();
			return new Percentiles(this);
		}

	}

	private static JsonpDeserializer<Percentiles> buildPercentilesDeserializer() {
		return new UnionDeserializer.Builder<Percentiles, Kind, Object>(Percentiles::new, false)
				.addMember(Kind.Array, JsonpDeserializer.arrayDeserializer(ArrayPercentilesItem._DESERIALIZER))
				.addMember(Kind.Keyed, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()))
				.build();
	}

	public static final JsonpDeserializer<Percentiles> _DESERIALIZER = JsonpDeserializer
			.lazy(Percentiles::buildPercentilesDeserializer);
}
