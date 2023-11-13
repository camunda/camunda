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

package org.opensearch.client.opensearch.indices;

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

// typedef: indices._types.FielddataFrequencyFilter

@JsonpDeserializable
public class FielddataFrequencyFilter implements JsonpSerializable {
	private final double max;

	private final double min;

	private final int minSegmentSize;

	// ---------------------------------------------------------------------------------------------

	private FielddataFrequencyFilter(Builder builder) {

		this.max = ApiTypeHelper.requireNonNull(builder.max, this, "max");
		this.min = ApiTypeHelper.requireNonNull(builder.min, this, "min");
		this.minSegmentSize = ApiTypeHelper.requireNonNull(builder.minSegmentSize, this, "minSegmentSize");

	}

	public static FielddataFrequencyFilter of(Function<Builder, ObjectBuilder<FielddataFrequencyFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code max}
	 */
	public final double max() {
		return this.max;
	}

	/**
	 * Required - API name: {@code min}
	 */
	public final double min() {
		return this.min;
	}

	/**
	 * Required - API name: {@code min_segment_size}
	 */
	public final int minSegmentSize() {
		return this.minSegmentSize;
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

		generator.writeKey("max");
		generator.write(this.max);

		generator.writeKey("min");
		generator.write(this.min);

		generator.writeKey("min_segment_size");
		generator.write(this.minSegmentSize);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link FielddataFrequencyFilter}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<FielddataFrequencyFilter> {
		private Double max;

		private Double min;

		private Integer minSegmentSize;

		/**
		 * Required - API name: {@code max}
		 */
		public final Builder max(double value) {
			this.max = value;
			return this;
		}

		/**
		 * Required - API name: {@code min}
		 */
		public final Builder min(double value) {
			this.min = value;
			return this;
		}

		/**
		 * Required - API name: {@code min_segment_size}
		 */
		public final Builder minSegmentSize(int value) {
			this.minSegmentSize = value;
			return this;
		}

		/**
		 * Builds a {@link FielddataFrequencyFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public FielddataFrequencyFilter build() {
			_checkSingleUse();

			return new FielddataFrequencyFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link FielddataFrequencyFilter}
	 */
	public static final JsonpDeserializer<FielddataFrequencyFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, FielddataFrequencyFilter::setupFielddataFrequencyFilterDeserializer);

	protected static void setupFielddataFrequencyFilterDeserializer(
			ObjectDeserializer<FielddataFrequencyFilter.Builder> op) {

		op.add(Builder::max, JsonpDeserializer.doubleDeserializer(), "max");
		op.add(Builder::min, JsonpDeserializer.doubleDeserializer(), "min");
		op.add(Builder::minSegmentSize, JsonpDeserializer.integerDeserializer(), "min_segment_size");

	}

}
