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

import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.GeoLineAggregation

@JsonpDeserializable
public class GeoLineAggregation implements AggregationVariant, JsonpSerializable {
	private final GeoLinePoint point;

	private final GeoLineSort sort;

	@Nullable
	private final Boolean includeSort;

	@Nullable
	private final SortOrder sortOrder;

	@Nullable
	private final Integer size;

	// ---------------------------------------------------------------------------------------------

	private GeoLineAggregation(Builder builder) {

		this.point = ApiTypeHelper.requireNonNull(builder.point, this, "point");
		this.sort = ApiTypeHelper.requireNonNull(builder.sort, this, "sort");
		this.includeSort = builder.includeSort;
		this.sortOrder = builder.sortOrder;
		this.size = builder.size;

	}

	public static GeoLineAggregation of(Function<Builder, ObjectBuilder<GeoLineAggregation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregation variant kind.
	 */
	@Override
	public Aggregation.Kind _aggregationKind() {
		return Aggregation.Kind.GeoLine;
	}

	/**
	 * Required - API name: {@code point}
	 */
	public final GeoLinePoint point() {
		return this.point;
	}

	/**
	 * Required - API name: {@code sort}
	 */
	public final GeoLineSort sort() {
		return this.sort;
	}

	/**
	 * API name: {@code include_sort}
	 */
	@Nullable
	public final Boolean includeSort() {
		return this.includeSort;
	}

	/**
	 * API name: {@code sort_order}
	 */
	@Nullable
	public final SortOrder sortOrder() {
		return this.sortOrder;
	}

	/**
	 * API name: {@code size}
	 */
	@Nullable
	public final Integer size() {
		return this.size;
	}

	/**
	 * Serialize this object to JSON.
	 */
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		serializeInternal(generator, mapper);
		generator.writeEnd();
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeKey("point");
		this.point.serialize(generator, mapper);

		generator.writeKey("sort");
		this.sort.serialize(generator, mapper);

		if (this.includeSort != null) {
			generator.writeKey("include_sort");
			generator.write(this.includeSort);

		}
		if (this.sortOrder != null) {
			generator.writeKey("sort_order");
			this.sortOrder.serialize(generator, mapper);
		}
		if (this.size != null) {
			generator.writeKey("size");
			generator.write(this.size);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GeoLineAggregation}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GeoLineAggregation> {
		private GeoLinePoint point;

		private GeoLineSort sort;

		@Nullable
		private Boolean includeSort;

		@Nullable
		private SortOrder sortOrder;

		@Nullable
		private Integer size;

		/**
		 * Required - API name: {@code point}
		 */
		public final Builder point(GeoLinePoint value) {
			this.point = value;
			return this;
		}

		/**
		 * Required - API name: {@code point}
		 */
		public final Builder point(Function<GeoLinePoint.Builder, ObjectBuilder<GeoLinePoint>> fn) {
			return this.point(fn.apply(new GeoLinePoint.Builder()).build());
		}

		/**
		 * Required - API name: {@code sort}
		 */
		public final Builder sort(GeoLineSort value) {
			this.sort = value;
			return this;
		}

		/**
		 * Required - API name: {@code sort}
		 */
		public final Builder sort(Function<GeoLineSort.Builder, ObjectBuilder<GeoLineSort>> fn) {
			return this.sort(fn.apply(new GeoLineSort.Builder()).build());
		}

		/**
		 * API name: {@code include_sort}
		 */
		public final Builder includeSort(@Nullable Boolean value) {
			this.includeSort = value;
			return this;
		}

		/**
		 * API name: {@code sort_order}
		 */
		public final Builder sortOrder(@Nullable SortOrder value) {
			this.sortOrder = value;
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
		 * Builds a {@link GeoLineAggregation}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GeoLineAggregation build() {
			_checkSingleUse();

			return new GeoLineAggregation(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GeoLineAggregation}
	 */
	public static final JsonpDeserializer<GeoLineAggregation> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, GeoLineAggregation::setupGeoLineAggregationDeserializer);

	protected static void setupGeoLineAggregationDeserializer(ObjectDeserializer<GeoLineAggregation.Builder> op) {

		op.add(Builder::point, GeoLinePoint._DESERIALIZER, "point");
		op.add(Builder::sort, GeoLineSort._DESERIALIZER, "sort");
		op.add(Builder::includeSort, JsonpDeserializer.booleanDeserializer(), "include_sort");
		op.add(Builder::sortOrder, SortOrder._DESERIALIZER, "sort_order");
		op.add(Builder::size, JsonpDeserializer.integerDeserializer(), "size");

	}

}
