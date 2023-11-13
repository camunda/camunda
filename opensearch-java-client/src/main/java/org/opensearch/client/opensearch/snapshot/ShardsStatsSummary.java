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

package org.opensearch.client.opensearch.snapshot;

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

// typedef: snapshot._types.ShardsStatsSummary

@JsonpDeserializable
public class ShardsStatsSummary implements JsonpSerializable {
	private final ShardsStatsSummaryItem incremental;

	private final ShardsStatsSummaryItem total;

	private final long startTimeInMillis;

	private final long timeInMillis;

	// ---------------------------------------------------------------------------------------------

	private ShardsStatsSummary(Builder builder) {

		this.incremental = ApiTypeHelper.requireNonNull(builder.incremental, this, "incremental");
		this.total = ApiTypeHelper.requireNonNull(builder.total, this, "total");
		this.startTimeInMillis = ApiTypeHelper.requireNonNull(builder.startTimeInMillis, this, "startTimeInMillis");
		this.timeInMillis = ApiTypeHelper.requireNonNull(builder.timeInMillis, this, "timeInMillis");

	}

	public static ShardsStatsSummary of(Function<Builder, ObjectBuilder<ShardsStatsSummary>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code incremental}
	 */
	public final ShardsStatsSummaryItem incremental() {
		return this.incremental;
	}

	/**
	 * Required - API name: {@code total}
	 */
	public final ShardsStatsSummaryItem total() {
		return this.total;
	}

	/**
	 * Required - API name: {@code start_time_in_millis}
	 */
	public final long startTimeInMillis() {
		return this.startTimeInMillis;
	}

	/**
	 * Required - API name: {@code time_in_millis}
	 */
	public final long timeInMillis() {
		return this.timeInMillis;
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

		generator.writeKey("incremental");
		this.incremental.serialize(generator, mapper);

		generator.writeKey("total");
		this.total.serialize(generator, mapper);

		generator.writeKey("start_time_in_millis");
		generator.write(this.startTimeInMillis);

		generator.writeKey("time_in_millis");
		generator.write(this.timeInMillis);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ShardsStatsSummary}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ShardsStatsSummary> {
		private ShardsStatsSummaryItem incremental;

		private ShardsStatsSummaryItem total;

		private Long startTimeInMillis;

		private Long timeInMillis;

		/**
		 * Required - API name: {@code incremental}
		 */
		public final Builder incremental(ShardsStatsSummaryItem value) {
			this.incremental = value;
			return this;
		}

		/**
		 * Required - API name: {@code incremental}
		 */
		public final Builder incremental(
				Function<ShardsStatsSummaryItem.Builder, ObjectBuilder<ShardsStatsSummaryItem>> fn) {
			return this.incremental(fn.apply(new ShardsStatsSummaryItem.Builder()).build());
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(ShardsStatsSummaryItem value) {
			this.total = value;
			return this;
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(Function<ShardsStatsSummaryItem.Builder, ObjectBuilder<ShardsStatsSummaryItem>> fn) {
			return this.total(fn.apply(new ShardsStatsSummaryItem.Builder()).build());
		}

		/**
		 * Required - API name: {@code start_time_in_millis}
		 */
		public final Builder startTimeInMillis(long value) {
			this.startTimeInMillis = value;
			return this;
		}

		/**
		 * Required - API name: {@code time_in_millis}
		 */
		public final Builder timeInMillis(long value) {
			this.timeInMillis = value;
			return this;
		}

		/**
		 * Builds a {@link ShardsStatsSummary}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ShardsStatsSummary build() {
			_checkSingleUse();

			return new ShardsStatsSummary(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ShardsStatsSummary}
	 */
	public static final JsonpDeserializer<ShardsStatsSummary> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ShardsStatsSummary::setupShardsStatsSummaryDeserializer);

	protected static void setupShardsStatsSummaryDeserializer(ObjectDeserializer<ShardsStatsSummary.Builder> op) {

		op.add(Builder::incremental, ShardsStatsSummaryItem._DESERIALIZER, "incremental");
		op.add(Builder::total, ShardsStatsSummaryItem._DESERIALIZER, "total");
		op.add(Builder::startTimeInMillis, JsonpDeserializer.longDeserializer(), "start_time_in_millis");
		op.add(Builder::timeInMillis, JsonpDeserializer.longDeserializer(), "time_in_millis");

	}

}
