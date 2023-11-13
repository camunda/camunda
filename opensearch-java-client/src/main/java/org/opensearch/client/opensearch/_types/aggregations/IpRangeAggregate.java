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

// typedef: _types.aggregations.IpRangeAggregate


@JsonpDeserializable
public class IpRangeAggregate extends MultiBucketAggregateBase<IpRangeBucket> implements AggregateVariant {
	// ---------------------------------------------------------------------------------------------

	private IpRangeAggregate(Builder builder) {
		super(builder);

	}

	public static IpRangeAggregate of(Function<Builder, ObjectBuilder<IpRangeAggregate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregate variant kind.
	 */
	@Override
	public Aggregate.Kind _aggregateKind() {
		return Aggregate.Kind.IpRange;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IpRangeAggregate}.
	 */

	public static class Builder extends MultiBucketAggregateBase.AbstractBuilder<IpRangeBucket, Builder>
			implements
				ObjectBuilder<IpRangeAggregate> {
		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link IpRangeAggregate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IpRangeAggregate build() {
			_checkSingleUse();
			super.tBucketSerializer(null);

			return new IpRangeAggregate(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IpRangeAggregate}
	 */
	public static final JsonpDeserializer<IpRangeAggregate> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IpRangeAggregate::setupIpRangeAggregateDeserializer);

	protected static void setupIpRangeAggregateDeserializer(ObjectDeserializer<IpRangeAggregate.Builder> op) {
		setupMultiBucketAggregateBaseDeserializer(op, IpRangeBucket._DESERIALIZER);

	}

}
