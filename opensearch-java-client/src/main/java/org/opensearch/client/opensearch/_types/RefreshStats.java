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
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.RefreshStats


@JsonpDeserializable
public class RefreshStats implements JsonpSerializable {
	private final long externalTotal;

	private final long externalTotalTimeInMillis;

	private final long listeners;

	private final long total;

	@Nullable
	private final String totalTime;

	private final long totalTimeInMillis;

	// ---------------------------------------------------------------------------------------------

	private RefreshStats(Builder builder) {

		this.externalTotal = ApiTypeHelper.requireNonNull(builder.externalTotal, this, "externalTotal");
		this.externalTotalTimeInMillis = ApiTypeHelper.requireNonNull(builder.externalTotalTimeInMillis, this,
				"externalTotalTimeInMillis");
		this.listeners = ApiTypeHelper.requireNonNull(builder.listeners, this, "listeners");
		this.total = ApiTypeHelper.requireNonNull(builder.total, this, "total");
		this.totalTime = builder.totalTime;
		this.totalTimeInMillis = ApiTypeHelper.requireNonNull(builder.totalTimeInMillis, this, "totalTimeInMillis");

	}

	public static RefreshStats of(Function<Builder, ObjectBuilder<RefreshStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code external_total}
	 */
	public final long externalTotal() {
		return this.externalTotal;
	}

	/**
	 * Required - API name: {@code external_total_time_in_millis}
	 */
	public final long externalTotalTimeInMillis() {
		return this.externalTotalTimeInMillis;
	}

	/**
	 * Required - API name: {@code listeners}
	 */
	public final long listeners() {
		return this.listeners;
	}

	/**
	 * Required - API name: {@code total}
	 */
	public final long total() {
		return this.total;
	}

	/**
	 * API name: {@code total_time}
	 */
	@Nullable
	public final String totalTime() {
		return this.totalTime;
	}

	/**
	 * Required - API name: {@code total_time_in_millis}
	 */
	public final long totalTimeInMillis() {
		return this.totalTimeInMillis;
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

		generator.writeKey("external_total");
		generator.write(this.externalTotal);

		generator.writeKey("external_total_time_in_millis");
		generator.write(this.externalTotalTimeInMillis);

		generator.writeKey("listeners");
		generator.write(this.listeners);

		generator.writeKey("total");
		generator.write(this.total);

		if (this.totalTime != null) {
			generator.writeKey("total_time");
			generator.write(this.totalTime);

		}
		generator.writeKey("total_time_in_millis");
		generator.write(this.totalTimeInMillis);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RefreshStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RefreshStats> {
		private Long externalTotal;

		private Long externalTotalTimeInMillis;

		private Long listeners;

		private Long total;

		@Nullable
		private String totalTime;

		private Long totalTimeInMillis;

		/**
		 * Required - API name: {@code external_total}
		 */
		public final Builder externalTotal(long value) {
			this.externalTotal = value;
			return this;
		}

		/**
		 * Required - API name: {@code external_total_time_in_millis}
		 */
		public final Builder externalTotalTimeInMillis(long value) {
			this.externalTotalTimeInMillis = value;
			return this;
		}

		/**
		 * Required - API name: {@code listeners}
		 */
		public final Builder listeners(long value) {
			this.listeners = value;
			return this;
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(long value) {
			this.total = value;
			return this;
		}

		/**
		 * API name: {@code total_time}
		 */
		public final Builder totalTime(@Nullable String value) {
			this.totalTime = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_time_in_millis}
		 */
		public final Builder totalTimeInMillis(long value) {
			this.totalTimeInMillis = value;
			return this;
		}

		/**
		 * Builds a {@link RefreshStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RefreshStats build() {
			_checkSingleUse();

			return new RefreshStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RefreshStats}
	 */
	public static final JsonpDeserializer<RefreshStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			RefreshStats::setupRefreshStatsDeserializer);

	protected static void setupRefreshStatsDeserializer(ObjectDeserializer<RefreshStats.Builder> op) {

		op.add(Builder::externalTotal, JsonpDeserializer.longDeserializer(), "external_total");
		op.add(Builder::externalTotalTimeInMillis, JsonpDeserializer.longDeserializer(),
				"external_total_time_in_millis");
		op.add(Builder::listeners, JsonpDeserializer.longDeserializer(), "listeners");
		op.add(Builder::total, JsonpDeserializer.longDeserializer(), "total");
		op.add(Builder::totalTime, JsonpDeserializer.stringDeserializer(), "total_time");
		op.add(Builder::totalTimeInMillis, JsonpDeserializer.longDeserializer(), "total_time_in_millis");

	}

}
