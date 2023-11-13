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
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;

// typedef: watcher._types.HourAndMinute


@JsonpDeserializable
public class HourAndMinute implements JsonpSerializable {
	private final List<Integer> hour;

	private final List<Integer> minute;

	// ---------------------------------------------------------------------------------------------

	private HourAndMinute(Builder builder) {

		this.hour = ApiTypeHelper.unmodifiableRequired(builder.hour, this, "hour");
		this.minute = ApiTypeHelper.unmodifiableRequired(builder.minute, this, "minute");

	}

	public static HourAndMinute of(Function<Builder, ObjectBuilder<HourAndMinute>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code hour}
	 */
	public final List<Integer> hour() {
		return this.hour;
	}

	/**
	 * Required - API name: {@code minute}
	 */
	public final List<Integer> minute() {
		return this.minute;
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

		if (ApiTypeHelper.isDefined(this.hour)) {
			generator.writeKey("hour");
			generator.writeStartArray();
			for (Integer item0 : this.hour) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.minute)) {
			generator.writeKey("minute");
			generator.writeStartArray();
			for (Integer item0 : this.minute) {
				generator.write(item0);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HourAndMinute}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<HourAndMinute> {
		private List<Integer> hour;

		private List<Integer> minute;

		/**
		 * Required - API name: {@code hour}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>hour</code>.
		 */
		public final Builder hour(List<Integer> list) {
			this.hour = _listAddAll(this.hour, list);
			return this;
		}

		/**
		 * Required - API name: {@code hour}
		 * <p>
		 * Adds one or more values to <code>hour</code>.
		 */
		public final Builder hour(Integer value, Integer... values) {
			this.hour = _listAdd(this.hour, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code minute}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>minute</code>.
		 */
		public final Builder minute(List<Integer> list) {
			this.minute = _listAddAll(this.minute, list);
			return this;
		}

		/**
		 * Required - API name: {@code minute}
		 * <p>
		 * Adds one or more values to <code>minute</code>.
		 */
		public final Builder minute(Integer value, Integer... values) {
			this.minute = _listAdd(this.minute, value, values);
			return this;
		}

		/**
		 * Builds a {@link HourAndMinute}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HourAndMinute build() {
			_checkSingleUse();

			return new HourAndMinute(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HourAndMinute}
	 */
	public static final JsonpDeserializer<HourAndMinute> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			HourAndMinute::setupHourAndMinuteDeserializer);

	protected static void setupHourAndMinuteDeserializer(ObjectDeserializer<HourAndMinute.Builder> op) {

		op.add(Builder::hour, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.integerDeserializer()), "hour");
		op.add(Builder::minute, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.integerDeserializer()), "minute");

	}

}
