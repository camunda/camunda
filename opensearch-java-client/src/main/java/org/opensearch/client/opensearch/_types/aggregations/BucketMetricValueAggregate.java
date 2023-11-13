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
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;

// typedef: _types.aggregations.BucketMetricValueAggregate

@JsonpDeserializable
public class BucketMetricValueAggregate extends SingleMetricAggregateBase implements AggregateVariant {
	private final List<String> keys;

	// ---------------------------------------------------------------------------------------------

	private BucketMetricValueAggregate(Builder builder) {
		super(builder);

		this.keys = ApiTypeHelper.unmodifiableRequired(builder.keys, this, "keys");

	}

	public static BucketMetricValueAggregate of(Function<Builder, ObjectBuilder<BucketMetricValueAggregate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregate variant kind.
	 */
	@Override
	public Aggregate.Kind _aggregateKind() {
		return Aggregate.Kind.BucketMetricValue;
	}

	/**
	 * Required - API name: {@code keys}
	 */
	public final List<String> keys() {
		return this.keys;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.keys)) {
			generator.writeKey("keys");
			generator.writeStartArray();
			for (String item0 : this.keys) {
				generator.write(item0);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link BucketMetricValueAggregate}.
	 */

	public static class Builder extends SingleMetricAggregateBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<BucketMetricValueAggregate> {
		private List<String> keys;

		/**
		 * Required - API name: {@code keys}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>keys</code>.
		 */
		public final Builder keys(List<String> list) {
			this.keys = _listAddAll(this.keys, list);
			return this;
		}

		/**
		 * Required - API name: {@code keys}
		 * <p>
		 * Adds one or more values to <code>keys</code>.
		 */
		public final Builder keys(String value, String... values) {
			this.keys = _listAdd(this.keys, value, values);
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link BucketMetricValueAggregate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public BucketMetricValueAggregate build() {
			_checkSingleUse();

			return new BucketMetricValueAggregate(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link BucketMetricValueAggregate}
	 */
	public static final JsonpDeserializer<BucketMetricValueAggregate> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, BucketMetricValueAggregate::setupBucketMetricValueAggregateDeserializer);

	protected static void setupBucketMetricValueAggregateDeserializer(
			ObjectDeserializer<BucketMetricValueAggregate.Builder> op) {
		setupSingleMetricAggregateBaseDeserializer(op);
		op.add(Builder::keys, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "keys");

	}

}
