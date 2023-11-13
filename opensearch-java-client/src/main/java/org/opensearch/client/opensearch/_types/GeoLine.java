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

// typedef: _types.GeoLine

/**
 * A GeoJson GeoLine.
 * 
 */
@JsonpDeserializable
public class GeoLine implements JsonpSerializable {
	private final String type;

	private final List<List<Double>> coordinates;

	// ---------------------------------------------------------------------------------------------

	private GeoLine(Builder builder) {

		this.type = ApiTypeHelper.requireNonNull(builder.type, this, "type");
		this.coordinates = ApiTypeHelper.unmodifiableRequired(builder.coordinates, this, "coordinates");

	}

	public static GeoLine of(Function<Builder, ObjectBuilder<GeoLine>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - Always <code>&quot;LineString&quot;</code>
	 * <p>
	 * API name: {@code type}
	 */
	public final String type() {
		return this.type;
	}

	/**
	 * Required - Array of <code>[lon, lat]</code> coordinates
	 * <p>
	 * API name: {@code coordinates}
	 */
	public final List<List<Double>> coordinates() {
		return this.coordinates;
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

		generator.writeKey("type");
		generator.write(this.type);

		if (ApiTypeHelper.isDefined(this.coordinates)) {
			generator.writeKey("coordinates");
			generator.writeStartArray();
			for (List<Double> item0 : this.coordinates) {
				generator.writeStartArray();
				if (item0 != null) {
					for (Double item1 : item0) {
						generator.write(item1);

					}
				}
				generator.writeEnd();

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GeoLine}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GeoLine> {
		private String type;

		private List<List<Double>> coordinates;

		/**
		 * Required - Always <code>&quot;LineString&quot;</code>
		 * <p>
		 * API name: {@code type}
		 */
		public final Builder type(String value) {
			this.type = value;
			return this;
		}

		/**
		 * Required - Array of <code>[lon, lat]</code> coordinates
		 * <p>
		 * API name: {@code coordinates}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>coordinates</code>.
		 */
		public final Builder coordinates(List<List<Double>> list) {
			this.coordinates = _listAddAll(this.coordinates, list);
			return this;
		}

		/**
		 * Required - Array of <code>[lon, lat]</code> coordinates
		 * <p>
		 * API name: {@code coordinates}
		 * <p>
		 * Adds one or more values to <code>coordinates</code>.
		 */
		public final Builder coordinates(List<Double> value, List<Double>... values) {
			this.coordinates = _listAdd(this.coordinates, value, values);
			return this;
		}

		/**
		 * Builds a {@link GeoLine}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GeoLine build() {
			_checkSingleUse();

			return new GeoLine(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GeoLine}
	 */
	public static final JsonpDeserializer<GeoLine> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			GeoLine::setupGeoLineDeserializer);

	protected static void setupGeoLineDeserializer(ObjectDeserializer<GeoLine.Builder> op) {

		op.add(Builder::type, JsonpDeserializer.stringDeserializer(), "type");
		op.add(Builder::coordinates, JsonpDeserializer.arrayDeserializer(
				JsonpDeserializer.arrayDeserializer(JsonpDeserializer.doubleDeserializer())), "coordinates");

	}

}
