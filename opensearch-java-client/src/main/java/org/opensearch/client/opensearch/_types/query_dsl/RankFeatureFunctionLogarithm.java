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
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: _types.query_dsl.RankFeatureFunctionLogarithm


@JsonpDeserializable
public class RankFeatureFunctionLogarithm extends RankFeatureFunction implements JsonpSerializable {
	private final float scalingFactor;

	// ---------------------------------------------------------------------------------------------

	private RankFeatureFunctionLogarithm(Builder builder) {

		this.scalingFactor = ApiTypeHelper.requireNonNull(builder.scalingFactor, this, "scalingFactor");

	}

	public static RankFeatureFunctionLogarithm of(Function<Builder, ObjectBuilder<RankFeatureFunctionLogarithm>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code scaling_factor}
	 */
	public final float scalingFactor() {
		return this.scalingFactor;
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

		generator.writeKey("scaling_factor");
		generator.write(this.scalingFactor);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RankFeatureFunctionLogarithm}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RankFeatureFunctionLogarithm> {
		private Float scalingFactor;

		/**
		 * Required - API name: {@code scaling_factor}
		 */
		public final Builder scalingFactor(float value) {
			this.scalingFactor = value;
			return this;
		}

		/**
		 * Builds a {@link RankFeatureFunctionLogarithm}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RankFeatureFunctionLogarithm build() {
			_checkSingleUse();

			return new RankFeatureFunctionLogarithm(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RankFeatureFunctionLogarithm}
	 */
	public static final JsonpDeserializer<RankFeatureFunctionLogarithm> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, RankFeatureFunctionLogarithm::setupRankFeatureFunctionLogarithmDeserializer);

	protected static void setupRankFeatureFunctionLogarithmDeserializer(
			ObjectDeserializer<RankFeatureFunctionLogarithm.Builder> op) {

		op.add(Builder::scalingFactor, JsonpDeserializer.floatDeserializer(), "scaling_factor");

	}

}
