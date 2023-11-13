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
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;

import java.util.function.Function;

// typedef: _types.aggregations.HdrPercentileRanksAggregate


@JsonpDeserializable
public class HdrPercentileRanksAggregate extends PercentilesAggregateBase implements AggregateVariant {
	// ---------------------------------------------------------------------------------------------

	private HdrPercentileRanksAggregate(Builder builder) {
		super(builder);

	}

	public static HdrPercentileRanksAggregate of(Function<Builder, ObjectBuilder<HdrPercentileRanksAggregate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregate variant kind.
	 */
	@Override
	public Aggregate.Kind _aggregateKind() {
		return Aggregate.Kind.HdrPercentileRanks;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HdrPercentileRanksAggregate}.
	 */

	public static class Builder extends PercentilesAggregateBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<HdrPercentileRanksAggregate> {
		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link HdrPercentileRanksAggregate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HdrPercentileRanksAggregate build() {
			_checkSingleUse();

			return new HdrPercentileRanksAggregate(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HdrPercentileRanksAggregate}
	 */
	public static final JsonpDeserializer<HdrPercentileRanksAggregate> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, HdrPercentileRanksAggregate::setupHdrPercentileRanksAggregateDeserializer);

	protected static void setupHdrPercentileRanksAggregateDeserializer(
			ObjectDeserializer<HdrPercentileRanksAggregate.Builder> op) {
		setupPercentilesAggregateBaseDeserializer(op);

	}

}
