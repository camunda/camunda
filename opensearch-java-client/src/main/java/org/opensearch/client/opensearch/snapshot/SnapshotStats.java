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

// typedef: snapshot._types.SnapshotStats


@JsonpDeserializable
public class SnapshotStats implements JsonpSerializable {
	private final FileCountSnapshotStats incremental;

	private final long startTimeInMillis;

	private final long timeInMillis;

	private final FileCountSnapshotStats total;

	// ---------------------------------------------------------------------------------------------

	private SnapshotStats(Builder builder) {

		this.incremental = ApiTypeHelper.requireNonNull(builder.incremental, this, "incremental");
		this.startTimeInMillis = ApiTypeHelper.requireNonNull(builder.startTimeInMillis, this, "startTimeInMillis");
		this.timeInMillis = ApiTypeHelper.requireNonNull(builder.timeInMillis, this, "timeInMillis");
		this.total = ApiTypeHelper.requireNonNull(builder.total, this, "total");

	}

	public static SnapshotStats of(Function<Builder, ObjectBuilder<SnapshotStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code incremental}
	 */
	public final FileCountSnapshotStats incremental() {
		return this.incremental;
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
	 * Required - API name: {@code total}
	 */
	public final FileCountSnapshotStats total() {
		return this.total;
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

		generator.writeKey("start_time_in_millis");
		generator.write(this.startTimeInMillis);

		generator.writeKey("time_in_millis");
		generator.write(this.timeInMillis);

		generator.writeKey("total");
		this.total.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SnapshotStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<SnapshotStats> {
		private FileCountSnapshotStats incremental;

		private Long startTimeInMillis;

		private Long timeInMillis;

		private FileCountSnapshotStats total;

		/**
		 * Required - API name: {@code incremental}
		 */
		public final Builder incremental(FileCountSnapshotStats value) {
			this.incremental = value;
			return this;
		}

		/**
		 * Required - API name: {@code incremental}
		 */
		public final Builder incremental(
				Function<FileCountSnapshotStats.Builder, ObjectBuilder<FileCountSnapshotStats>> fn) {
			return this.incremental(fn.apply(new FileCountSnapshotStats.Builder()).build());
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
		 * Required - API name: {@code total}
		 */
		public final Builder total(FileCountSnapshotStats value) {
			this.total = value;
			return this;
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(Function<FileCountSnapshotStats.Builder, ObjectBuilder<FileCountSnapshotStats>> fn) {
			return this.total(fn.apply(new FileCountSnapshotStats.Builder()).build());
		}

		/**
		 * Builds a {@link SnapshotStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public SnapshotStats build() {
			_checkSingleUse();

			return new SnapshotStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link SnapshotStats}
	 */
	public static final JsonpDeserializer<SnapshotStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			SnapshotStats::setupSnapshotStatsDeserializer);

	protected static void setupSnapshotStatsDeserializer(ObjectDeserializer<SnapshotStats.Builder> op) {

		op.add(Builder::incremental, FileCountSnapshotStats._DESERIALIZER, "incremental");
		op.add(Builder::startTimeInMillis, JsonpDeserializer.longDeserializer(), "start_time_in_millis");
		op.add(Builder::timeInMillis, JsonpDeserializer.longDeserializer(), "time_in_millis");
		op.add(Builder::total, FileCountSnapshotStats._DESERIALIZER, "total");

	}

}
