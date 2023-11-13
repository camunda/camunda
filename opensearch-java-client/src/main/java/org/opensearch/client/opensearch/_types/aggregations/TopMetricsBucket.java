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

// typedef: _types.aggregations.TopMetricsBucket


@JsonpDeserializable
public class TopMetricsBucket extends MultiBucketBase {
	private final List<TopMetrics> top;

	// ---------------------------------------------------------------------------------------------

	private TopMetricsBucket(Builder builder) {
		super(builder);

		this.top = ApiTypeHelper.unmodifiableRequired(builder.top, this, "top");

	}

	public static TopMetricsBucket of(Function<Builder, ObjectBuilder<TopMetricsBucket>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code top}
	 */
	public final List<TopMetrics> top() {
		return this.top;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.top)) {
			generator.writeKey("top");
			generator.writeStartArray();
			for (TopMetrics item0 : this.top) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link TopMetricsBucket}.
	 */

	public static class Builder extends MultiBucketBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<TopMetricsBucket> {
		private List<TopMetrics> top;

		/**
		 * Required - API name: {@code top}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>top</code>.
		 */
		public final Builder top(List<TopMetrics> list) {
			this.top = _listAddAll(this.top, list);
			return this;
		}

		/**
		 * Required - API name: {@code top}
		 * <p>
		 * Adds one or more values to <code>top</code>.
		 */
		public final Builder top(TopMetrics value, TopMetrics... values) {
			this.top = _listAdd(this.top, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code top}
		 * <p>
		 * Adds a value to <code>top</code> using a builder lambda.
		 */
		public final Builder top(Function<TopMetrics.Builder, ObjectBuilder<TopMetrics>> fn) {
			return top(fn.apply(new TopMetrics.Builder()).build());
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link TopMetricsBucket}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public TopMetricsBucket build() {
			_checkSingleUse();

			return new TopMetricsBucket(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link TopMetricsBucket}
	 */
	public static final JsonpDeserializer<TopMetricsBucket> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			TopMetricsBucket::setupTopMetricsBucketDeserializer);

	protected static void setupTopMetricsBucketDeserializer(ObjectDeserializer<TopMetricsBucket.Builder> op) {
		setupMultiBucketBaseDeserializer(op);
		op.add(Builder::top, JsonpDeserializer.arrayDeserializer(TopMetrics._DESERIALIZER), "top");

	}

}
