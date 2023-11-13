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

package org.opensearch.client.opensearch.shutdown.get_node;

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

// typedef: shutdown.get_node.NodeShutdownStatus

/**
 *
 */
@JsonpDeserializable
public class NodeShutdownStatus implements JsonpSerializable {
	private final String nodeId;

	private final ShutdownType type;

	private final String reason;

	private final String shutdownStartedmillis;

	private final ShutdownStatus status;

	private final ShardMigrationStatus shardMigration;

	private final PersistentTaskStatus persistentTasks;

	private final PluginsStatus plugins;

	// ---------------------------------------------------------------------------------------------

	private NodeShutdownStatus(Builder builder) {

		this.nodeId = ApiTypeHelper.requireNonNull(builder.nodeId, this, "nodeId");
		this.type = ApiTypeHelper.requireNonNull(builder.type, this, "type");
		this.reason = ApiTypeHelper.requireNonNull(builder.reason, this, "reason");
		this.shutdownStartedmillis = ApiTypeHelper.requireNonNull(builder.shutdownStartedmillis, this,
				"shutdownStartedmillis");
		this.status = ApiTypeHelper.requireNonNull(builder.status, this, "status");
		this.shardMigration = ApiTypeHelper.requireNonNull(builder.shardMigration, this, "shardMigration");
		this.persistentTasks = ApiTypeHelper.requireNonNull(builder.persistentTasks, this, "persistentTasks");
		this.plugins = ApiTypeHelper.requireNonNull(builder.plugins, this, "plugins");

	}

	public static NodeShutdownStatus of(Function<Builder, ObjectBuilder<NodeShutdownStatus>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code node_id}
	 */
	public final String nodeId() {
		return this.nodeId;
	}

	/**
	 * Required - API name: {@code type}
	 */
	public final ShutdownType type() {
		return this.type;
	}

	/**
	 * Required - API name: {@code reason}
	 */
	public final String reason() {
		return this.reason;
	}

	/**
	 * Required - API name: {@code shutdown_startedmillis}
	 */
	public final String shutdownStartedmillis() {
		return this.shutdownStartedmillis;
	}

	/**
	 * Required - API name: {@code status}
	 */
	public final ShutdownStatus status() {
		return this.status;
	}

	/**
	 * Required - API name: {@code shard_migration}
	 */
	public final ShardMigrationStatus shardMigration() {
		return this.shardMigration;
	}

	/**
	 * Required - API name: {@code persistent_tasks}
	 */
	public final PersistentTaskStatus persistentTasks() {
		return this.persistentTasks;
	}

	/**
	 * Required - API name: {@code plugins}
	 */
	public final PluginsStatus plugins() {
		return this.plugins;
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

		generator.writeKey("node_id");
		generator.write(this.nodeId);

		generator.writeKey("type");
		this.type.serialize(generator, mapper);
		generator.writeKey("reason");
		generator.write(this.reason);

		generator.writeKey("shutdown_startedmillis");
		generator.write(this.shutdownStartedmillis);

		generator.writeKey("status");
		this.status.serialize(generator, mapper);
		generator.writeKey("shard_migration");
		this.shardMigration.serialize(generator, mapper);

		generator.writeKey("persistent_tasks");
		this.persistentTasks.serialize(generator, mapper);

		generator.writeKey("plugins");
		this.plugins.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeShutdownStatus}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeShutdownStatus> {
		private String nodeId;

		private ShutdownType type;

		private String reason;

		private String shutdownStartedmillis;

		private ShutdownStatus status;

		private ShardMigrationStatus shardMigration;

		private PersistentTaskStatus persistentTasks;

		private PluginsStatus plugins;

		/**
		 * Required - API name: {@code node_id}
		 */
		public final Builder nodeId(String value) {
			this.nodeId = value;
			return this;
		}

		/**
		 * Required - API name: {@code type}
		 */
		public final Builder type(ShutdownType value) {
			this.type = value;
			return this;
		}

		/**
		 * Required - API name: {@code reason}
		 */
		public final Builder reason(String value) {
			this.reason = value;
			return this;
		}

		/**
		 * Required - API name: {@code shutdown_startedmillis}
		 */
		public final Builder shutdownStartedmillis(String value) {
			this.shutdownStartedmillis = value;
			return this;
		}

		/**
		 * Required - API name: {@code status}
		 */
		public final Builder status(ShutdownStatus value) {
			this.status = value;
			return this;
		}

		/**
		 * Required - API name: {@code shard_migration}
		 */
		public final Builder shardMigration(ShardMigrationStatus value) {
			this.shardMigration = value;
			return this;
		}

		/**
		 * Required - API name: {@code shard_migration}
		 */
		public final Builder shardMigration(
				Function<ShardMigrationStatus.Builder, ObjectBuilder<ShardMigrationStatus>> fn) {
			return this.shardMigration(fn.apply(new ShardMigrationStatus.Builder()).build());
		}

		/**
		 * Required - API name: {@code persistent_tasks}
		 */
		public final Builder persistentTasks(PersistentTaskStatus value) {
			this.persistentTasks = value;
			return this;
		}

		/**
		 * Required - API name: {@code persistent_tasks}
		 */
		public final Builder persistentTasks(
				Function<PersistentTaskStatus.Builder, ObjectBuilder<PersistentTaskStatus>> fn) {
			return this.persistentTasks(fn.apply(new PersistentTaskStatus.Builder()).build());
		}

		/**
		 * Required - API name: {@code plugins}
		 */
		public final Builder plugins(PluginsStatus value) {
			this.plugins = value;
			return this;
		}

		/**
		 * Required - API name: {@code plugins}
		 */
		public final Builder plugins(Function<PluginsStatus.Builder, ObjectBuilder<PluginsStatus>> fn) {
			return this.plugins(fn.apply(new PluginsStatus.Builder()).build());
		}

		/**
		 * Builds a {@link NodeShutdownStatus}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeShutdownStatus build() {
			_checkSingleUse();

			return new NodeShutdownStatus(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeShutdownStatus}
	 */
	public static final JsonpDeserializer<NodeShutdownStatus> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, NodeShutdownStatus::setupNodeShutdownStatusDeserializer);

	protected static void setupNodeShutdownStatusDeserializer(ObjectDeserializer<NodeShutdownStatus.Builder> op) {

		op.add(Builder::nodeId, JsonpDeserializer.stringDeserializer(), "node_id");
		op.add(Builder::type, ShutdownType._DESERIALIZER, "type");
		op.add(Builder::reason, JsonpDeserializer.stringDeserializer(), "reason");
		op.add(Builder::shutdownStartedmillis, JsonpDeserializer.stringDeserializer(), "shutdown_startedmillis");
		op.add(Builder::status, ShutdownStatus._DESERIALIZER, "status");
		op.add(Builder::shardMigration, ShardMigrationStatus._DESERIALIZER, "shard_migration");
		op.add(Builder::persistentTasks, PersistentTaskStatus._DESERIALIZER, "persistent_tasks");
		op.add(Builder::plugins, PluginsStatus._DESERIALIZER, "plugins");

	}

}
