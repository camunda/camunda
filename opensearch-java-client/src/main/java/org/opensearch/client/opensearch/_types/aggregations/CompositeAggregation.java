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
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.CompositeAggregation

@JsonpDeserializable
public class CompositeAggregation extends BucketAggregationBase implements AggregationVariant {
	private final Map<String, String> after;

	@Nullable
	private final Integer size;

	private final List<Map<String, CompositeAggregationSource>> sources;

	// ---------------------------------------------------------------------------------------------

	private CompositeAggregation(Builder builder) {
		super(builder);

		this.after = ApiTypeHelper.unmodifiable(builder.after);
		this.size = builder.size;
		this.sources = ApiTypeHelper.unmodifiable(builder.sources);

	}

	public static CompositeAggregation of(Function<Builder, ObjectBuilder<CompositeAggregation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregation variant kind.
	 */
	@Override
	public Aggregation.Kind _aggregationKind() {
		return Aggregation.Kind.Composite;
	}

	/**
	 * API name: {@code after}
	 */
	public final Map<String, String> after() {
		return this.after;
	}

	/**
	 * API name: {@code size}
	 */
	@Nullable
	public final Integer size() {
		return this.size;
	}

	/**
	 * API name: {@code sources}
	 */
	public final List<Map<String, CompositeAggregationSource>> sources() {
		return this.sources;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.after)) {
			generator.writeKey("after");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.after.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}
		if (this.size != null) {
			generator.writeKey("size");
			generator.write(this.size);

		}
		if (ApiTypeHelper.isDefined(this.sources)) {
			generator.writeKey("sources");
			generator.writeStartArray();
			for (Map<String, CompositeAggregationSource> item0 : this.sources) {
				generator.writeStartObject();
				if (item0 != null) {
					for (Map.Entry<String, CompositeAggregationSource> item1 : item0.entrySet()) {
						generator.writeKey(item1.getKey());
						item1.getValue().serialize(generator, mapper);

					}
				}
				generator.writeEnd();

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CompositeAggregation}.
	 */

	public static class Builder extends BucketAggregationBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<CompositeAggregation> {
		@Nullable
		private Map<String, String> after;

		@Nullable
		private Integer size;

		@Nullable
		private List<Map<String, CompositeAggregationSource>> sources;

		/**
		 * API name: {@code after}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>after</code>.
		 */
		public final Builder after(Map<String, String> map) {
			this.after = _mapPutAll(this.after, map);
			return this;
		}

		/**
		 * API name: {@code after}
		 * <p>
		 * Adds an entry to <code>after</code>.
		 */
		public final Builder after(String key, String value) {
			this.after = _mapPut(this.after, key, value);
			return this;
		}

		/**
		 * API name: {@code size}
		 */
		public final Builder size(@Nullable Integer value) {
			this.size = value;
			return this;
		}

		/**
		 * API name: {@code sources}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>sources</code>.
		 */
		public final Builder sources(List<Map<String, CompositeAggregationSource>> list) {
			this.sources = _listAddAll(this.sources, list);
			return this;
		}

		/**
		 * API name: {@code sources}
		 * <p>
		 * Adds one or more values to <code>sources</code>.
		 */
		public final Builder sources(Map<String, CompositeAggregationSource> value,
				Map<String, CompositeAggregationSource>... values) {
			this.sources = _listAdd(this.sources, value, values);
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link CompositeAggregation}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CompositeAggregation build() {
			_checkSingleUse();

			return new CompositeAggregation(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CompositeAggregation}
	 */
	public static final JsonpDeserializer<CompositeAggregation> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, CompositeAggregation::setupCompositeAggregationDeserializer);

	protected static void setupCompositeAggregationDeserializer(ObjectDeserializer<CompositeAggregation.Builder> op) {
		BucketAggregationBase.setupBucketAggregationBaseDeserializer(op);
		op.add(Builder::after, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()),
				"after");
		op.add(Builder::size, JsonpDeserializer.integerDeserializer(), "size");
		op.add(Builder::sources, JsonpDeserializer.arrayDeserializer(
				JsonpDeserializer.stringMapDeserializer(CompositeAggregationSource._DESERIALIZER)), "sources");

	}

}
