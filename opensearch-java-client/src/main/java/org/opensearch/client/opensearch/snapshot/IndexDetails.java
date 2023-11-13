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
import javax.annotation.Nullable;

// typedef: snapshot._types.IndexDetails

@JsonpDeserializable
public class IndexDetails implements JsonpSerializable {
	private final int shardCount;

	@Nullable
	private final String size;

	private final long sizeInBytes;

	private final long maxSegmentsPerShard;

	// ---------------------------------------------------------------------------------------------

	private IndexDetails(Builder builder) {

		this.shardCount = ApiTypeHelper.requireNonNull(builder.shardCount, this, "shardCount");
		this.size = builder.size;
		this.sizeInBytes = ApiTypeHelper.requireNonNull(builder.sizeInBytes, this, "sizeInBytes");
		this.maxSegmentsPerShard = ApiTypeHelper.requireNonNull(builder.maxSegmentsPerShard, this,
				"maxSegmentsPerShard");

	}

	public static IndexDetails of(Function<Builder, ObjectBuilder<IndexDetails>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code shard_count}
	 */
	public final int shardCount() {
		return this.shardCount;
	}

	/**
	 * API name: {@code size}
	 */
	@Nullable
	public final String size() {
		return this.size;
	}

	/**
	 * Required - API name: {@code size_in_bytes}
	 */
	public final long sizeInBytes() {
		return this.sizeInBytes;
	}

	/**
	 * Required - API name: {@code max_segments_per_shard}
	 */
	public final long maxSegmentsPerShard() {
		return this.maxSegmentsPerShard;
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

		generator.writeKey("shard_count");
		generator.write(this.shardCount);

		if (this.size != null) {
			generator.writeKey("size");
			generator.write(this.size);

		}
		generator.writeKey("size_in_bytes");
		generator.write(this.sizeInBytes);

		generator.writeKey("max_segments_per_shard");
		generator.write(this.maxSegmentsPerShard);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndexDetails}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexDetails> {
		private Integer shardCount;

		@Nullable
		private String size;

		private Long sizeInBytes;

		private Long maxSegmentsPerShard;

		/**
		 * Required - API name: {@code shard_count}
		 */
		public final Builder shardCount(int value) {
			this.shardCount = value;
			return this;
		}

		/**
		 * API name: {@code size}
		 */
		public final Builder size(@Nullable String value) {
			this.size = value;
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
		 * Required - API name: {@code max_segments_per_shard}
		 */
		public final Builder maxSegmentsPerShard(long value) {
			this.maxSegmentsPerShard = value;
			return this;
		}

		/**
		 * Builds a {@link IndexDetails}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndexDetails build() {
			_checkSingleUse();

			return new IndexDetails(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndexDetails}
	 */
	public static final JsonpDeserializer<IndexDetails> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndexDetails::setupIndexDetailsDeserializer);

	protected static void setupIndexDetailsDeserializer(ObjectDeserializer<IndexDetails.Builder> op) {

		op.add(Builder::shardCount, JsonpDeserializer.integerDeserializer(), "shard_count");
		op.add(Builder::size, JsonpDeserializer.stringDeserializer(), "size");
		op.add(Builder::sizeInBytes, JsonpDeserializer.longDeserializer(), "size_in_bytes");
		op.add(Builder::maxSegmentsPerShard, JsonpDeserializer.longDeserializer(), "max_segments_per_shard");

	}

}
