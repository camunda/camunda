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

package org.opensearch.client.opensearch.cluster.health;

import org.opensearch.client.opensearch._types.HealthStatus;
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

// typedef: cluster.health.ShardHealthStats


@JsonpDeserializable
public class ShardHealthStats implements JsonpSerializable {
	private final int activeShards;

	private final int initializingShards;

	private final boolean primaryActive;

	private final int relocatingShards;

	private final HealthStatus status;

	private final int unassignedShards;

	// ---------------------------------------------------------------------------------------------

	private ShardHealthStats(Builder builder) {

		this.activeShards = ApiTypeHelper.requireNonNull(builder.activeShards, this, "activeShards");
		this.initializingShards = ApiTypeHelper.requireNonNull(builder.initializingShards, this, "initializingShards");
		this.primaryActive = ApiTypeHelper.requireNonNull(builder.primaryActive, this, "primaryActive");
		this.relocatingShards = ApiTypeHelper.requireNonNull(builder.relocatingShards, this, "relocatingShards");
		this.status = ApiTypeHelper.requireNonNull(builder.status, this, "status");
		this.unassignedShards = ApiTypeHelper.requireNonNull(builder.unassignedShards, this, "unassignedShards");

	}

	public static ShardHealthStats of(Function<Builder, ObjectBuilder<ShardHealthStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code active_shards}
	 */
	public final int activeShards() {
		return this.activeShards;
	}

	/**
	 * Required - API name: {@code initializing_shards}
	 */
	public final int initializingShards() {
		return this.initializingShards;
	}

	/**
	 * Required - API name: {@code primary_active}
	 */
	public final boolean primaryActive() {
		return this.primaryActive;
	}

	/**
	 * Required - API name: {@code relocating_shards}
	 */
	public final int relocatingShards() {
		return this.relocatingShards;
	}

	/**
	 * Required - API name: {@code status}
	 */
	public final HealthStatus status() {
		return this.status;
	}

	/**
	 * Required - API name: {@code unassigned_shards}
	 */
	public final int unassignedShards() {
		return this.unassignedShards;
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

		generator.writeKey("active_shards");
		generator.write(this.activeShards);

		generator.writeKey("initializing_shards");
		generator.write(this.initializingShards);

		generator.writeKey("primary_active");
		generator.write(this.primaryActive);

		generator.writeKey("relocating_shards");
		generator.write(this.relocatingShards);

		generator.writeKey("status");
		this.status.serialize(generator, mapper);
		generator.writeKey("unassigned_shards");
		generator.write(this.unassignedShards);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ShardHealthStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ShardHealthStats> {
		private Integer activeShards;

		private Integer initializingShards;

		private Boolean primaryActive;

		private Integer relocatingShards;

		private HealthStatus status;

		private Integer unassignedShards;

		/**
		 * Required - API name: {@code active_shards}
		 */
		public final Builder activeShards(int value) {
			this.activeShards = value;
			return this;
		}

		/**
		 * Required - API name: {@code initializing_shards}
		 */
		public final Builder initializingShards(int value) {
			this.initializingShards = value;
			return this;
		}

		/**
		 * Required - API name: {@code primary_active}
		 */
		public final Builder primaryActive(boolean value) {
			this.primaryActive = value;
			return this;
		}

		/**
		 * Required - API name: {@code relocating_shards}
		 */
		public final Builder relocatingShards(int value) {
			this.relocatingShards = value;
			return this;
		}

		/**
		 * Required - API name: {@code status}
		 */
		public final Builder status(HealthStatus value) {
			this.status = value;
			return this;
		}

		/**
		 * Required - API name: {@code unassigned_shards}
		 */
		public final Builder unassignedShards(int value) {
			this.unassignedShards = value;
			return this;
		}

		/**
		 * Builds a {@link ShardHealthStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ShardHealthStats build() {
			_checkSingleUse();

			return new ShardHealthStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ShardHealthStats}
	 */
	public static final JsonpDeserializer<ShardHealthStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ShardHealthStats::setupShardHealthStatsDeserializer);

	protected static void setupShardHealthStatsDeserializer(ObjectDeserializer<ShardHealthStats.Builder> op) {

		op.add(Builder::activeShards, JsonpDeserializer.integerDeserializer(), "active_shards");
		op.add(Builder::initializingShards, JsonpDeserializer.integerDeserializer(), "initializing_shards");
		op.add(Builder::primaryActive, JsonpDeserializer.booleanDeserializer(), "primary_active");
		op.add(Builder::relocatingShards, JsonpDeserializer.integerDeserializer(), "relocating_shards");
		op.add(Builder::status, HealthStatus._DESERIALIZER, "status");
		op.add(Builder::unassignedShards, JsonpDeserializer.integerDeserializer(), "unassigned_shards");

	}

}
