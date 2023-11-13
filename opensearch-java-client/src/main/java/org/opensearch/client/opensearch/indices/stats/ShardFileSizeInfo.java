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

package org.opensearch.client.opensearch.indices.stats;

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

// typedef: indices.stats.ShardFileSizeInfo


@JsonpDeserializable
public class ShardFileSizeInfo implements JsonpSerializable {
	private final String description;

	private final long sizeInBytes;

	@Nullable
	private final Long minSizeInBytes;

	@Nullable
	private final Long maxSizeInBytes;

	@Nullable
	private final Long averageSizeInBytes;

	@Nullable
	private final Long count;

	// ---------------------------------------------------------------------------------------------

	private ShardFileSizeInfo(Builder builder) {

		this.description = ApiTypeHelper.requireNonNull(builder.description, this, "description");
		this.sizeInBytes = ApiTypeHelper.requireNonNull(builder.sizeInBytes, this, "sizeInBytes");
		this.minSizeInBytes = builder.minSizeInBytes;
		this.maxSizeInBytes = builder.maxSizeInBytes;
		this.averageSizeInBytes = builder.averageSizeInBytes;
		this.count = builder.count;

	}

	public static ShardFileSizeInfo of(Function<Builder, ObjectBuilder<ShardFileSizeInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code description}
	 */
	public final String description() {
		return this.description;
	}

	/**
	 * Required - API name: {@code size_in_bytes}
	 */
	public final long sizeInBytes() {
		return this.sizeInBytes;
	}

	/**
	 * API name: {@code min_size_in_bytes}
	 */
	@Nullable
	public final Long minSizeInBytes() {
		return this.minSizeInBytes;
	}

	/**
	 * API name: {@code max_size_in_bytes}
	 */
	@Nullable
	public final Long maxSizeInBytes() {
		return this.maxSizeInBytes;
	}

	/**
	 * API name: {@code average_size_in_bytes}
	 */
	@Nullable
	public final Long averageSizeInBytes() {
		return this.averageSizeInBytes;
	}

	/**
	 * API name: {@code count}
	 */
	@Nullable
	public final Long count() {
		return this.count;
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

		generator.writeKey("description");
		generator.write(this.description);

		generator.writeKey("size_in_bytes");
		generator.write(this.sizeInBytes);

		if (this.minSizeInBytes != null) {
			generator.writeKey("min_size_in_bytes");
			generator.write(this.minSizeInBytes);

		}
		if (this.maxSizeInBytes != null) {
			generator.writeKey("max_size_in_bytes");
			generator.write(this.maxSizeInBytes);

		}
		if (this.averageSizeInBytes != null) {
			generator.writeKey("average_size_in_bytes");
			generator.write(this.averageSizeInBytes);

		}
		if (this.count != null) {
			generator.writeKey("count");
			generator.write(this.count);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ShardFileSizeInfo}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ShardFileSizeInfo> {
		private String description;

		private Long sizeInBytes;

		@Nullable
		private Long minSizeInBytes;

		@Nullable
		private Long maxSizeInBytes;

		@Nullable
		private Long averageSizeInBytes;

		@Nullable
		private Long count;

		/**
		 * Required - API name: {@code description}
		 */
		public final Builder description(String value) {
			this.description = value;
			return this;
		}

		/**
		 * Required - API name: {@code size_in_bytes}
		 */
		public final Builder sizeInBytes(long value) {
			this.sizeInBytes = value;
			return this;
		}

		/**
		 * API name: {@code min_size_in_bytes}
		 */
		public final Builder minSizeInBytes(@Nullable Long value) {
			this.minSizeInBytes = value;
			return this;
		}

		/**
		 * API name: {@code max_size_in_bytes}
		 */
		public final Builder maxSizeInBytes(@Nullable Long value) {
			this.maxSizeInBytes = value;
			return this;
		}

		/**
		 * API name: {@code average_size_in_bytes}
		 */
		public final Builder averageSizeInBytes(@Nullable Long value) {
			this.averageSizeInBytes = value;
			return this;
		}

		/**
		 * API name: {@code count}
		 */
		public final Builder count(@Nullable Long value) {
			this.count = value;
			return this;
		}

		/**
		 * Builds a {@link ShardFileSizeInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ShardFileSizeInfo build() {
			_checkSingleUse();

			return new ShardFileSizeInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ShardFileSizeInfo}
	 */
	public static final JsonpDeserializer<ShardFileSizeInfo> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ShardFileSizeInfo::setupShardFileSizeInfoDeserializer);

	protected static void setupShardFileSizeInfoDeserializer(ObjectDeserializer<ShardFileSizeInfo.Builder> op) {

		op.add(Builder::description, JsonpDeserializer.stringDeserializer(), "description");
		op.add(Builder::sizeInBytes, JsonpDeserializer.longDeserializer(), "size_in_bytes");
		op.add(Builder::minSizeInBytes, JsonpDeserializer.longDeserializer(), "min_size_in_bytes");
		op.add(Builder::maxSizeInBytes, JsonpDeserializer.longDeserializer(), "max_size_in_bytes");
		op.add(Builder::averageSizeInBytes, JsonpDeserializer.longDeserializer(), "average_size_in_bytes");
		op.add(Builder::count, JsonpDeserializer.longDeserializer(), "count");

	}

}
