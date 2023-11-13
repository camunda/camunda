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

package org.opensearch.client.opensearch.watcher;

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
import java.lang.Object;
import java.lang.String;
import java.util.function.Function;

// typedef: watcher._types.TimeOfDay


@JsonpDeserializable
public class TimeOfDay implements TaggedUnion<TimeOfDay.Kind, Object>, JsonpSerializable {

	public enum Kind {
		Text, HourMinute

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

	private TimeOfDay(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	private TimeOfDay(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static TimeOfDay of(Function<Builder, ObjectBuilder<TimeOfDay>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code text}?
	 */
	public boolean isText() {
		return _kind == Kind.Text;
	}

	/**
	 * Get the {@code text} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code text} kind.
	 */
	public String text() {
		return TaggedUnionUtils.get(this, Kind.Text);
	}

	/**
	 * Is this variant instance of kind {@code hour_minute}?
	 */
	public boolean isHourMinute() {
		return _kind == Kind.HourMinute;
	}

	/**
	 * Get the {@code hour_minute} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code hour_minute} kind.
	 */
	public HourAndMinute hourMinute() {
		return TaggedUnionUtils.get(this, Kind.HourMinute);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		} else {
			switch (_kind) {
				case Text :
					generator.write(((String) this._value));

					break;
			}
		}

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<TimeOfDay> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<TimeOfDay> text(String v) {
			this._kind = Kind.Text;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TimeOfDay> hourMinute(HourAndMinute v) {
			this._kind = Kind.HourMinute;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TimeOfDay> hourMinute(Function<HourAndMinute.Builder, ObjectBuilder<HourAndMinute>> fn) {
			return this.hourMinute(fn.apply(new HourAndMinute.Builder()).build());
		}

		public TimeOfDay build() {
			_checkSingleUse();
			return new TimeOfDay(this);
		}

	}

	private static JsonpDeserializer<TimeOfDay> buildTimeOfDayDeserializer() {
		return new UnionDeserializer.Builder<TimeOfDay, Kind, Object>(TimeOfDay::new, false)
				.addMember(Kind.Text, JsonpDeserializer.stringDeserializer())
				.addMember(Kind.HourMinute, HourAndMinute._DESERIALIZER).build();
	}

	public static final JsonpDeserializer<TimeOfDay> _DESERIALIZER = JsonpDeserializer
			.lazy(TimeOfDay::buildTimeOfDayDeserializer);
}
