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

// typedef: _types.aggregations.IpRangeAggregationRange

@JsonpDeserializable
public class IpRangeAggregationRange implements JsonpSerializable {
	@Nullable
	private final String from;

	@Nullable
	private final String mask;

	@Nullable
	private final String to;

	// ---------------------------------------------------------------------------------------------

	private IpRangeAggregationRange(Builder builder) {

		this.from = builder.from;
		this.mask = builder.mask;
		this.to = builder.to;

	}

	public static IpRangeAggregationRange of(Function<Builder, ObjectBuilder<IpRangeAggregationRange>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code from}
	 */
	@Nullable
	public final String from() {
		return this.from;
	}

	/**
	 * API name: {@code mask}
	 */
	@Nullable
	public final String mask() {
		return this.mask;
	}

	/**
	 * API name: {@code to}
	 */
	@Nullable
	public final String to() {
		return this.to;
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

		if (this.from != null) {
			generator.writeKey("from");
			generator.write(this.from);

		}
		if (this.mask != null) {
			generator.writeKey("mask");
			generator.write(this.mask);

		}
		if (this.to != null) {
			generator.writeKey("to");
			generator.write(this.to);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IpRangeAggregationRange}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IpRangeAggregationRange> {
		@Nullable
		private String from;

		@Nullable
		private String mask;

		@Nullable
		private String to;

		/**
		 * API name: {@code from}
		 */
		public final Builder from(@Nullable String value) {
			this.from = value;
			return this;
		}

		/**
		 * API name: {@code mask}
		 */
		public final Builder mask(@Nullable String value) {
			this.mask = value;
			return this;
		}

		/**
		 * API name: {@code to}
		 */
		public final Builder to(@Nullable String value) {
			this.to = value;
			return this;
		}

		/**
		 * Builds a {@link IpRangeAggregationRange}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IpRangeAggregationRange build() {
			_checkSingleUse();

			return new IpRangeAggregationRange(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IpRangeAggregationRange}
	 */
	public static final JsonpDeserializer<IpRangeAggregationRange> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, IpRangeAggregationRange::setupIpRangeAggregationRangeDeserializer);

	protected static void setupIpRangeAggregationRangeDeserializer(
			ObjectDeserializer<IpRangeAggregationRange.Builder> op) {

		op.add(Builder::from, JsonpDeserializer.stringDeserializer(), "from");
		op.add(Builder::mask, JsonpDeserializer.stringDeserializer(), "mask");
		op.add(Builder::to, JsonpDeserializer.stringDeserializer(), "to");

	}

}
