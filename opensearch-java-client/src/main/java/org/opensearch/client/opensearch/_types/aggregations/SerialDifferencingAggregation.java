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
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.SerialDifferencingAggregation


@JsonpDeserializable
public class SerialDifferencingAggregation extends PipelineAggregationBase implements AggregationVariant {
	@Nullable
	private final Integer lag;

	// ---------------------------------------------------------------------------------------------

	private SerialDifferencingAggregation(Builder builder) {
		super(builder);

		this.lag = builder.lag;

	}

	public static SerialDifferencingAggregation of(Function<Builder, ObjectBuilder<SerialDifferencingAggregation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregation variant kind.
	 */
	@Override
	public Aggregation.Kind _aggregationKind() {
		return Aggregation.Kind.SerialDiff;
	}

	/**
	 * API name: {@code lag}
	 */
	@Nullable
	public final Integer lag() {
		return this.lag;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.lag != null) {
			generator.writeKey("lag");
			generator.write(this.lag);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SerialDifferencingAggregation}.
	 */

	public static class Builder extends PipelineAggregationBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<SerialDifferencingAggregation> {
		@Nullable
		private Integer lag;

		/**
		 * API name: {@code lag}
		 */
		public final Builder lag(@Nullable Integer value) {
			this.lag = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link SerialDifferencingAggregation}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public SerialDifferencingAggregation build() {
			_checkSingleUse();

			return new SerialDifferencingAggregation(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link SerialDifferencingAggregation}
	 */
	public static final JsonpDeserializer<SerialDifferencingAggregation> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, SerialDifferencingAggregation::setupSerialDifferencingAggregationDeserializer);

	protected static void setupSerialDifferencingAggregationDeserializer(
			ObjectDeserializer<SerialDifferencingAggregation.Builder> op) {
		setupPipelineAggregationBaseDeserializer(op);
		op.add(Builder::lag, JsonpDeserializer.integerDeserializer(), "lag");

	}

}
