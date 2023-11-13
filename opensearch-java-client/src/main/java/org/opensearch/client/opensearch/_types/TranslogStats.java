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

// typedef: _types.TranslogStats

@JsonpDeserializable
public class TranslogStats implements JsonpSerializable {
	private final long earliestLastModifiedAge;

	private final long operations;

	@Nullable
	private final String size;

	private final long sizeInBytes;

	private final int uncommittedOperations;

	@Nullable
	private final String uncommittedSize;

	private final long uncommittedSizeInBytes;

	// ---------------------------------------------------------------------------------------------

	private TranslogStats(Builder builder) {

		this.earliestLastModifiedAge = ApiTypeHelper.requireNonNull(builder.earliestLastModifiedAge, this,
				"earliestLastModifiedAge");
		this.operations = ApiTypeHelper.requireNonNull(builder.operations, this, "operations");
		this.size = builder.size;
		this.sizeInBytes = ApiTypeHelper.requireNonNull(builder.sizeInBytes, this, "sizeInBytes");
		this.uncommittedOperations = ApiTypeHelper.requireNonNull(builder.uncommittedOperations, this,
				"uncommittedOperations");
		this.uncommittedSize = builder.uncommittedSize;
		this.uncommittedSizeInBytes = ApiTypeHelper.requireNonNull(builder.uncommittedSizeInBytes, this,
				"uncommittedSizeInBytes");

	}

	public static TranslogStats of(Function<Builder, ObjectBuilder<TranslogStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code earliest_last_modified_age}
	 */
	public final long earliestLastModifiedAge() {
		return this.earliestLastModifiedAge;
	}

	/**
	 * Required - API name: {@code operations}
	 */
	public final long operations() {
		return this.operations;
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
	 * Required - API name: {@code uncommitted_operations}
	 */
	public final int uncommittedOperations() {
		return this.uncommittedOperations;
	}

	/**
	 * API name: {@code uncommitted_size}
	 */
	@Nullable
	public final String uncommittedSize() {
		return this.uncommittedSize;
	}

	/**
	 * Required - API name: {@code uncommitted_size_in_bytes}
	 */
	public final long uncommittedSizeInBytes() {
		return this.uncommittedSizeInBytes;
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

		generator.writeKey("earliest_last_modified_age");
		generator.write(this.earliestLastModifiedAge);

		generator.writeKey("operations");
		generator.write(this.operations);

		if (this.size != null) {
			generator.writeKey("size");
			generator.write(this.size);

		}
		generator.writeKey("size_in_bytes");
		generator.write(this.sizeInBytes);

		generator.writeKey("uncommitted_operations");
		generator.write(this.uncommittedOperations);

		if (this.uncommittedSize != null) {
			generator.writeKey("uncommitted_size");
			generator.write(this.uncommittedSize);

		}
		generator.writeKey("uncommitted_size_in_bytes");
		generator.write(this.uncommittedSizeInBytes);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link TranslogStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<TranslogStats> {
		private Long earliestLastModifiedAge;

		private Long operations;

		@Nullable
		private String size;

		private Long sizeInBytes;

		private Integer uncommittedOperations;

		@Nullable
		private String uncommittedSize;

		private Long uncommittedSizeInBytes;

		/**
		 * Required - API name: {@code earliest_last_modified_age}
		 */
		public final Builder earliestLastModifiedAge(long value) {
			this.earliestLastModifiedAge = value;
			return this;
		}

		/**
		 * Required - API name: {@code operations}
		 */
		public final Builder operations(long value) {
			this.operations = value;
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
		 * Required - API name: {@code uncommitted_operations}
		 */
		public final Builder uncommittedOperations(int value) {
			this.uncommittedOperations = value;
			return this;
		}

		/**
		 * API name: {@code uncommitted_size}
		 */
		public final Builder uncommittedSize(@Nullable String value) {
			this.uncommittedSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code uncommitted_size_in_bytes}
		 */
		public final Builder uncommittedSizeInBytes(long value) {
			this.uncommittedSizeInBytes = value;
			return this;
		}

		/**
		 * Builds a {@link TranslogStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public TranslogStats build() {
			_checkSingleUse();

			return new TranslogStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link TranslogStats}
	 */
	public static final JsonpDeserializer<TranslogStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			TranslogStats::setupTranslogStatsDeserializer);

	protected static void setupTranslogStatsDeserializer(ObjectDeserializer<TranslogStats.Builder> op) {

		op.add(Builder::earliestLastModifiedAge, JsonpDeserializer.longDeserializer(), "earliest_last_modified_age");
		op.add(Builder::operations, JsonpDeserializer.longDeserializer(), "operations");
		op.add(Builder::size, JsonpDeserializer.stringDeserializer(), "size");
		op.add(Builder::sizeInBytes, JsonpDeserializer.longDeserializer(), "size_in_bytes");
		op.add(Builder::uncommittedOperations, JsonpDeserializer.integerDeserializer(), "uncommitted_operations");
		op.add(Builder::uncommittedSize, JsonpDeserializer.stringDeserializer(), "uncommitted_size");
		op.add(Builder::uncommittedSizeInBytes, JsonpDeserializer.longDeserializer(), "uncommitted_size_in_bytes");

	}

}
