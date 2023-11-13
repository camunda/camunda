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

package org.opensearch.client.opensearch.nodes;

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
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: nodes._types.Cpu

@JsonpDeserializable
public class Cpu implements JsonpSerializable {
	private final int percent;

	@Nullable
	private final String sys;

	@Nullable
	private final Long sysInMillis;

	@Nullable
	private final String total;

	@Nullable
	private final Long totalInMillis;

	@Nullable
	private final String user;

	@Nullable
	private final Long userInMillis;

	private final Map<String, Double> loadAverage;

	// ---------------------------------------------------------------------------------------------

	private Cpu(Builder builder) {

		this.percent = ApiTypeHelper.requireNonNull(builder.percent, this, "percent");
		this.sys = builder.sys;
		this.sysInMillis = builder.sysInMillis;
		this.total = builder.total;
		this.totalInMillis = builder.totalInMillis;
		this.user = builder.user;
		this.userInMillis = builder.userInMillis;
		this.loadAverage = ApiTypeHelper.unmodifiable(builder.loadAverage);

	}

	public static Cpu of(Function<Builder, ObjectBuilder<Cpu>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code percent}
	 */
	public final int percent() {
		return this.percent;
	}

	/**
	 * API name: {@code sys}
	 */
	@Nullable
	public final String sys() {
		return this.sys;
	}

	/**
	 * API name: {@code sys_in_millis}
	 */
	@Nullable
	public final Long sysInMillis() {
		return this.sysInMillis;
	}

	/**
	 * API name: {@code total}
	 */
	@Nullable
	public final String total() {
		return this.total;
	}

	/**
	 * API name: {@code total_in_millis}
	 */
	@Nullable
	public final Long totalInMillis() {
		return this.totalInMillis;
	}

	/**
	 * API name: {@code user}
	 */
	@Nullable
	public final String user() {
		return this.user;
	}

	/**
	 * API name: {@code user_in_millis}
	 */
	@Nullable
	public final Long userInMillis() {
		return this.userInMillis;
	}

	/**
	 * API name: {@code load_average}
	 */
	public final Map<String, Double> loadAverage() {
		return this.loadAverage;
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

		generator.writeKey("percent");
		generator.write(this.percent);

		if (this.sys != null) {
			generator.writeKey("sys");
			generator.write(this.sys);

		}
		if (this.sysInMillis != null) {
			generator.writeKey("sys_in_millis");
			generator.write(this.sysInMillis);

		}
		if (this.total != null) {
			generator.writeKey("total");
			generator.write(this.total);

		}
		if (this.totalInMillis != null) {
			generator.writeKey("total_in_millis");
			generator.write(this.totalInMillis);

		}
		if (this.user != null) {
			generator.writeKey("user");
			generator.write(this.user);

		}
		if (this.userInMillis != null) {
			generator.writeKey("user_in_millis");
			generator.write(this.userInMillis);

		}
		if (ApiTypeHelper.isDefined(this.loadAverage)) {
			generator.writeKey("load_average");
			generator.writeStartObject();
			for (Map.Entry<String, Double> item0 : this.loadAverage.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Cpu}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Cpu> {
		private Integer percent;

		@Nullable
		private String sys;

		@Nullable
		private Long sysInMillis;

		@Nullable
		private String total;

		@Nullable
		private Long totalInMillis;

		@Nullable
		private String user;

		@Nullable
		private Long userInMillis;

		@Nullable
		private Map<String, Double> loadAverage;

		/**
		 * Required - API name: {@code percent}
		 */
		public final Builder percent(int value) {
			this.percent = value;
			return this;
		}

		/**
		 * API name: {@code sys}
		 */
		public final Builder sys(@Nullable String value) {
			this.sys = value;
			return this;
		}

		/**
		 * API name: {@code sys_in_millis}
		 */
		public final Builder sysInMillis(@Nullable Long value) {
			this.sysInMillis = value;
			return this;
		}

		/**
		 * API name: {@code total}
		 */
		public final Builder total(@Nullable String value) {
			this.total = value;
			return this;
		}

		/**
		 * API name: {@code total_in_millis}
		 */
		public final Builder totalInMillis(@Nullable Long value) {
			this.totalInMillis = value;
			return this;
		}

		/**
		 * API name: {@code user}
		 */
		public final Builder user(@Nullable String value) {
			this.user = value;
			return this;
		}

		/**
		 * API name: {@code user_in_millis}
		 */
		public final Builder userInMillis(@Nullable Long value) {
			this.userInMillis = value;
			return this;
		}

		/**
		 * API name: {@code load_average}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>loadAverage</code>.
		 */
		public final Builder loadAverage(Map<String, Double> map) {
			this.loadAverage = _mapPutAll(this.loadAverage, map);
			return this;
		}

		/**
		 * API name: {@code load_average}
		 * <p>
		 * Adds an entry to <code>loadAverage</code>.
		 */
		public final Builder loadAverage(String key, Double value) {
			this.loadAverage = _mapPut(this.loadAverage, key, value);
			return this;
		}

		/**
		 * Builds a {@link Cpu}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Cpu build() {
			_checkSingleUse();

			return new Cpu(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Cpu}
	 */
	public static final JsonpDeserializer<Cpu> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Cpu::setupCpuDeserializer);

	protected static void setupCpuDeserializer(ObjectDeserializer<Cpu.Builder> op) {

		op.add(Builder::percent, JsonpDeserializer.integerDeserializer(), "percent");
		op.add(Builder::sys, JsonpDeserializer.stringDeserializer(), "sys");
		op.add(Builder::sysInMillis, JsonpDeserializer.longDeserializer(), "sys_in_millis");
		op.add(Builder::total, JsonpDeserializer.stringDeserializer(), "total");
		op.add(Builder::totalInMillis, JsonpDeserializer.longDeserializer(), "total_in_millis");
		op.add(Builder::user, JsonpDeserializer.stringDeserializer(), "user");
		op.add(Builder::userInMillis, JsonpDeserializer.longDeserializer(), "user_in_millis");
		op.add(Builder::loadAverage, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.doubleDeserializer()),
				"load_average");

	}

}
