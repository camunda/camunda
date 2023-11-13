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
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.HoltWintersModelSettings


@JsonpDeserializable
public class HoltWintersModelSettings implements JsonpSerializable {
	@Nullable
	private final Float alpha;

	@Nullable
	private final Float beta;

	@Nullable
	private final Float gamma;

	@Nullable
	private final Boolean pad;

	@Nullable
	private final Integer period;

	@Nullable
	private final HoltWintersType type;

	// ---------------------------------------------------------------------------------------------

	private HoltWintersModelSettings(Builder builder) {

		this.alpha = builder.alpha;
		this.beta = builder.beta;
		this.gamma = builder.gamma;
		this.pad = builder.pad;
		this.period = builder.period;
		this.type = builder.type;

	}

	public static HoltWintersModelSettings of(Function<Builder, ObjectBuilder<HoltWintersModelSettings>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code alpha}
	 */
	@Nullable
	public final Float alpha() {
		return this.alpha;
	}

	/**
	 * API name: {@code beta}
	 */
	@Nullable
	public final Float beta() {
		return this.beta;
	}

	/**
	 * API name: {@code gamma}
	 */
	@Nullable
	public final Float gamma() {
		return this.gamma;
	}

	/**
	 * API name: {@code pad}
	 */
	@Nullable
	public final Boolean pad() {
		return this.pad;
	}

	/**
	 * API name: {@code period}
	 */
	@Nullable
	public final Integer period() {
		return this.period;
	}

	/**
	 * API name: {@code type}
	 */
	@Nullable
	public final HoltWintersType type() {
		return this.type;
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

		if (this.alpha != null) {
			generator.writeKey("alpha");
			generator.write(this.alpha);

		}
		if (this.beta != null) {
			generator.writeKey("beta");
			generator.write(this.beta);

		}
		if (this.gamma != null) {
			generator.writeKey("gamma");
			generator.write(this.gamma);

		}
		if (this.pad != null) {
			generator.writeKey("pad");
			generator.write(this.pad);

		}
		if (this.period != null) {
			generator.writeKey("period");
			generator.write(this.period);

		}
		if (this.type != null) {
			generator.writeKey("type");
			this.type.serialize(generator, mapper);
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HoltWintersModelSettings}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<HoltWintersModelSettings> {
		@Nullable
		private Float alpha;

		@Nullable
		private Float beta;

		@Nullable
		private Float gamma;

		@Nullable
		private Boolean pad;

		@Nullable
		private Integer period;

		@Nullable
		private HoltWintersType type;

		/**
		 * API name: {@code alpha}
		 */
		public final Builder alpha(@Nullable Float value) {
			this.alpha = value;
			return this;
		}

		/**
		 * API name: {@code beta}
		 */
		public final Builder beta(@Nullable Float value) {
			this.beta = value;
			return this;
		}

		/**
		 * API name: {@code gamma}
		 */
		public final Builder gamma(@Nullable Float value) {
			this.gamma = value;
			return this;
		}

		/**
		 * API name: {@code pad}
		 */
		public final Builder pad(@Nullable Boolean value) {
			this.pad = value;
			return this;
		}

		/**
		 * API name: {@code period}
		 */
		public final Builder period(@Nullable Integer value) {
			this.period = value;
			return this;
		}

		/**
		 * API name: {@code type}
		 */
		public final Builder type(@Nullable HoltWintersType value) {
			this.type = value;
			return this;
		}

		/**
		 * Builds a {@link HoltWintersModelSettings}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HoltWintersModelSettings build() {
			_checkSingleUse();

			return new HoltWintersModelSettings(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HoltWintersModelSettings}
	 */
	public static final JsonpDeserializer<HoltWintersModelSettings> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, HoltWintersModelSettings::setupHoltWintersModelSettingsDeserializer);

	protected static void setupHoltWintersModelSettingsDeserializer(
			ObjectDeserializer<HoltWintersModelSettings.Builder> op) {

		op.add(Builder::alpha, JsonpDeserializer.floatDeserializer(), "alpha");
		op.add(Builder::beta, JsonpDeserializer.floatDeserializer(), "beta");
		op.add(Builder::gamma, JsonpDeserializer.floatDeserializer(), "gamma");
		op.add(Builder::pad, JsonpDeserializer.booleanDeserializer(), "pad");
		op.add(Builder::period, JsonpDeserializer.integerDeserializer(), "period");
		op.add(Builder::type, HoltWintersType._DESERIALIZER, "type");

	}

}
