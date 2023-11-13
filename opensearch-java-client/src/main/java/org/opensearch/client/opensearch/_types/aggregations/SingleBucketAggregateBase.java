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

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.util.ObjectBuilder;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

// typedef: _types.aggregations.SingleBucketAggregateBase



public abstract class SingleBucketAggregateBase extends AggregateBase {
	private final Map<String, Aggregate> aggregations;
	private final long docCount;

	// ---------------------------------------------------------------------------------------------

	protected SingleBucketAggregateBase(AbstractBuilder<?> builder) {
		super(builder);
		this.aggregations = ApiTypeHelper.unmodifiable(builder.aggregations);

		this.docCount = ApiTypeHelper.requireNonNull(builder.docCount, this, "docCount");

	}

	public final Map<String, Aggregate> aggregations() {
		return this.aggregations;
	}

	/**
	 * Required - API name: {@code doc_count}
	 */
	public final long docCount() {
		return this.docCount;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("doc_count");
		generator.write(this.docCount);

	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				AggregateBase.AbstractBuilder<BuilderT> {
		@Nullable
		protected Map<String, Aggregate> aggregations = new HashMap<>();
		private Long docCount;

		public final BuilderT aggregations(Map<String, Aggregate> aggregateMap) {
			this.aggregations = _mapPutAll(this.aggregations, aggregateMap);
			return self();
		}

		public final BuilderT aggregations(String key, Aggregate value) {
			this.aggregations = _mapPut(this.aggregations, key, value);
			return self();
		}

		public final BuilderT aggregations(String key, Function<Aggregate.Builder, ObjectBuilder<Aggregate>> function) {
			return aggregations(key, function.apply(new Aggregate.Builder()).build());
		}

		public final BuilderT docCount(long value) {
			this.docCount = value;
			return self();
		}

	}

	// ---------------------------------------------------------------------------------------------
	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupSingleBucketAggregateBaseDeserializer(
			ObjectDeserializer<BuilderT> op) {
		AggregateBase.setupAggregateBaseDeserializer(op);
		op.add(AbstractBuilder::docCount, JsonpDeserializer.longDeserializer(), "doc_count");

		op.setUnknownFieldHandler((builder, name, parser, mapper) -> {
			if (builder.aggregations == null) {
				builder.aggregations = new HashMap<>();
			}
			Aggregate._TYPED_KEYS_DESERIALIZER.deserializeEntry(name, parser, mapper, builder.aggregations);
		});
	}

}
