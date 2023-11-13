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

package org.opensearch.client.opensearch.nodes.info;

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

// typedef: nodes.info.NodeOperatingSystemInfo

@JsonpDeserializable
public class NodeOperatingSystemInfo implements JsonpSerializable {
	private final String arch;

	private final int availableProcessors;

	@Nullable
	private final Integer allocatedProcessors;

	private final String name;

	private final String prettyName;

	private final int refreshIntervalInMillis;

	private final String version;

	@Nullable
	private final NodeInfoOSCPU cpu;

	@Nullable
	private final NodeInfoMemory mem;

	@Nullable
	private final NodeInfoMemory swap;

	// ---------------------------------------------------------------------------------------------

	private NodeOperatingSystemInfo(Builder builder) {

		this.arch = ApiTypeHelper.requireNonNull(builder.arch, this, "arch");
		this.availableProcessors = ApiTypeHelper.requireNonNull(builder.availableProcessors, this,
				"availableProcessors");
		this.allocatedProcessors = builder.allocatedProcessors;
		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.prettyName = ApiTypeHelper.requireNonNull(builder.prettyName, this, "prettyName");
		this.refreshIntervalInMillis = ApiTypeHelper.requireNonNull(builder.refreshIntervalInMillis, this,
				"refreshIntervalInMillis");
		this.version = ApiTypeHelper.requireNonNull(builder.version, this, "version");
		this.cpu = builder.cpu;
		this.mem = builder.mem;
		this.swap = builder.swap;

	}

