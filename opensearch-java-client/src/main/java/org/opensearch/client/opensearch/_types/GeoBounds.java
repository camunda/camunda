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
import org.opensearch.client.json.UnionDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: _types.GeoBounds

/**
 * A geo bounding box. It can be represented in various ways:
 * <ul>
 * <li>as 4 top/bottom/left/right coordinates</li>
 * <li>as 2 top_left / bottom_right points</li>
 * <li>as 2 top_right / bottom_left points</li>
 * <li>as a WKT bounding box</li>
 * </ul>
 *
 */
@JsonpDeserializable
public class GeoBounds implements TaggedUnion<GeoBounds.Kind, Object>, JsonpSerializable {

	public enum Kind {
		Coords, Tlbr, Trbl, Wkt

	}

	private final Kind _kind;
	private final Object _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final Object _get() {
		return _value;
	}

	private GeoBounds(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	private GeoBounds(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static GeoBounds of(Function<Builder, ObjectBuilder<GeoBounds>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code coords}?
	 */
	public boolean isCoords() {
		return _kind == Kind.Coords;
	}

	/**
	 * Get the {@code coords} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code coords} kind.
	 */
	public CoordsGeoBounds coords() {
		return TaggedUnionUtils.get(this, Kind.Coords);
	}

	/**
	 * Is this variant instance of kind {@code tlbr}?
	 */
	public boolean isTlbr() {
		return _kind == Kind.Tlbr;
	}

	/**
	 * Get the {@code tlbr} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code tlbr} kind.
	 */
	public TopLeftBottomRightGeoBounds tlbr() {
		return TaggedUnionUtils.get(this, Kind.Tlbr);
	}

	/**
	 * Is this variant instance of kind {@code trbl}?
	 */
	public boolean isTrbl() {
		return _kind == Kind.Trbl;
	}

	/**
	 * Get the {@code trbl} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code trbl} kind.
	 */
	public TopRightBottomLeftGeoBounds trbl() {
		return TaggedUnionUtils.get(this, Kind.Trbl);
	}

	/**
	 * Is this variant instance of kind {@code wkt}?
	 */
	public boolean isWkt() {
		return _kind == Kind.Wkt;
	}

	/**
	 * Get the {@code wkt} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code wkt} kind.
	 */
	public WktGeoBounds wkt() {
		return TaggedUnionUtils.get(this, Kind.Wkt);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		}

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GeoBounds> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<GeoBounds> coords(CoordsGeoBounds v) {
			this._kind = Kind.Coords;
			this._value = v;
			return this;
		}

		public ObjectBuilder<GeoBounds> coords(Function<CoordsGeoBounds.Builder, ObjectBuilder<CoordsGeoBounds>> fn) {
			return this.coords(fn.apply(new CoordsGeoBounds.Builder()).build());
		}

		public ObjectBuilder<GeoBounds> tlbr(TopLeftBottomRightGeoBounds v) {
			this._kind = Kind.Tlbr;
			this._value = v;
			return this;
		}

		public ObjectBuilder<GeoBounds> tlbr(
				Function<TopLeftBottomRightGeoBounds.Builder, ObjectBuilder<TopLeftBottomRightGeoBounds>> fn) {
			return this.tlbr(fn.apply(new TopLeftBottomRightGeoBounds.Builder()).build());
		}

		public ObjectBuilder<GeoBounds> trbl(TopRightBottomLeftGeoBounds v) {
			this._kind = Kind.Trbl;
			this._value = v;
			return this;
		}

		public ObjectBuilder<GeoBounds> trbl(
				Function<TopRightBottomLeftGeoBounds.Builder, ObjectBuilder<TopRightBottomLeftGeoBounds>> fn) {
			return this.trbl(fn.apply(new TopRightBottomLeftGeoBounds.Builder()).build());
		}

		public ObjectBuilder<GeoBounds> wkt(WktGeoBounds v) {
			this._kind = Kind.Wkt;
			this._value = v;
			return this;
		}

		public ObjectBuilder<GeoBounds> wkt(Function<WktGeoBounds.Builder, ObjectBuilder<WktGeoBounds>> fn) {
			return this.wkt(fn.apply(new WktGeoBounds.Builder()).build());
		}

		public GeoBounds build() {
			_checkSingleUse();
			return new GeoBounds(this);
		}

	}

	private static JsonpDeserializer<GeoBounds> buildGeoBoundsDeserializer() {
		return new UnionDeserializer.Builder<GeoBounds, Kind, Object>(GeoBounds::new, false)
				.addMember(Kind.Coords, CoordsGeoBounds._DESERIALIZER)
				.addMember(Kind.Tlbr, TopLeftBottomRightGeoBounds._DESERIALIZER)
				.addMember(Kind.Trbl, TopRightBottomLeftGeoBounds._DESERIALIZER)
				.addMember(Kind.Wkt, WktGeoBounds._DESERIALIZER).build();
	}

	public static final JsonpDeserializer<GeoBounds> _DESERIALIZER = JsonpDeserializer
			.lazy(GeoBounds::buildGeoBoundsDeserializer);
}
