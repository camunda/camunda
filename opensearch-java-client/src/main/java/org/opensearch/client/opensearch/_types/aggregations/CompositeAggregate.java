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

import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.CompositeAggregate

@JsonpDeserializable
public class CompositeAggregate extends MultiBucketAggregateBase<CompositeBucket> implements AggregateVariant {
	private final Map<String, JsonData> afterKey;

	// ---------------------------------------------------------------------------------------------

	private CompositeAggregate(Builder builder) {
		super(builder);

		this.afterKey = ApiTypeHelper.unmodifiable(builder.afterKey);

	}

	public static CompositeAggregate of(Function<Builder, ObjectBuilder<CompositeAggregate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregate variant kind.
	 */
	@Override
	public Aggregate.Kind _aggregateKind() {
		return Aggregate.Kind.Composite;
	}

	/**
	 * API name: {@code after_key}
	 */
	public final Map<String, JsonData> afterKey() {
		return this.afterKey;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.afterKey)) {
			generator.writeKey("after_key");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.afterKey.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CompositeAggregate}.
	 */

	public static class Builder extends MultiBucketAggregateBase.AbstractBuilder<CompositeBucket, Builder>
			implements
				ObjectBuilder<CompositeAggregate> {
		@Nullable
		private Map<String, JsonData> afterKey;

		/**
		 * API name: {@code after_key}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>afterKey</code>.
		 */
		public final Builder afterKey(Map<String, JsonData> map) {
			this.afterKey = _mapPutAll(this.afterKey, map);
			return this;
		}

		/**
		 * API name: {@code after_key}
		 * <p>
		 * Adds an entry to <code>afterKey</code>.
		 */
		public final Builder afterKey(String key, JsonData value) {
			this.afterKey = _mapPut(this.afterKey, key, value);
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link CompositeAggregate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CompositeAggregate build() {
			_checkSingleUse();
			super.tBucketSerializer(null);

			return new CompositeAggregate(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CompositeAggregate}
	 */
	public static final JsonpDeserializer<CompositeAggregate> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, CompositeAggregate::setupCompositeAggregateDeserializer);

	protected static void setupCompositeAggregateDeserializer(ObjectDeserializer<CompositeAggregate.Builder> op) {
		MultiBucketAggregateBase.setupMultiBucketAggregateBaseDeserializer(op, CompositeBucket._DESERIALIZER);
		op.add(Builder::afterKey, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "after_key");

	}

}
