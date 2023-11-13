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

package org.opensearch.client.opensearch.cat.thread_pool;

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

// typedef: cat.thread_pool.ThreadPoolRecord


@JsonpDeserializable
public class ThreadPoolRecord implements JsonpSerializable {
	@Nullable
	private final String nodeName;

	@Nullable
	private final String nodeId;

	@Nullable
	private final String ephemeralNodeId;

	@Nullable
	private final String pid;

	@Nullable
	private final String host;

	@Nullable
	private final String ip;

	@Nullable
	private final String port;

	@Nullable
	private final String name;

	@Nullable
	private final String type;

	@Nullable
	private final String active;

	@Nullable
	private final String poolSize;

	@Nullable
	private final String queue;

	@Nullable
	private final String queueSize;

	@Nullable
	private final String rejected;

	@Nullable
	private final String largest;

	@Nullable
	private final String completed;

	@Nullable
	private final String core;

	@Nullable
	private final String max;

	@Nullable
	private final String size;

	@Nullable
	private final String keepAlive;

	// ---------------------------------------------------------------------------------------------

	private ThreadPoolRecord(Builder builder) {

		this.nodeName = builder.nodeName;
		this.nodeId = builder.nodeId;
		this.ephemeralNodeId = builder.ephemeralNodeId;
		this.pid = builder.pid;
		this.host = builder.host;
		this.ip = builder.ip;
		this.port = builder.port;
		this.name = builder.name;
		this.type = builder.type;
		this.active = builder.active;
		this.poolSize = builder.poolSize;
		this.queue = builder.queue;
		this.queueSize = builder.queueSize;
		this.rejected = builder.rejected;
		this.largest = builder.largest;
		this.completed = builder.completed;
		this.core = builder.core;
		this.max = builder.max;
		this.size = builder.size;
		this.keepAlive = builder.keepAlive;

	}

