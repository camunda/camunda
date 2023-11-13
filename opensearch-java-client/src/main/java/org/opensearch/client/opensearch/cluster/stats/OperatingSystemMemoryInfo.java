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

package org.opensearch.client.opensearch.cluster.stats;

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

// typedef: cluster.stats.OperatingSystemMemoryInfo


@JsonpDeserializable
public class OperatingSystemMemoryInfo implements JsonpSerializable {
	private final long freeInBytes;

	private final int freePercent;

	private final long totalInBytes;

	private final long usedInBytes;

	private final int usedPercent;

	// ---------------------------------------------------------------------------------------------

	private OperatingSystemMemoryInfo(Builder builder) {

		this.freeInBytes = ApiTypeHelper.requireNonNull(builder.freeInBytes, this, "freeInBytes");
		this.freePercent = ApiTypeHelper.requireNonNull(builder.freePercent, this, "freePercent");
		this.totalInBytes = ApiTypeHelper.requireNonNull(builder.totalInBytes, this, "totalInBytes");
		this.usedInBytes = ApiTypeHelper.requireNonNull(builder.usedInBytes, this, "usedInBytes");
		this.usedPercent = ApiTypeHelper.requireNonNull(builder.usedPercent, this, "usedPercent");

	}

	public static OperatingSystemMemoryInfo of(Function<Builder, ObjectBuilder<OperatingSystemMemoryInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code free_in_bytes}
	 */
	public final long freeInBytes() {
		return this.freeInBytes;
	}

	/**
	 * Required - API name: {@code free_percent}
	 */
	public final int freePercent() {
		return this.freePercent;
	}

	/**
	 * Required - API name: {@code total_in_bytes}
	 */
	public final long totalInBytes() {
		return this.totalInBytes;
	}

	/**
	 * Required - API name: {@code used_in_bytes}
	 */
	public final long usedInBytes() {
		return this.usedInBytes;
	}

	/**
	 * Required - API name: {@code used_percent}
	 */
	public final int usedPercent() {
		return this.usedPercent;
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

		generator.writeKey("free_in_bytes");
		generator.write(this.freeInBytes);

		generator.writeKey("free_percent");
		generator.write(this.freePercent);

		generator.writeKey("total_in_bytes");
		generator.write(this.totalInBytes);

		generator.writeKey("used_in_bytes");
		generator.write(this.usedInBytes);

		generator.writeKey("used_percent");
		generator.write(this.usedPercent);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link OperatingSystemMemoryInfo}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<OperatingSystemMemoryInfo> {
		private Long freeInBytes;

		private Integer freePercent;

		private Long totalInBytes;

		private Long usedInBytes;

		private Integer usedPercent;

		/**
		 * Required - API name: {@code free_in_bytes}
		 */
		public final Builder freeInBytes(long value) {
			this.freeInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code free_percent}
		 */
		public final Builder freePercent(int value) {
			this.freePercent = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_in_bytes}
		 */
		public final Builder totalInBytes(long value) {
			this.totalInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code used_in_bytes}
		 */
		public final Builder usedInBytes(long value) {
			this.usedInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code used_percent}
		 */
		public final Builder usedPercent(int value) {
			this.usedPercent = value;
			return this;
		}

		/**
		 * Builds a {@link OperatingSystemMemoryInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public OperatingSystemMemoryInfo build() {
			_checkSingleUse();

			return new OperatingSystemMemoryInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link OperatingSystemMemoryInfo}
	 */
	public static final JsonpDeserializer<OperatingSystemMemoryInfo> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, OperatingSystemMemoryInfo::setupOperatingSystemMemoryInfoDeserializer);

	protected static void setupOperatingSystemMemoryInfoDeserializer(
			ObjectDeserializer<OperatingSystemMemoryInfo.Builder> op) {

		op.add(Builder::freeInBytes, JsonpDeserializer.longDeserializer(), "free_in_bytes");
		op.add(Builder::freePercent, JsonpDeserializer.integerDeserializer(), "free_percent");
		op.add(Builder::totalInBytes, JsonpDeserializer.longDeserializer(), "total_in_bytes");
		op.add(Builder::usedInBytes, JsonpDeserializer.longDeserializer(), "used_in_bytes");
		op.add(Builder::usedPercent, JsonpDeserializer.integerDeserializer(), "used_percent");

	}

}
