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

package org.opensearch.client.opensearch.cluster.allocation_explain;

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

// typedef: cluster.allocation_explain.DiskUsage


@JsonpDeserializable
public class DiskUsage implements JsonpSerializable {
	private final String path;

	private final long totalBytes;

	private final long usedBytes;

	private final long freeBytes;

	private final double freeDiskPercent;

	private final double usedDiskPercent;

	// ---------------------------------------------------------------------------------------------

	private DiskUsage(Builder builder) {

		this.path = ApiTypeHelper.requireNonNull(builder.path, this, "path");
		this.totalBytes = ApiTypeHelper.requireNonNull(builder.totalBytes, this, "totalBytes");
		this.usedBytes = ApiTypeHelper.requireNonNull(builder.usedBytes, this, "usedBytes");
		this.freeBytes = ApiTypeHelper.requireNonNull(builder.freeBytes, this, "freeBytes");
		this.freeDiskPercent = ApiTypeHelper.requireNonNull(builder.freeDiskPercent, this, "freeDiskPercent");
		this.usedDiskPercent = ApiTypeHelper.requireNonNull(builder.usedDiskPercent, this, "usedDiskPercent");

	}

	public static DiskUsage of(Function<Builder, ObjectBuilder<DiskUsage>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code path}
	 */
	public final String path() {
		return this.path;
	}

	/**
	 * Required - API name: {@code total_bytes}
	 */
	public final long totalBytes() {
		return this.totalBytes;
	}

	/**
	 * Required - API name: {@code used_bytes}
	 */
	public final long usedBytes() {
		return this.usedBytes;
	}

	/**
	 * Required - API name: {@code free_bytes}
	 */
	public final long freeBytes() {
		return this.freeBytes;
	}

	/**
	 * Required - API name: {@code free_disk_percent}
	 */
	public final double freeDiskPercent() {
		return this.freeDiskPercent;
	}

	/**
	 * Required - API name: {@code used_disk_percent}
	 */
	public final double usedDiskPercent() {
		return this.usedDiskPercent;
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

		generator.writeKey("path");
		generator.write(this.path);

		generator.writeKey("total_bytes");
		generator.write(this.totalBytes);

		generator.writeKey("used_bytes");
		generator.write(this.usedBytes);

		generator.writeKey("free_bytes");
		generator.write(this.freeBytes);

		generator.writeKey("free_disk_percent");
		generator.write(this.freeDiskPercent);

		generator.writeKey("used_disk_percent");
		generator.write(this.usedDiskPercent);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DiskUsage}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DiskUsage> {
		private String path;

		private Long totalBytes;

		private Long usedBytes;

		private Long freeBytes;

		private Double freeDiskPercent;

		private Double usedDiskPercent;

		/**
		 * Required - API name: {@code path}
		 */
		public final Builder path(String value) {
			this.path = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_bytes}
		 */
		public final Builder totalBytes(long value) {
			this.totalBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code used_bytes}
		 */
		public final Builder usedBytes(long value) {
			this.usedBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code free_bytes}
		 */
		public final Builder freeBytes(long value) {
			this.freeBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code free_disk_percent}
		 */
		public final Builder freeDiskPercent(double value) {
			this.freeDiskPercent = value;
			return this;
		}

		/**
		 * Required - API name: {@code used_disk_percent}
		 */
		public final Builder usedDiskPercent(double value) {
			this.usedDiskPercent = value;
			return this;
		}

		/**
		 * Builds a {@link DiskUsage}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DiskUsage build() {
			_checkSingleUse();

			return new DiskUsage(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link DiskUsage}
	 */
	public static final JsonpDeserializer<DiskUsage> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			DiskUsage::setupDiskUsageDeserializer);

	protected static void setupDiskUsageDeserializer(ObjectDeserializer<DiskUsage.Builder> op) {

		op.add(Builder::path, JsonpDeserializer.stringDeserializer(), "path");
		op.add(Builder::totalBytes, JsonpDeserializer.longDeserializer(), "total_bytes");
		op.add(Builder::usedBytes, JsonpDeserializer.longDeserializer(), "used_bytes");
		op.add(Builder::freeBytes, JsonpDeserializer.longDeserializer(), "free_bytes");
		op.add(Builder::freeDiskPercent, JsonpDeserializer.doubleDeserializer(), "free_disk_percent");
		op.add(Builder::usedDiskPercent, JsonpDeserializer.doubleDeserializer(), "used_disk_percent");

	}

}