	public static NodeOperatingSystemInfo of(Function<Builder, ObjectBuilder<NodeOperatingSystemInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - Name of the JVM architecture (ex: amd64, x86)
	 * <p>
	 * API name: {@code arch}
	 */
	public final String arch() {
		return this.arch;
	}

	/**
	 * Required - Number of processors available to the Java virtual machine
	 * <p>
	 * API name: {@code available_processors}
	 */
	public final int availableProcessors() {
		return this.availableProcessors;
	}

	/**
	 * The number of processors actually used to calculate thread pool size. This
	 * number can be set with the node.processors setting of a node and defaults to
	 * the number of processors reported by the OS.
	 * <p>
	 * API name: {@code allocated_processors}
	 */
	@Nullable
	public final Integer allocatedProcessors() {
		return this.allocatedProcessors;
	}

	/**
	 * Required - Name of the operating system (ex: Linux, Windows, Mac OS X)
	 * <p>
	 * API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * Required - API name: {@code pretty_name}
	 */
	public final String prettyName() {
		return this.prettyName;
	}

	/**
	 * Required - Refresh interval for the OS statistics
	 * <p>
	 * API name: {@code refresh_interval_in_millis}
	 */
	public final int refreshIntervalInMillis() {
		return this.refreshIntervalInMillis;
	}

	/**
	 * Required - Version of the operating system
	 * <p>
	 * API name: {@code version}
	 */
	public final String version() {
		return this.version;
	}

	/**
	 * API name: {@code cpu}
	 */
	@Nullable
	public final NodeInfoOSCPU cpu() {
		return this.cpu;
	}

	/**
	 * API name: {@code mem}
	 */
	@Nullable
	public final NodeInfoMemory mem() {
		return this.mem;
	}

	/**
	 * API name: {@code swap}
	 */
	@Nullable
	public final NodeInfoMemory swap() {
		return this.swap;
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

		generator.writeKey("arch");
		generator.write(this.arch);

		generator.writeKey("available_processors");
		generator.write(this.availableProcessors);

		if (this.allocatedProcessors != null) {
			generator.writeKey("allocated_processors");
			generator.write(this.allocatedProcessors);

		}
		generator.writeKey("name");
		generator.write(this.name);

		generator.writeKey("pretty_name");
		generator.write(this.prettyName);

		generator.writeKey("refresh_interval_in_millis");
		generator.write(this.refreshIntervalInMillis);

		generator.writeKey("version");
		generator.write(this.version);

		if (this.cpu != null) {
			generator.writeKey("cpu");
			this.cpu.serialize(generator, mapper);

		}
		if (this.mem != null) {
			generator.writeKey("mem");
			this.mem.serialize(generator, mapper);

		}
		if (this.swap != null) {
			generator.writeKey("swap");
			this.swap.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeOperatingSystemInfo}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeOperatingSystemInfo> {
		private String arch;

		private Integer availableProcessors;

		@Nullable
		private Integer allocatedProcessors;

		private String name;

		private String prettyName;

		private Integer refreshIntervalInMillis;

		private String version;

		@Nullable
		private NodeInfoOSCPU cpu;

		@Nullable
		private NodeInfoMemory mem;

		@Nullable
		private NodeInfoMemory swap;

		/**
		 * Required - Name of the JVM architecture (ex: amd64, x86)
		 * <p>
		 * API name: {@code arch}
		 */
		public final Builder arch(String value) {
			this.arch = value;
			return this;
		}

		/**
		 * Required - Number of processors available to the Java virtual machine
		 * <p>
		 * API name: {@code available_processors}
		 */
		public final Builder availableProcessors(int value) {
			this.availableProcessors = value;
			return this;
		}

		/**
		 * The number of processors actually used to calculate thread pool size. This
		 * number can be set with the node.processors setting of a node and defaults to
		 * the number of processors reported by the OS.
		 * <p>
		 * API name: {@code allocated_processors}
		 */
		public final Builder allocatedProcessors(@Nullable Integer value) {
			this.allocatedProcessors = value;
			return this;
		}

		/**
		 * Required - Name of the operating system (ex: Linux, Windows, Mac OS X)
		 * <p>
		 * API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * Required - API name: {@code pretty_name}
		 */
		public final Builder prettyName(String value) {
			this.prettyName = value;
			return this;
		}

		/**
		 * Required - Refresh interval for the OS statistics
		 * <p>
		 * API name: {@code refresh_interval_in_millis}
		 */
		public final Builder refreshIntervalInMillis(int value) {
			this.refreshIntervalInMillis = value;
			return this;
		}

		/**
		 * Required - Version of the operating system
		 * <p>
		 * API name: {@code version}
		 */
		public final Builder version(String value) {
			this.version = value;
			return this;
		}

		/**
		 * API name: {@code cpu}
		 */
		public final Builder cpu(@Nullable NodeInfoOSCPU value) {
			this.cpu = value;
			return this;
		}

		/**
		 * API name: {@code cpu}
		 */
		public final Builder cpu(Function<NodeInfoOSCPU.Builder, ObjectBuilder<NodeInfoOSCPU>> fn) {
			return this.cpu(fn.apply(new NodeInfoOSCPU.Builder()).build());
		}

		/**
		 * API name: {@code mem}
		 */
		public final Builder mem(@Nullable NodeInfoMemory value) {
			this.mem = value;
			return this;
		}

		/**
		 * API name: {@code mem}
		 */
		public final Builder mem(Function<NodeInfoMemory.Builder, ObjectBuilder<NodeInfoMemory>> fn) {
			return this.mem(fn.apply(new NodeInfoMemory.Builder()).build());
		}

		/**
		 * API name: {@code swap}
		 */
		public final Builder swap(@Nullable NodeInfoMemory value) {
			this.swap = value;
			return this;
		}

		/**
		 * API name: {@code swap}
		 */
		public final Builder swap(Function<NodeInfoMemory.Builder, ObjectBuilder<NodeInfoMemory>> fn) {
			return this.swap(fn.apply(new NodeInfoMemory.Builder()).build());
		}

		/**
		 * Builds a {@link NodeOperatingSystemInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeOperatingSystemInfo build() {
			_checkSingleUse();

			return new NodeOperatingSystemInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeOperatingSystemInfo}
	 */
	public static final JsonpDeserializer<NodeOperatingSystemInfo> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, NodeOperatingSystemInfo::setupNodeOperatingSystemInfoDeserializer);

	protected static void setupNodeOperatingSystemInfoDeserializer(
			ObjectDeserializer<NodeOperatingSystemInfo.Builder> op) {

		op.add(Builder::arch, JsonpDeserializer.stringDeserializer(), "arch");
		op.add(Builder::availableProcessors, JsonpDeserializer.integerDeserializer(), "available_processors");
		op.add(Builder::allocatedProcessors, JsonpDeserializer.integerDeserializer(), "allocated_processors");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::prettyName, JsonpDeserializer.stringDeserializer(), "pretty_name");
		op.add(Builder::refreshIntervalInMillis, JsonpDeserializer.integerDeserializer(), "refresh_interval_in_millis");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version");
		op.add(Builder::cpu, NodeInfoOSCPU._DESERIALIZER, "cpu");
		op.add(Builder::mem, NodeInfoMemory._DESERIALIZER, "mem");
		op.add(Builder::swap, NodeInfoMemory._DESERIALIZER, "swap");

	}

}
