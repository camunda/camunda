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

// typedef: cluster.stats.IndicesVersions


@JsonpDeserializable
public class IndicesVersions implements JsonpSerializable {
	private final int indexCount;

	private final int primaryShardCount;

	private final long totalPrimaryBytes;

	private final String version;

	// ---------------------------------------------------------------------------------------------

	private IndicesVersions(Builder builder) {

		this.indexCount = ApiTypeHelper.requireNonNull(builder.indexCount, this, "indexCount");
		this.primaryShardCount = ApiTypeHelper.requireNonNull(builder.primaryShardCount, this, "primaryShardCount");
		this.totalPrimaryBytes = ApiTypeHelper.requireNonNull(builder.totalPrimaryBytes, this, "totalPrimaryBytes");
		this.version = ApiTypeHelper.requireNonNull(builder.version, this, "version");

	}

	public static IndicesVersions of(Function<Builder, ObjectBuilder<IndicesVersions>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code index_count}
	 */
	public final int indexCount() {
		return this.indexCount;
	}

	/**
	 * Required - API name: {@code primary_shard_count}
	 */
	public final int primaryShardCount() {
		return this.primaryShardCount;
	}

	/**
	 * Required - API name: {@code total_primary_bytes}
	 */
	public final long totalPrimaryBytes() {
		return this.totalPrimaryBytes;
	}

	/**
	 * Required - API name: {@code version}
	 */
	public final String version() {
		return this.version;
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

		generator.writeKey("index_count");
		generator.write(this.indexCount);

		generator.writeKey("primary_shard_count");
		generator.write(this.primaryShardCount);

		generator.writeKey("total_primary_bytes");
		generator.write(this.totalPrimaryBytes);

		generator.writeKey("version");
		generator.write(this.version);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndicesVersions}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndicesVersions> {
		private Integer indexCount;

		private Integer primaryShardCount;

		private Long totalPrimaryBytes;

		private String version;

		/**
		 * Required - API name: {@code index_count}
		 */
		public final Builder indexCount(int value) {
			this.indexCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code primary_shard_count}
		 */
		public final Builder primaryShardCount(int value) {
			this.primaryShardCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_primary_bytes}
		 */
		public final Builder totalPrimaryBytes(long value) {
			this.totalPrimaryBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code version}
		 */
		public final Builder version(String value) {
			this.version = value;
			return this;
		}

		/**
		 * Builds a {@link IndicesVersions}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndicesVersions build() {
			_checkSingleUse();

			return new IndicesVersions(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndicesVersions}
	 */
	public static final JsonpDeserializer<IndicesVersions> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndicesVersions::setupIndicesVersionsDeserializer);

	protected static void setupIndicesVersionsDeserializer(ObjectDeserializer<IndicesVersions.Builder> op) {

		op.add(Builder::indexCount, JsonpDeserializer.integerDeserializer(), "index_count");
		op.add(Builder::primaryShardCount, JsonpDeserializer.integerDeserializer(), "primary_shard_count");
		op.add(Builder::totalPrimaryBytes, JsonpDeserializer.longDeserializer(), "total_primary_bytes");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version");

	}

}
