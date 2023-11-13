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

package org.opensearch.client.opensearch._types.analysis;

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

// typedef: _types.analysis.LowercaseNormalizer

@JsonpDeserializable
public class LowercaseNormalizer implements NormalizerVariant, JsonpSerializable {
	// ---------------------------------------------------------------------------------------------

	private LowercaseNormalizer(Builder builder) {

	}

	public static LowercaseNormalizer of(Function<Builder, ObjectBuilder<LowercaseNormalizer>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Normalizer variant kind.
	 */
	@Override
	public Normalizer.Kind _normalizerKind() {
		return Normalizer.Kind.Lowercase;
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

		generator.write("type", "lowercase");

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link LowercaseNormalizer}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<LowercaseNormalizer> {

		/**
		 * Builds a {@link LowercaseNormalizer}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public LowercaseNormalizer build() {
			_checkSingleUse();

			return new LowercaseNormalizer(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link LowercaseNormalizer}
	 */
	public static final JsonpDeserializer<LowercaseNormalizer> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, LowercaseNormalizer::setupLowercaseNormalizerDeserializer);

	protected static void setupLowercaseNormalizerDeserializer(ObjectDeserializer<LowercaseNormalizer.Builder> op) {

		op.ignore("type");
	}

}
