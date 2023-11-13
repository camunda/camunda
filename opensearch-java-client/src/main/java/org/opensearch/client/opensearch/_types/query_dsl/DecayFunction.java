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
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: _types.query_dsl.DecayFunction


@JsonpDeserializable
public class DecayFunction extends DecayFunctionBase implements FunctionScoreVariant {
	private final String field;

	private final DecayPlacement placement;

	// ---------------------------------------------------------------------------------------------

	private DecayFunction(Builder builder) {
		super(builder);
		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");
		this.placement = ApiTypeHelper.requireNonNull(builder.placement, this, "placement");

	}

	public static DecayFunction of(Function<Builder, ObjectBuilder<DecayFunction>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * FunctionScore variant kind.
	 */
	@Override
	public FunctionScore.Kind _functionScoreKind() {
		return FunctionScore.Kind.Linear;
	}

	/**
	 * Required -
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * Required -
	 */
	public final DecayPlacement placement() {
		return this.placement;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeKey(this.field);
		this.placement.serialize(generator, mapper);

		super.serializeInternal(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DecayFunction}.
	 */

	public static class Builder extends DecayFunctionBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<DecayFunction> {
		private String field;

		private DecayPlacement placement;

		/**
		 * Required -
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		/**
		 * Required -
		 */
		public final Builder placement(DecayPlacement value) {
			this.placement = value;
			return this;
		}

		/**
		 * Required -
		 */
		public final Builder placement(Function<DecayPlacement.Builder, ObjectBuilder<DecayPlacement>> fn) {
			return this.placement(fn.apply(new DecayPlacement.Builder()).build());
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link DecayFunction}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DecayFunction build() {
			_checkSingleUse();

			return new DecayFunction(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link DecayFunction}
	 */
	public static final JsonpDeserializer<DecayFunction> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			DecayFunction::setupDecayFunctionDeserializer);

	protected static void setupDecayFunctionDeserializer(ObjectDeserializer<DecayFunction.Builder> op) {
		setupDecayFunctionBaseDeserializer(op);

		op.setUnknownFieldHandler((builder, name, parser, mapper) -> {
			builder.field(name);
			builder.placement(DecayPlacement._DESERIALIZER.deserialize(parser, mapper));
		});

	}

}
