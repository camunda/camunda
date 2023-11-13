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
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.query_dsl.RankFeatureFunctionSaturation


@JsonpDeserializable
public class RankFeatureFunctionSaturation extends RankFeatureFunction implements JsonpSerializable {
	@Nullable
	private final Float pivot;

	// ---------------------------------------------------------------------------------------------

	private RankFeatureFunctionSaturation(Builder builder) {

		this.pivot = builder.pivot;

	}

	public static RankFeatureFunctionSaturation of(Function<Builder, ObjectBuilder<RankFeatureFunctionSaturation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code pivot}
	 */
	@Nullable
	public final Float pivot() {
		return this.pivot;
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

		if (this.pivot != null) {
			generator.writeKey("pivot");
			generator.write(this.pivot);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RankFeatureFunctionSaturation}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RankFeatureFunctionSaturation> {
		@Nullable
		private Float pivot;

		/**
		 * API name: {@code pivot}
		 */
		public final Builder pivot(@Nullable Float value) {
			this.pivot = value;
			return this;
		}

		/**
		 * Builds a {@link RankFeatureFunctionSaturation}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RankFeatureFunctionSaturation build() {
			_checkSingleUse();

			return new RankFeatureFunctionSaturation(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RankFeatureFunctionSaturation}
	 */
	public static final JsonpDeserializer<RankFeatureFunctionSaturation> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, RankFeatureFunctionSaturation::setupRankFeatureFunctionSaturationDeserializer);

	protected static void setupRankFeatureFunctionSaturationDeserializer(
			ObjectDeserializer<RankFeatureFunctionSaturation.Builder> op) {

		op.add(Builder::pivot, JsonpDeserializer.floatDeserializer(), "pivot");

	}

}
