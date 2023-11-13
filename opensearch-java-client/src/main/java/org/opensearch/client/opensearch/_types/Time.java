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

package org.opensearch.client.opensearch._types;

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

// typedef: _types.Time

/**
 * Whenever durations need to be specified, e.g. for a timeout parameter, the
 * duration must specify the unit, like 2d for 2 days.
 * 
 */
@JsonpDeserializable
public class Time implements TaggedUnion<Time.Kind, Object>, JsonpSerializable {

	public enum Kind {
		Offset, Time

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

	private Time(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	public String _toJsonString() {
		switch (_kind) {
			case Offset :
				return String.valueOf(this.offset());
			case Time :
				return this.time();

			default :
				throw new IllegalStateException("Unknown kind " + _kind);
		}
	}

	private Time(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static Time of(Function<Builder, ObjectBuilder<Time>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code offset}?
	 */
	public boolean isOffset() {
		return _kind == Kind.Offset;
	}

	/**
	 * Get the {@code offset} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code offset} kind.
	 */
	public Integer offset() {
		return TaggedUnionUtils.get(this, Kind.Offset);
	}

	/**
	 * Is this variant instance of kind {@code time}?
	 */
	public boolean isTime() {
		return _kind == Kind.Time;
	}

	/**
	 * Get the {@code time} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code time} kind.
	 */
	public String time() {
		return TaggedUnionUtils.get(this, Kind.Time);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		} else {
			switch (_kind) {
				case Offset :
					generator.write(((Integer) this._value));

					break;
				case Time :
					generator.write(((String) this._value));

					break;
			}
		}

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Time> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<Time> offset(Integer v) {
			this._kind = Kind.Offset;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Time> time(String v) {
			this._kind = Kind.Time;
			this._value = v;
			return this;
		}

		public Time build() {
			_checkSingleUse();
			return new Time(this);
		}

	}

	private static JsonpDeserializer<Time> buildTimeDeserializer() {
		return new UnionDeserializer.Builder<Time, Kind, Object>(Time::new, false)
				.addMember(Kind.Offset, JsonpDeserializer.integerDeserializer())
				.addMember(Kind.Time, JsonpDeserializer.stringDeserializer()).build();
	}

	public static final JsonpDeserializer<Time> _DESERIALIZER = JsonpDeserializer.lazy(Time::buildTimeDeserializer);
}
