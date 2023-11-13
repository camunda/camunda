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

package org.opensearch.client.opensearch._types.query_dsl;

import org.opensearch.client.opensearch._types.GeoLocation;
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

// typedef: _types.query_dsl.GeoPolygonPoints


@JsonpDeserializable
public class GeoPolygonPoints implements JsonpSerializable {
	private final List<GeoLocation> points;

	// ---------------------------------------------------------------------------------------------

	private GeoPolygonPoints(Builder builder) {

		this.points = ApiTypeHelper.unmodifiableRequired(builder.points, this, "points");

	}

	public static GeoPolygonPoints of(Function<Builder, ObjectBuilder<GeoPolygonPoints>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code points}
	 */
	public final List<GeoLocation> points() {
		return this.points;
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

		if (ApiTypeHelper.isDefined(this.points)) {
			generator.writeKey("points");
			generator.writeStartArray();
			for (GeoLocation item0 : this.points) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GeoPolygonPoints}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GeoPolygonPoints> {
		private List<GeoLocation> points;

		/**
		 * Required - API name: {@code points}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>points</code>.
		 */
		public final Builder points(List<GeoLocation> list) {
			this.points = _listAddAll(this.points, list);
			return this;
		}

		/**
		 * Required - API name: {@code points}
		 * <p>
		 * Adds one or more values to <code>points</code>.
		 */
		public final Builder points(GeoLocation value, GeoLocation... values) {
			this.points = _listAdd(this.points, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code points}
		 * <p>
		 * Adds a value to <code>points</code> using a builder lambda.
		 */
		public final Builder points(Function<GeoLocation.Builder, ObjectBuilder<GeoLocation>> fn) {
			return points(fn.apply(new GeoLocation.Builder()).build());
		}

		/**
		 * Builds a {@link GeoPolygonPoints}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GeoPolygonPoints build() {
			_checkSingleUse();

			return new GeoPolygonPoints(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GeoPolygonPoints}
	 */
	public static final JsonpDeserializer<GeoPolygonPoints> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			GeoPolygonPoints::setupGeoPolygonPointsDeserializer);

	protected static void setupGeoPolygonPointsDeserializer(ObjectDeserializer<GeoPolygonPoints.Builder> op) {

		op.add(Builder::points, JsonpDeserializer.arrayDeserializer(GeoLocation._DESERIALIZER), "points");

	}

}
