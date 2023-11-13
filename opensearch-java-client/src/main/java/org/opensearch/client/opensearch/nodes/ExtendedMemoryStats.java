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

package org.opensearch.client.opensearch.nodes;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: nodes._types.ExtendedMemoryStats

@JsonpDeserializable
public class ExtendedMemoryStats extends MemoryStats {
	private final int freePercent;

	private final int usedPercent;

	// ---------------------------------------------------------------------------------------------

	private ExtendedMemoryStats(Builder builder) {
		super(builder);

		this.freePercent = ApiTypeHelper.requireNonNull(builder.freePercent, this, "freePercent");
		this.usedPercent = ApiTypeHelper.requireNonNull(builder.usedPercent, this, "usedPercent");

	}

	public static ExtendedMemoryStats of(Function<Builder, ObjectBuilder<ExtendedMemoryStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code free_percent}
	 */
	public final int freePercent() {
		return this.freePercent;
	}

	/**
	 * Required - API name: {@code used_percent}
	 */
	public final int usedPercent() {
		return this.usedPercent;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("free_percent");
		generator.write(this.freePercent);

		generator.writeKey("used_percent");
		generator.write(this.usedPercent);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ExtendedMemoryStats}.
	 */

	public static class Builder extends MemoryStats.AbstractBuilder<Builder>
			implements
				ObjectBuilder<ExtendedMemoryStats> {
		private Integer freePercent;

		private Integer usedPercent;

		/**
		 * Required - API name: {@code free_percent}
		 */
		public final Builder freePercent(int value) {
			this.freePercent = value;
			return this;
		}

		/**
		 * Required - API name: {@code used_percent}
		 */
		public final Builder usedPercent(int value) {
			this.usedPercent = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link ExtendedMemoryStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ExtendedMemoryStats build() {
			_checkSingleUse();

			return new ExtendedMemoryStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ExtendedMemoryStats}
	 */
	public static final JsonpDeserializer<ExtendedMemoryStats> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ExtendedMemoryStats::setupExtendedMemoryStatsDeserializer);

	protected static void setupExtendedMemoryStatsDeserializer(ObjectDeserializer<ExtendedMemoryStats.Builder> op) {
		setupMemoryStatsDeserializer(op);
		op.add(Builder::freePercent, JsonpDeserializer.integerDeserializer(), "free_percent");
		op.add(Builder::usedPercent, JsonpDeserializer.integerDeserializer(), "used_percent");

	}

}
