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
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: nodes._types.AdaptiveSelection


@JsonpDeserializable
public class AdaptiveSelection implements JsonpSerializable {
	private final long avgQueueSize;

	private final long avgResponseTimeNs;

	private final long avgServiceTimeNs;

	private final long outgoingSearches;

	private final String rank;

	// ---------------------------------------------------------------------------------------------

	private AdaptiveSelection(Builder builder) {

		this.avgQueueSize = ApiTypeHelper.requireNonNull(builder.avgQueueSize, this, "avgQueueSize");
		this.avgResponseTimeNs = ApiTypeHelper.requireNonNull(builder.avgResponseTimeNs, this, "avgResponseTimeNs");
		this.avgServiceTimeNs = ApiTypeHelper.requireNonNull(builder.avgServiceTimeNs, this, "avgServiceTimeNs");
		this.outgoingSearches = ApiTypeHelper.requireNonNull(builder.outgoingSearches, this, "outgoingSearches");
		this.rank = ApiTypeHelper.requireNonNull(builder.rank, this, "rank");

	}

	public static AdaptiveSelection of(Function<Builder, ObjectBuilder<AdaptiveSelection>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code avg_queue_size}
	 */
	public final long avgQueueSize() {
		return this.avgQueueSize;
	}

	/**
	 * Required - API name: {@code avg_response_time_ns}
	 */
	public final long avgResponseTimeNs() {
		return this.avgResponseTimeNs;
	}

	/**
	 * Required - API name: {@code avg_service_time_ns}
	 */
	public final long avgServiceTimeNs() {
		return this.avgServiceTimeNs;
	}

	/**
	 * Required - API name: {@code outgoing_searches}
	 */
	public final long outgoingSearches() {
		return this.outgoingSearches;
	}

	/**
	 * Required - API name: {@code rank}
	 */
	public final String rank() {
		return this.rank;
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

		generator.writeKey("avg_queue_size");
		generator.write(this.avgQueueSize);

		generator.writeKey("avg_response_time_ns");
		generator.write(this.avgResponseTimeNs);

		generator.writeKey("avg_service_time_ns");
		generator.write(this.avgServiceTimeNs);

		generator.writeKey("outgoing_searches");
		generator.write(this.outgoingSearches);

		generator.writeKey("rank");
		generator.write(this.rank);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link AdaptiveSelection}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<AdaptiveSelection> {
		private Long avgQueueSize;

		private Long avgResponseTimeNs;

		private Long avgServiceTimeNs;

		private Long outgoingSearches;

		private String rank;

		/**
		 * Required - API name: {@code avg_queue_size}
		 */
		public final Builder avgQueueSize(long value) {
			this.avgQueueSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code avg_response_time_ns}
		 */
		public final Builder avgResponseTimeNs(long value) {
			this.avgResponseTimeNs = value;
			return this;
		}

		/**
		 * Required - API name: {@code avg_service_time_ns}
		 */
		public final Builder avgServiceTimeNs(long value) {
			this.avgServiceTimeNs = value;
			return this;
		}

		/**
		 * Required - API name: {@code outgoing_searches}
		 */
		public final Builder outgoingSearches(long value) {
			this.outgoingSearches = value;
			return this;
		}

		/**
		 * Required - API name: {@code rank}
		 */
		public final Builder rank(String value) {
			this.rank = value;
			return this;
		}

		/**
		 * Builds a {@link AdaptiveSelection}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public AdaptiveSelection build() {
			_checkSingleUse();

			return new AdaptiveSelection(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link AdaptiveSelection}
	 */
	public static final JsonpDeserializer<AdaptiveSelection> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, AdaptiveSelection::setupAdaptiveSelectionDeserializer);

	protected static void setupAdaptiveSelectionDeserializer(ObjectDeserializer<AdaptiveSelection.Builder> op) {

		op.add(Builder::avgQueueSize, JsonpDeserializer.longDeserializer(), "avg_queue_size");
		op.add(Builder::avgResponseTimeNs, JsonpDeserializer.longDeserializer(), "avg_response_time_ns");
		op.add(Builder::avgServiceTimeNs, JsonpDeserializer.longDeserializer(), "avg_service_time_ns");
		op.add(Builder::outgoingSearches, JsonpDeserializer.longDeserializer(), "outgoing_searches");
		op.add(Builder::rank, JsonpDeserializer.stringDeserializer(), "rank");

	}

}
