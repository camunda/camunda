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
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.CompositeAggregationSource

@JsonpDeserializable
public class CompositeAggregationSource implements JsonpSerializable {
	@Nullable
	private final TermsAggregation terms;

	@Nullable
	private final HistogramAggregation histogram;

	@Nullable
	private final DateHistogramAggregation dateHistogram;

	@Nullable
	private final GeoTileGridAggregation geotileGrid;

	// ---------------------------------------------------------------------------------------------

	private CompositeAggregationSource(Builder builder) {

		this.terms = builder.terms;
		this.histogram = builder.histogram;
		this.dateHistogram = builder.dateHistogram;
		this.geotileGrid = builder.geotileGrid;

	}

	public static CompositeAggregationSource of(Function<Builder, ObjectBuilder<CompositeAggregationSource>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code terms}
	 */
	@Nullable
	public final TermsAggregation terms() {
		return this.terms;
	}

	/**
	 * API name: {@code histogram}
	 */
	@Nullable
	public final HistogramAggregation histogram() {
		return this.histogram;
	}

	/**
	 * API name: {@code date_histogram}
	 */
	@Nullable
	public final DateHistogramAggregation dateHistogram() {
		return this.dateHistogram;
	}

	/**
	 * API name: {@code geotile_grid}
	 */
	@Nullable
	public final GeoTileGridAggregation geotileGrid() {
		return this.geotileGrid;
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

		if (this.terms != null) {
			generator.writeKey("terms");
			this.terms.serialize(generator, mapper);

		}
		if (this.histogram != null) {
			generator.writeKey("histogram");
			this.histogram.serialize(generator, mapper);

		}
		if (this.dateHistogram != null) {
			generator.writeKey("date_histogram");
			this.dateHistogram.serialize(generator, mapper);

		}
		if (this.geotileGrid != null) {
			generator.writeKey("geotile_grid");
			this.geotileGrid.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CompositeAggregationSource}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<CompositeAggregationSource> {
		@Nullable
		private TermsAggregation terms;

		@Nullable
		private HistogramAggregation histogram;

		@Nullable
		private DateHistogramAggregation dateHistogram;

		@Nullable
		private GeoTileGridAggregation geotileGrid;

		/**
		 * API name: {@code terms}
		 */
		public final Builder terms(@Nullable TermsAggregation value) {
			this.terms = value;
			return this;
		}

		/**
		 * API name: {@code terms}
		 */
		public final Builder terms(Function<TermsAggregation.Builder, ObjectBuilder<TermsAggregation>> fn) {
			return this.terms(fn.apply(new TermsAggregation.Builder()).build());
		}

		/**
		 * API name: {@code histogram}
		 */
		public final Builder histogram(@Nullable HistogramAggregation value) {
			this.histogram = value;
			return this;
		}

		/**
		 * API name: {@code histogram}
		 */
		public final Builder histogram(Function<HistogramAggregation.Builder, ObjectBuilder<HistogramAggregation>> fn) {
			return this.histogram(fn.apply(new HistogramAggregation.Builder()).build());
		}

		/**
		 * API name: {@code date_histogram}
		 */
		public final Builder dateHistogram(@Nullable DateHistogramAggregation value) {
			this.dateHistogram = value;
			return this;
		}

		/**
		 * API name: {@code date_histogram}
		 */
		public final Builder dateHistogram(
				Function<DateHistogramAggregation.Builder, ObjectBuilder<DateHistogramAggregation>> fn) {
			return this.dateHistogram(fn.apply(new DateHistogramAggregation.Builder()).build());
		}

		/**
		 * API name: {@code geotile_grid}
		 */
		public final Builder geotileGrid(@Nullable GeoTileGridAggregation value) {
			this.geotileGrid = value;
			return this;
		}

		/**
		 * API name: {@code geotile_grid}
		 */
		public final Builder geotileGrid(
				Function<GeoTileGridAggregation.Builder, ObjectBuilder<GeoTileGridAggregation>> fn) {
			return this.geotileGrid(fn.apply(new GeoTileGridAggregation.Builder()).build());
		}

		/**
		 * Builds a {@link CompositeAggregationSource}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CompositeAggregationSource build() {
			_checkSingleUse();

			return new CompositeAggregationSource(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CompositeAggregationSource}
	 */
	public static final JsonpDeserializer<CompositeAggregationSource> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, CompositeAggregationSource::setupCompositeAggregationSourceDeserializer);

	protected static void setupCompositeAggregationSourceDeserializer(
			ObjectDeserializer<CompositeAggregationSource.Builder> op) {

		op.add(Builder::terms, TermsAggregation._DESERIALIZER, "terms");
		op.add(Builder::histogram, HistogramAggregation._DESERIALIZER, "histogram");
		op.add(Builder::dateHistogram, DateHistogramAggregation._DESERIALIZER, "date_histogram");
		op.add(Builder::geotileGrid, GeoTileGridAggregation._DESERIALIZER, "geotile_grid");

	}

}
