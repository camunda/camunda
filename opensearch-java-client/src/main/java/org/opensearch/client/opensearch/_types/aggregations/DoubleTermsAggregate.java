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

// typedef: _types.aggregations.DoubleTermsAggregate

/**
 * Result of a <code>terms</code> aggregation when the field is some kind of
 * decimal number like a float, double, or distance.
 * 
 */
@JsonpDeserializable
public class DoubleTermsAggregate extends TermsAggregateBase<DoubleTermsBucket> implements AggregateVariant {
	// ---------------------------------------------------------------------------------------------

	private DoubleTermsAggregate(Builder builder) {
		super(builder);

	}

	public static DoubleTermsAggregate of(Function<Builder, ObjectBuilder<DoubleTermsAggregate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregate variant kind.
	 */
	@Override
	public Aggregate.Kind _aggregateKind() {
		return Aggregate.Kind.Dterms;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DoubleTermsAggregate}.
	 */

	public static class Builder extends TermsAggregateBase.AbstractBuilder<DoubleTermsBucket, Builder>
			implements
				ObjectBuilder<DoubleTermsAggregate> {
		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link DoubleTermsAggregate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DoubleTermsAggregate build() {
			_checkSingleUse();
			super.tBucketSerializer(null);

			return new DoubleTermsAggregate(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link DoubleTermsAggregate}
	 */
	public static final JsonpDeserializer<DoubleTermsAggregate> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, DoubleTermsAggregate::setupDoubleTermsAggregateDeserializer);

	protected static void setupDoubleTermsAggregateDeserializer(ObjectDeserializer<DoubleTermsAggregate.Builder> op) {
		setupTermsAggregateBaseDeserializer(op, DoubleTermsBucket._DESERIALIZER);

	}

}
