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

package org.opensearch.client.opensearch.cat.nodeattrs;

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

// typedef: cat.nodeattrs.NodeAttributesRecord


@JsonpDeserializable
public class NodeAttributesRecord implements JsonpSerializable {
	@Nullable
	private final String node;

	@Nullable
	private final String id;

	@Nullable
	private final String pid;

	@Nullable
	private final String host;

	@Nullable
	private final String ip;

	@Nullable
	private final String port;

	@Nullable
	private final String attr;

	@Nullable
	private final String value;

	// ---------------------------------------------------------------------------------------------

	private NodeAttributesRecord(Builder builder) {

		this.node = builder.node;
		this.id = builder.id;
		this.pid = builder.pid;
		this.host = builder.host;
		this.ip = builder.ip;
		this.port = builder.port;
		this.attr = builder.attr;
		this.value = builder.value;

	}

	public static NodeAttributesRecord of(Function<Builder, ObjectBuilder<NodeAttributesRecord>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * node name
	 * <p>
	 * API name: {@code node}
	 */
	@Nullable
	public final String node() {
		return this.node;
	}

	/**
	 * unique node id
	 * <p>
	 * API name: {@code id}
	 */
	@Nullable
	public final String id() {
		return this.id;
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
	 * attribute description
	 * <p>
	 * API name: {@code attr}
	 */
	@Nullable
	public final String attr() {
		return this.attr;
	}

	/**
	 * attribute value
	 * <p>
	 * API name: {@code value}
	 */
	@Nullable
	public final String value() {
		return this.value;
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

		if (this.node != null) {
			generator.writeKey("node");
			generator.write(this.node);

		}
		if (this.id != null) {
			generator.writeKey("id");
			generator.write(this.id);

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
		if (this.attr != null) {
			generator.writeKey("attr");
			generator.write(this.attr);

		}
		if (this.value != null) {
			generator.writeKey("value");
			generator.write(this.value);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeAttributesRecord}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeAttributesRecord> {
		@Nullable
		private String node;

		@Nullable
		private String id;

		@Nullable
		private String pid;

		@Nullable
		private String host;

		@Nullable
		private String ip;

		@Nullable
		private String port;

		@Nullable
		private String attr;

		@Nullable
		private String value;

		/**
		 * node name
		 * <p>
		 * API name: {@code node}
		 */
		public final Builder node(@Nullable String value) {
			this.node = value;
			return this;
		}

		/**
		 * unique node id
		 * <p>
		 * API name: {@code id}
		 */
		public final Builder id(@Nullable String value) {
			this.id = value;
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
		 * attribute description
		 * <p>
		 * API name: {@code attr}
		 */
		public final Builder attr(@Nullable String value) {
			this.attr = value;
			return this;
		}

		/**
		 * attribute value
		 * <p>
		 * API name: {@code value}
		 */
		public final Builder value(@Nullable String value) {
			this.value = value;
			return this;
		}

		/**
		 * Builds a {@link NodeAttributesRecord}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeAttributesRecord build() {
			_checkSingleUse();

			return new NodeAttributesRecord(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeAttributesRecord}
	 */
	public static final JsonpDeserializer<NodeAttributesRecord> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, NodeAttributesRecord::setupNodeAttributesRecordDeserializer);

	protected static void setupNodeAttributesRecordDeserializer(ObjectDeserializer<NodeAttributesRecord.Builder> op) {

		op.add(Builder::node, JsonpDeserializer.stringDeserializer(), "node");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "id");
		op.add(Builder::pid, JsonpDeserializer.stringDeserializer(), "pid");
		op.add(Builder::host, JsonpDeserializer.stringDeserializer(), "host", "h");
		op.add(Builder::ip, JsonpDeserializer.stringDeserializer(), "ip", "i");
		op.add(Builder::port, JsonpDeserializer.stringDeserializer(), "port");
		op.add(Builder::attr, JsonpDeserializer.stringDeserializer(), "attr");
		op.add(Builder::value, JsonpDeserializer.stringDeserializer(), "value");

	}

}
