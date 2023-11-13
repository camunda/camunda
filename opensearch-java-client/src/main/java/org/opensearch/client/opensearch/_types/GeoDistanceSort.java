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

package org.opensearch.client.opensearch._types;

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
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.GeoDistanceSort

@JsonpDeserializable
public class GeoDistanceSort implements SortOptionsVariant, JsonpSerializable {
	private final String field;

	private final List<GeoLocation> location;

	@Nullable
	private final SortMode mode;

	@Nullable
	private final GeoDistanceType distanceType;

	@Nullable
	private final Boolean ignoreUnmapped;

	@Nullable
	private final SortOrder order;

	@Nullable
	private final DistanceUnit unit;

	// ---------------------------------------------------------------------------------------------

	private GeoDistanceSort(Builder builder) {

		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");
		this.location = ApiTypeHelper.unmodifiableRequired(builder.location, this, "location");

		this.mode = builder.mode;
		this.distanceType = builder.distanceType;
		this.ignoreUnmapped = builder.ignoreUnmapped;
		this.order = builder.order;
		this.unit = builder.unit;

	}

	public static GeoDistanceSort of(Function<Builder, ObjectBuilder<GeoDistanceSort>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * SortOptions variant kind.
	 */
	@Override
	public SortOptions.Kind _sortOptionsKind() {
		return SortOptions.Kind.GeoDistance;
	}

	/**
	 * Required -
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * Required -
	 */
	public final List<GeoLocation> location() {
		return this.location;
	}

	/**
	 * API name: {@code mode}
	 */
	@Nullable
	public final SortMode mode() {
		return this.mode;
	}

	/**
	 * API name: {@code distance_type}
	 */
	@Nullable
	public final GeoDistanceType distanceType() {
		return this.distanceType;
	}

	/**
	 * API name: {@code ignore_unmapped}
	 */
	@Nullable
	public final Boolean ignoreUnmapped() {
		return this.ignoreUnmapped;
	}

	/**
	 * API name: {@code order}
	 */
	@Nullable
	public final SortOrder order() {
		return this.order;
	}

	/**
	 * API name: {@code unit}
	 */
	@Nullable
	public final DistanceUnit unit() {
		return this.unit;
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
		generator.writeKey(this.field);
		generator.writeStartArray();
		for (GeoLocation item0 : this.location) {
			item0.serialize(generator, mapper);

		}
		generator.writeEnd();

		if (this.mode != null) {
			generator.writeKey("mode");
			this.mode.serialize(generator, mapper);
		}
		if (this.distanceType != null) {
			generator.writeKey("distance_type");
			this.distanceType.serialize(generator, mapper);
		}
		if (this.ignoreUnmapped != null) {
			generator.writeKey("ignore_unmapped");
			generator.write(this.ignoreUnmapped);

		}
		if (this.order != null) {
			generator.writeKey("order");
			this.order.serialize(generator, mapper);
		}
		if (this.unit != null) {
			generator.writeKey("unit");
			this.unit.serialize(generator, mapper);
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GeoDistanceSort}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GeoDistanceSort> {
		private String field;

		private List<GeoLocation> location;

		/**
		 * Required -
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		/**
		 * Required - Adds all elements of <code>list</code> to <code>location</code>.
		 */
		public final Builder location(List<GeoLocation> list) {
			this.location = _listAddAll(this.location, list);
			return this;
		}

		/**
		 * Required - Adds one or more values to <code>location</code>.
		 */
		public final Builder location(GeoLocation value, GeoLocation... values) {
			this.location = _listAdd(this.location, value, values);
			return this;
		}

		/**
		 * Required - Adds a value to <code>location</code> using a builder lambda.
		 */
		public final Builder location(Function<GeoLocation.Builder, ObjectBuilder<GeoLocation>> fn) {
			return location(fn.apply(new GeoLocation.Builder()).build());
		}

		@Nullable
		private SortMode mode;

		@Nullable
		private GeoDistanceType distanceType;

		@Nullable
		private Boolean ignoreUnmapped;

		@Nullable
		private SortOrder order;

		@Nullable
		private DistanceUnit unit;

		/**
		 * API name: {@code mode}
		 */
		public final Builder mode(@Nullable SortMode value) {
			this.mode = value;
			return this;
		}

		/**
		 * API name: {@code distance_type}
		 */
		public final Builder distanceType(@Nullable GeoDistanceType value) {
			this.distanceType = value;
			return this;
		}

		/**
		 * API name: {@code ignore_unmapped}
		 */
		public final Builder ignoreUnmapped(@Nullable Boolean value) {
			this.ignoreUnmapped = value;
			return this;
		}

		/**
		 * API name: {@code order}
		 */
		public final Builder order(@Nullable SortOrder value) {
			this.order = value;
			return this;
		}

		/**
		 * API name: {@code unit}
		 */
		public final Builder unit(@Nullable DistanceUnit value) {
			this.unit = value;
			return this;
		}

		/**
		 * Builds a {@link GeoDistanceSort}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GeoDistanceSort build() {
			_checkSingleUse();

			return new GeoDistanceSort(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GeoDistanceSort}
	 */
	public static final JsonpDeserializer<GeoDistanceSort> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			GeoDistanceSort::setupGeoDistanceSortDeserializer);

	protected static void setupGeoDistanceSortDeserializer(ObjectDeserializer<GeoDistanceSort.Builder> op) {

		op.add(Builder::mode, SortMode._DESERIALIZER, "mode");
		op.add(Builder::distanceType, GeoDistanceType._DESERIALIZER, "distance_type");
		op.add(Builder::ignoreUnmapped, JsonpDeserializer.booleanDeserializer(), "ignore_unmapped");
		op.add(Builder::order, SortOrder._DESERIALIZER, "order");
		op.add(Builder::unit, DistanceUnit._DESERIALIZER, "unit");

		op.setUnknownFieldHandler((builder, name, parser, mapper) -> {
			builder.field(name);
			builder.location(
					JsonpDeserializer.arrayDeserializer(GeoLocation._DESERIALIZER).deserialize(parser, mapper));
		});

	}

}
