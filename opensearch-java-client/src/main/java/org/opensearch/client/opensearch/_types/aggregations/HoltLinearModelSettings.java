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

// typedef: _types.aggregations.HoltLinearModelSettings


@JsonpDeserializable
public class HoltLinearModelSettings implements JsonpSerializable {
	@Nullable
	private final Float alpha;

	@Nullable
	private final Float beta;

	// ---------------------------------------------------------------------------------------------

	private HoltLinearModelSettings(Builder builder) {

		this.alpha = builder.alpha;
		this.beta = builder.beta;

	}

	public static HoltLinearModelSettings of(Function<Builder, ObjectBuilder<HoltLinearModelSettings>> fn) {
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

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HoltLinearModelSettings}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<HoltLinearModelSettings> {
		@Nullable
		private Float alpha;

		@Nullable
		private Float beta;

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
		 * Builds a {@link HoltLinearModelSettings}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HoltLinearModelSettings build() {
			_checkSingleUse();

			return new HoltLinearModelSettings(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HoltLinearModelSettings}
	 */
	public static final JsonpDeserializer<HoltLinearModelSettings> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, HoltLinearModelSettings::setupHoltLinearModelSettingsDeserializer);

	protected static void setupHoltLinearModelSettingsDeserializer(
			ObjectDeserializer<HoltLinearModelSettings.Builder> op) {

		op.add(Builder::alpha, JsonpDeserializer.floatDeserializer(), "alpha");
		op.add(Builder::beta, JsonpDeserializer.floatDeserializer(), "beta");

	}

}
