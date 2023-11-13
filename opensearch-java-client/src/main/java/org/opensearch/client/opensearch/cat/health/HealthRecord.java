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

package org.opensearch.client.opensearch.cat.health;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: cat.health.HealthRecord


@JsonpDeserializable
public class HealthRecord implements JsonpSerializable {
	@Nullable
	private final String epoch;

	@Nullable
	private final String timestamp;

	@Nullable
	private final String cluster;

	@Nullable
	private final String status;

	@Nullable
	private final String nodeTotal;

	@Nullable
	private final String nodeData;

	@Nullable
	private final String shards;

	@Nullable
	private final String pri;

	@Nullable
	private final String relo;

	@Nullable
	private final String init;

	@Nullable
	private final String unassign;

	@Nullable
	private final String pendingTasks;

	@Nullable
	private final String maxTaskWaitTime;

	@Nullable
	private final String activeShardsPercent;

	// ---------------------------------------------------------------------------------------------

	private HealthRecord(Builder builder) {

		this.epoch = builder.epoch;
		this.timestamp = builder.timestamp;
		this.cluster = builder.cluster;
		this.status = builder.status;
		this.nodeTotal = builder.nodeTotal;
		this.nodeData = builder.nodeData;
		this.shards = builder.shards;
		this.pri = builder.pri;
		this.relo = builder.relo;
		this.init = builder.init;
		this.unassign = builder.unassign;
		this.pendingTasks = builder.pendingTasks;
		this.maxTaskWaitTime = builder.maxTaskWaitTime;
		this.activeShardsPercent = builder.activeShardsPercent;

	}

