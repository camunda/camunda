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

import org.opensearch.client.json.JsonData;
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

// typedef: _types.query_dsl.DecayPlacement


@JsonpDeserializable
public class DecayPlacement implements JsonpSerializable {
	@Nullable
	private final Double decay;

	@Nullable
	private final JsonData offset;

	@Nullable
	private final JsonData scale;

	@Nullable
	private final JsonData origin;

	// ---------------------------------------------------------------------------------------------

	private DecayPlacement(Builder builder) {

		this.decay = builder.decay;
		this.offset = builder.offset;
		this.scale = builder.scale;
		this.origin = builder.origin;

	}

	public static DecayPlacement of(Function<Builder, ObjectBuilder<DecayPlacement>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code decay}
	 */
	@Nullable
	public final Double decay() {
		return this.decay;
	}

	/**
	 * API name: {@code offset}
	 */
	@Nullable
	public final JsonData offset() {
		return this.offset;
	}

	/**
	 * API name: {@code scale}
	 */
	@Nullable
	public final JsonData scale() {
		return this.scale;
	}

	/**
	 * API name: {@code origin}
	 */
	@Nullable
	public final JsonData origin() {
		return this.origin;
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

		if (this.decay != null) {
			generator.writeKey("decay");
			generator.write(this.decay);

		}
		if (this.offset != null) {
			generator.writeKey("offset");
			this.offset.serialize(generator, mapper);

		}
		if (this.scale != null) {
			generator.writeKey("scale");
			this.scale.serialize(generator, mapper);

		}
		if (this.origin != null) {
			generator.writeKey("origin");
			this.origin.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DecayPlacement}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DecayPlacement> {
		@Nullable
		private Double decay;

		@Nullable
		private JsonData offset;

		@Nullable
		private JsonData scale;

		@Nullable
		private JsonData origin;

		/**
		 * API name: {@code decay}
		 */
		public final Builder decay(@Nullable Double value) {
			this.decay = value;
			return this;
		}

		/**
		 * API name: {@code offset}
		 */
		public final Builder offset(@Nullable JsonData value) {
			this.offset = value;
			return this;
		}

		/**
		 * API name: {@code scale}
		 */
		public final Builder scale(@Nullable JsonData value) {
			this.scale = value;
			return this;
		}

		/**
		 * API name: {@code origin}
		 */
		public final Builder origin(@Nullable JsonData value) {
			this.origin = value;
			return this;
		}

		/**
		 * Builds a {@link DecayPlacement}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DecayPlacement build() {
			_checkSingleUse();

			return new DecayPlacement(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link DecayPlacement}
	 */
	public static final JsonpDeserializer<DecayPlacement> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			DecayPlacement::setupDecayPlacementDeserializer);

	protected static void setupDecayPlacementDeserializer(ObjectDeserializer<DecayPlacement.Builder> op) {

		op.add(Builder::decay, JsonpDeserializer.doubleDeserializer(), "decay");
		op.add(Builder::offset, JsonData._DESERIALIZER, "offset");
		op.add(Builder::scale, JsonData._DESERIALIZER, "scale");
		op.add(Builder::origin, JsonData._DESERIALIZER, "origin");

	}

}