	public static ThreadPoolRecord of(Function<Builder, ObjectBuilder<ThreadPoolRecord>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * node name
	 * <p>
	 * API name: {@code node_name}
	 */
	@Nullable
	public final String nodeName() {
		return this.nodeName;
	}

	/**
	 * persistent node id
	 * <p>
	 * API name: {@code node_id}
	 */
	@Nullable
	public final String nodeId() {
		return this.nodeId;
	}

	/**
	 * ephemeral node id
	 * <p>
	 * API name: {@code ephemeral_node_id}
	 */
	@Nullable
	public final String ephemeralNodeId() {
		return this.ephemeralNodeId;
	}

	/**
	 * process id
	 * <p>
	 * API name: {@code pid}
	 */
	@Nullable
	public final String pid() {
		return this.pid;
	}

	/**
	 * host name
	 * <p>
	 * API name: {@code host}
	 */
	@Nullable
	public final String host() {
		return this.host;
	}

	/**
	 * ip address
	 * <p>
	 * API name: {@code ip}
	 */
	@Nullable
	public final String ip() {
		return this.ip;
	}

	/**
	 * bound transport port
	 * <p>
	 * API name: {@code port}
	 */
	@Nullable
	public final String port() {
		return this.port;
	}

	/**
	 * thread pool name
	 * <p>
	 * API name: {@code name}
	 */
	@Nullable
	public final String name() {
		return this.name;
	}

	/**
	 * thread pool type
	 * <p>
	 * API name: {@code type}
	 */
	@Nullable
	public final String type() {
		return this.type;
	}

	/**
	 * number of active threads
	 * <p>
	 * API name: {@code active}
	 */
	@Nullable
	public final String active() {
		return this.active;
	}

	/**
	 * number of threads
	 * <p>
	 * API name: {@code pool_size}
	 */
	@Nullable
	public final String poolSize() {
		return this.poolSize;
	}

	/**
	 * number of tasks currently in queue
	 * <p>
	 * API name: {@code queue}
	 */
	@Nullable
	public final String queue() {
		return this.queue;
	}

	/**
	 * maximum number of tasks permitted in queue
	 * <p>
	 * API name: {@code queue_size}
	 */
	@Nullable
	public final String queueSize() {
		return this.queueSize;
	}

	/**
	 * number of rejected tasks
	 * <p>
	 * API name: {@code rejected}
	 */
	@Nullable
	public final String rejected() {
		return this.rejected;
	}

	/**
	 * highest number of seen active threads
	 * <p>
	 * API name: {@code largest}
	 */
	@Nullable
	public final String largest() {
		return this.largest;
	}

	/**
	 * number of completed tasks
	 * <p>
	 * API name: {@code completed}
	 */
	@Nullable
	public final String completed() {
		return this.completed;
	}

	/**
	 * core number of threads in a scaling thread pool
	 * <p>
	 * API name: {@code core}
	 */
	@Nullable
	public final String core() {
		return this.core;
	}

	/**
	 * maximum number of threads in a scaling thread pool
	 * <p>
	 * API name: {@code max}
	 */
	@Nullable
	public final String max() {
		return this.max;
	}

	/**
	 * number of threads in a fixed thread pool
	 * <p>
	 * API name: {@code size}
	 */
	@Nullable
	public final String size() {
		return this.size;
	}

	/**
	 * thread keep alive time
	 * <p>
	 * API name: {@code keep_alive}
	 */
	@Nullable
	public final String keepAlive() {
		return this.keepAlive;
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

		if (this.nodeName != null) {
			generator.writeKey("node_name");
			generator.write(this.nodeName);

		}
		if (this.nodeId != null) {
			generator.writeKey("node_id");
			generator.write(this.nodeId);

		}
		if (this.ephemeralNodeId != null) {
			generator.writeKey("ephemeral_node_id");
			generator.write(this.ephemeralNodeId);

		}
		if (this.pid != null) {
			generator.writeKey("pid");
			generator.write(this.pid);

		}
		if (this.host != null) {
			generator.writeKey("host");
			generator.write(this.host);

		}
		if (this.ip != null) {
			generator.writeKey("ip");
			generator.write(this.ip);

		}
		if (this.port != null) {
			generator.writeKey("port");
			generator.write(this.port);

		}
		if (this.name != null) {
			generator.writeKey("name");
			generator.write(this.name);

		}
		if (this.type != null) {
			generator.writeKey("type");
			generator.write(this.type);

		}
		if (this.active != null) {
			generator.writeKey("active");
			generator.write(this.active);

		}
		if (this.poolSize != null) {
			generator.writeKey("pool_size");
			generator.write(this.poolSize);

		}
		if (this.queue != null) {
			generator.writeKey("queue");
			generator.write(this.queue);

		}
		if (this.queueSize != null) {
			generator.writeKey("queue_size");
			generator.write(this.queueSize);

		}
		if (this.rejected != null) {
			generator.writeKey("rejected");
			generator.write(this.rejected);

		}
		if (this.largest != null) {
			generator.writeKey("largest");
			generator.write(this.largest);

		}
		if (this.completed != null) {
			generator.writeKey("completed");
			generator.write(this.completed);

		}
		if (this.core != null) {
			generator.writeKey("core");
			generator.write(this.core);

		}
		if (this.max != null) {
			generator.writeKey("max");
			generator.write(this.max);

		}
		if (this.size != null) {
			generator.writeKey("size");
			generator.write(this.size);

		}
		if (this.keepAlive != null) {
			generator.writeKey("keep_alive");
			generator.write(this.keepAlive);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ThreadPoolRecord}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ThreadPoolRecord> {
		@Nullable
		private String nodeName;

		@Nullable
		private String nodeId;

		@Nullable
		private String ephemeralNodeId;

		@Nullable
		private String pid;

		@Nullable
		private String host;

		@Nullable
		private String ip;

		@Nullable
		private String port;

		@Nullable
		private String name;

		@Nullable
		private String type;

		@Nullable
		private String active;

		@Nullable
		private String poolSize;

		@Nullable
		private String queue;

		@Nullable
		private String queueSize;

		@Nullable
		private String rejected;

		@Nullable
		private String largest;

		@Nullable
		private String completed;

		@Nullable
		private String core;

		@Nullable
		private String max;

		@Nullable
		private String size;

		@Nullable
		private String keepAlive;

		/**
		 * node name
		 * <p>
		 * API name: {@code node_name}
		 */
		public final Builder nodeName(@Nullable String value) {
			this.nodeName = value;
			return this;
		}

		/**
		 * persistent node id
		 * <p>
		 * API name: {@code node_id}
		 */
		public final Builder nodeId(@Nullable String value) {
			this.nodeId = value;
			return this;
		}

		/**
		 * ephemeral node id
		 * <p>
		 * API name: {@code ephemeral_node_id}
		 */
		public final Builder ephemeralNodeId(@Nullable String value) {
			this.ephemeralNodeId = value;
			return this;
		}

		/**
		 * process id
		 * <p>
		 * API name: {@code pid}
		 */
		public final Builder pid(@Nullable String value) {
			this.pid = value;
			return this;
		}

		/**
		 * host name
		 * <p>
		 * API name: {@code host}
		 */
		public final Builder host(@Nullable String value) {
			this.host = value;
			return this;
		}

		/**
		 * ip address
		 * <p>
		 * API name: {@code ip}
		 */
		public final Builder ip(@Nullable String value) {
			this.ip = value;
			return this;
		}

		/**
		 * bound transport port
		 * <p>
		 * API name: {@code port}
		 */
		public final Builder port(@Nullable String value) {
			this.port = value;
			return this;
		}

		/**
		 * thread pool name
		 * <p>
		 * API name: {@code name}
		 */
		public final Builder name(@Nullable String value) {
			this.name = value;
			return this;
		}

		/**
		 * thread pool type
		 * <p>
		 * API name: {@code type}
		 */
		public final Builder type(@Nullable String value) {
			this.type = value;
			return this;
		}

		/**
		 * number of active threads
		 * <p>
		 * API name: {@code active}
		 */
		public final Builder active(@Nullable String value) {
			this.active = value;
			return this;
		}

		/**
		 * number of threads
		 * <p>
		 * API name: {@code pool_size}
		 */
		public final Builder poolSize(@Nullable String value) {
			this.poolSize = value;
			return this;
		}

		/**
		 * number of tasks currently in queue
		 * <p>
		 * API name: {@code queue}
		 */
		public final Builder queue(@Nullable String value) {
			this.queue = value;
			return this;
		}

		/**
		 * maximum number of tasks permitted in queue
		 * <p>
		 * API name: {@code queue_size}
		 */
		public final Builder queueSize(@Nullable String value) {
			this.queueSize = value;
			return this;
		}

		/**
		 * number of rejected tasks
		 * <p>
		 * API name: {@code rejected}
		 */
		public final Builder rejected(@Nullable String value) {
			this.rejected = value;
			return this;
		}

		/**
		 * highest number of seen active threads
		 * <p>
		 * API name: {@code largest}
		 */
		public final Builder largest(@Nullable String value) {
			this.largest = value;
			return this;
		}

		/**
		 * number of completed tasks
		 * <p>
		 * API name: {@code completed}
		 */
		public final Builder completed(@Nullable String value) {
			this.completed = value;
			return this;
		}

		/**
		 * core number of threads in a scaling thread pool
		 * <p>
		 * API name: {@code core}
		 */
		public final Builder core(@Nullable String value) {
			this.core = value;
			return this;
		}

		/**
		 * maximum number of threads in a scaling thread pool
		 * <p>
		 * API name: {@code max}
		 */
		public final Builder max(@Nullable String value) {
			this.max = value;
			return this;
		}

		/**
		 * number of threads in a fixed thread pool
		 * <p>
		 * API name: {@code size}
		 */
		public final Builder size(@Nullable String value) {
			this.size = value;
			return this;
		}

		/**
		 * thread keep alive time
		 * <p>
		 * API name: {@code keep_alive}
		 */
		public final Builder keepAlive(@Nullable String value) {
			this.keepAlive = value;
			return this;
		}

		/**
		 * Builds a {@link ThreadPoolRecord}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ThreadPoolRecord build() {
			_checkSingleUse();

			return new ThreadPoolRecord(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ThreadPoolRecord}
	 */
	public static final JsonpDeserializer<ThreadPoolRecord> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ThreadPoolRecord::setupThreadPoolRecordDeserializer);

	protected static void setupThreadPoolRecordDeserializer(ObjectDeserializer<ThreadPoolRecord.Builder> op) {

		op.add(Builder::nodeName, JsonpDeserializer.stringDeserializer(), "node_name", "nn");
		op.add(Builder::nodeId, JsonpDeserializer.stringDeserializer(), "node_id", "id");
		op.add(Builder::ephemeralNodeId, JsonpDeserializer.stringDeserializer(), "ephemeral_node_id", "eid");
		op.add(Builder::pid, JsonpDeserializer.stringDeserializer(), "pid", "p");
		op.add(Builder::host, JsonpDeserializer.stringDeserializer(), "host", "h");
		op.add(Builder::ip, JsonpDeserializer.stringDeserializer(), "ip", "i");
		op.add(Builder::port, JsonpDeserializer.stringDeserializer(), "port", "po");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name", "n");
		op.add(Builder::type, JsonpDeserializer.stringDeserializer(), "type", "t");
		op.add(Builder::active, JsonpDeserializer.stringDeserializer(), "active", "a");
		op.add(Builder::poolSize, JsonpDeserializer.stringDeserializer(), "pool_size", "psz");
		op.add(Builder::queue, JsonpDeserializer.stringDeserializer(), "queue", "q");
		op.add(Builder::queueSize, JsonpDeserializer.stringDeserializer(), "queue_size", "qs");
		op.add(Builder::rejected, JsonpDeserializer.stringDeserializer(), "rejected", "r");
		op.add(Builder::largest, JsonpDeserializer.stringDeserializer(), "largest", "l");
		op.add(Builder::completed, JsonpDeserializer.stringDeserializer(), "completed", "c");
		op.add(Builder::core, JsonpDeserializer.stringDeserializer(), "core", "cr");
		op.add(Builder::max, JsonpDeserializer.stringDeserializer(), "max", "mx");
		op.add(Builder::size, JsonpDeserializer.stringDeserializer(), "size", "sz");
		op.add(Builder::keepAlive, JsonpDeserializer.stringDeserializer(), "keep_alive", "ka");

	}

}