	public static HealthRecord of(Function<Builder, ObjectBuilder<HealthRecord>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * seconds since 1969-01-01 00:00:00
	 * <p>
	 * API name: {@code epoch}
	 */
	@Nullable
	public final String epoch() {
		return this.epoch;
	}

	/**
	 * time in HH:MM:SS
	 * <p>
	 * API name: {@code timestamp}
	 */
	@Nullable
	public final String timestamp() {
		return this.timestamp;
	}

	/**
	 * cluster name
	 * <p>
	 * API name: {@code cluster}
	 */
	@Nullable
	public final String cluster() {
		return this.cluster;
	}

	/**
	 * health status
	 * <p>
	 * API name: {@code status}
	 */
	@Nullable
	public final String status() {
		return this.status;
	}

	/**
	 * total number of nodes
	 * <p>
	 * API name: {@code node.total}
	 */
	@Nullable
	public final String nodeTotal() {
		return this.nodeTotal;
	}

	/**
	 * number of nodes that can store data
	 * <p>
	 * API name: {@code node.data}
	 */
	@Nullable
	public final String nodeData() {
		return this.nodeData;
	}

	/**
	 * total number of shards
	 * <p>
	 * API name: {@code shards}
	 */
	@Nullable
	public final String shards() {
		return this.shards;
	}

	/**
	 * number of primary shards
	 * <p>
	 * API name: {@code pri}
	 */
	@Nullable
	public final String pri() {
		return this.pri;
	}

	/**
	 * number of relocating nodes
	 * <p>
	 * API name: {@code relo}
	 */
	@Nullable
	public final String relo() {
		return this.relo;
	}

	/**
	 * number of initializing nodes
	 * <p>
	 * API name: {@code init}
	 */
	@Nullable
	public final String init() {
		return this.init;
	}

	/**
	 * number of unassigned shards
	 * <p>
	 * API name: {@code unassign}
	 */
	@Nullable
	public final String unassign() {
		return this.unassign;
	}

	/**
	 * number of pending tasks
	 * <p>
	 * API name: {@code pending_tasks}
	 */
	@Nullable
	public final String pendingTasks() {
		return this.pendingTasks;
	}

	/**
	 * wait time of longest task pending
	 * <p>
	 * API name: {@code max_task_wait_time}
	 */
	@Nullable
	public final String maxTaskWaitTime() {
		return this.maxTaskWaitTime;
	}

	/**
	 * active number of shards in percent
	 * <p>
	 * API name: {@code active_shards_percent}
	 */
	@Nullable
	public final String activeShardsPercent() {
		return this.activeShardsPercent;
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

		if (this.epoch != null) {
			generator.writeKey("epoch");
			generator.write(this.epoch);

		}
		if (this.timestamp != null) {
			generator.writeKey("timestamp");
			generator.write(this.timestamp);

		}
		if (this.cluster != null) {
			generator.writeKey("cluster");
			generator.write(this.cluster);

		}
		if (this.status != null) {
			generator.writeKey("status");
			generator.write(this.status);

		}
		if (this.nodeTotal != null) {
			generator.writeKey("node.total");
			generator.write(this.nodeTotal);

		}
		if (this.nodeData != null) {
			generator.writeKey("node.data");
			generator.write(this.nodeData);

		}
		if (this.shards != null) {
			generator.writeKey("shards");
			generator.write(this.shards);

		}
		if (this.pri != null) {
			generator.writeKey("pri");
			generator.write(this.pri);

		}
		if (this.relo != null) {
			generator.writeKey("relo");
			generator.write(this.relo);

		}
		if (this.init != null) {
			generator.writeKey("init");
			generator.write(this.init);

		}
		if (this.unassign != null) {
			generator.writeKey("unassign");
			generator.write(this.unassign);

		}
		if (this.pendingTasks != null) {
			generator.writeKey("pending_tasks");
			generator.write(this.pendingTasks);

		}
		if (this.maxTaskWaitTime != null) {
			generator.writeKey("max_task_wait_time");
			generator.write(this.maxTaskWaitTime);

		}
		if (this.activeShardsPercent != null) {
			generator.writeKey("active_shards_percent");
			generator.write(this.activeShardsPercent);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HealthRecord}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<HealthRecord> {
		@Nullable
		private String epoch;

		@Nullable
		private String timestamp;

		@Nullable
		private String cluster;

		@Nullable
		private String status;

		@Nullable
		private String nodeTotal;

		@Nullable
		private String nodeData;

		@Nullable
		private String shards;

		@Nullable
		private String pri;

		@Nullable
		private String relo;

		@Nullable
		private String init;

		@Nullable
		private String unassign;

		@Nullable
		private String pendingTasks;

		@Nullable
		private String maxTaskWaitTime;

		@Nullable
		private String activeShardsPercent;

		/**
		 * seconds since 1969-01-01 00:00:00
		 * <p>
		 * API name: {@code epoch}
		 */
		public final Builder epoch(@Nullable String value) {
			this.epoch = value;
			return this;
		}

		/**
		 * time in HH:MM:SS
		 * <p>
		 * API name: {@code timestamp}
		 */
		public final Builder timestamp(@Nullable String value) {
			this.timestamp = value;
			return this;
		}

		/**
		 * cluster name
		 * <p>
		 * API name: {@code cluster}
		 */
		public final Builder cluster(@Nullable String value) {
			this.cluster = value;
			return this;
		}

		/**
		 * health status
		 * <p>
		 * API name: {@code status}
		 */
		public final Builder status(@Nullable String value) {
			this.status = value;
			return this;
		}

		/**
		 * total number of nodes
		 * <p>
		 * API name: {@code node.total}
		 */
		public final Builder nodeTotal(@Nullable String value) {
			this.nodeTotal = value;
			return this;
		}

		/**
		 * number of nodes that can store data
		 * <p>
		 * API name: {@code node.data}
		 */
		public final Builder nodeData(@Nullable String value) {
			this.nodeData = value;
			return this;
		}

		/**
		 * total number of shards
		 * <p>
		 * API name: {@code shards}
		 */
		public final Builder shards(@Nullable String value) {
			this.shards = value;
			return this;
		}

		/**
		 * number of primary shards
		 * <p>
		 * API name: {@code pri}
		 */
		public final Builder pri(@Nullable String value) {
			this.pri = value;
			return this;
		}

		/**
		 * number of relocating nodes
		 * <p>
		 * API name: {@code relo}
		 */
		public final Builder relo(@Nullable String value) {
			this.relo = value;
			return this;
		}

		/**
		 * number of initializing nodes
		 * <p>
		 * API name: {@code init}
		 */
		public final Builder init(@Nullable String value) {
			this.init = value;
			return this;
		}

		/**
		 * number of unassigned shards
		 * <p>
		 * API name: {@code unassign}
		 */
		public final Builder unassign(@Nullable String value) {
			this.unassign = value;
			return this;
		}

		/**
		 * number of pending tasks
		 * <p>
		 * API name: {@code pending_tasks}
		 */
		public final Builder pendingTasks(@Nullable String value) {
			this.pendingTasks = value;
			return this;
		}

		/**
		 * wait time of longest task pending
		 * <p>
		 * API name: {@code max_task_wait_time}
		 */
		public final Builder maxTaskWaitTime(@Nullable String value) {
			this.maxTaskWaitTime = value;
			return this;
		}

		/**
		 * active number of shards in percent
		 * <p>
		 * API name: {@code active_shards_percent}
		 */
		public final Builder activeShardsPercent(@Nullable String value) {
			this.activeShardsPercent = value;
			return this;
		}

		/**
		 * Builds a {@link HealthRecord}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HealthRecord build() {
			_checkSingleUse();

			return new HealthRecord(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HealthRecord}
	 */
	public static final JsonpDeserializer<HealthRecord> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			HealthRecord::setupHealthRecordDeserializer);

	protected static void setupHealthRecordDeserializer(ObjectDeserializer<HealthRecord.Builder> op) {

		op.add(Builder::epoch, JsonpDeserializer.stringDeserializer(), "epoch", "time");
		op.add(Builder::timestamp, JsonpDeserializer.stringDeserializer(), "timestamp", "ts", "hms", "hhmmss");
		op.add(Builder::cluster, JsonpDeserializer.stringDeserializer(), "cluster", "cl");
		op.add(Builder::status, JsonpDeserializer.stringDeserializer(), "status", "st");
		op.add(Builder::nodeTotal, JsonpDeserializer.stringDeserializer(), "node.total", "nt", "nodeTotal");
		op.add(Builder::nodeData, JsonpDeserializer.stringDeserializer(), "node.data", "nd", "nodeData");
		op.add(Builder::shards, JsonpDeserializer.stringDeserializer(), "shards", "t", "sh", "shards.total",
				"shardsTotal");
		op.add(Builder::pri, JsonpDeserializer.stringDeserializer(), "pri", "p", "shards.primary", "shardsPrimary");
		op.add(Builder::relo, JsonpDeserializer.stringDeserializer(), "relo", "r", "shards.relocating",
				"shardsRelocating");
		op.add(Builder::init, JsonpDeserializer.stringDeserializer(), "init", "i", "shards.initializing",
				"shardsInitializing");
		op.add(Builder::unassign, JsonpDeserializer.stringDeserializer(), "unassign", "u", "shards.unassigned",
				"shardsUnassigned");
		op.add(Builder::pendingTasks, JsonpDeserializer.stringDeserializer(), "pending_tasks", "pt", "pendingTasks");
		op.add(Builder::maxTaskWaitTime, JsonpDeserializer.stringDeserializer(), "max_task_wait_time", "mtwt",
				"maxTaskWaitTime");
		op.add(Builder::activeShardsPercent, JsonpDeserializer.stringDeserializer(), "active_shards_percent", "asp",
				"activeShardsPercent");

	}

}
