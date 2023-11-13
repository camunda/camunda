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

package org.opensearch.client.opensearch.cluster.remote_info;

import org.opensearch.client.opensearch._types.Time;
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

// typedef: cluster.remote_info.ClusterRemoteProxyInfo

@JsonpDeserializable
public class ClusterRemoteProxyInfo implements ClusterRemoteInfoVariant, JsonpSerializable {
	private final boolean connected;

	private final Time initialConnectTimeout;

	private final boolean skipUnavailable;

	private final String proxyAddress;

	private final String serverName;

	private final int numProxySocketsConnected;

	private final int maxProxySocketConnections;

	// ---------------------------------------------------------------------------------------------

	private ClusterRemoteProxyInfo(Builder builder) {

		this.connected = ApiTypeHelper.requireNonNull(builder.connected, this, "connected");
		this.initialConnectTimeout = ApiTypeHelper.requireNonNull(builder.initialConnectTimeout, this,
				"initialConnectTimeout");
		this.skipUnavailable = ApiTypeHelper.requireNonNull(builder.skipUnavailable, this, "skipUnavailable");
		this.proxyAddress = ApiTypeHelper.requireNonNull(builder.proxyAddress, this, "proxyAddress");
		this.serverName = ApiTypeHelper.requireNonNull(builder.serverName, this, "serverName");
		this.numProxySocketsConnected = ApiTypeHelper.requireNonNull(builder.numProxySocketsConnected, this,
				"numProxySocketsConnected");
		this.maxProxySocketConnections = ApiTypeHelper.requireNonNull(builder.maxProxySocketConnections, this,
				"maxProxySocketConnections");

	}

	public static ClusterRemoteProxyInfo of(Function<Builder, ObjectBuilder<ClusterRemoteProxyInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * ClusterRemoteInfo variant kind.
	 */
	@Override
	public ClusterRemoteInfo.Kind _clusterRemoteInfoKind() {
		return ClusterRemoteInfo.Kind.Proxy;
	}

	/**
	 * Required - API name: {@code connected}
	 */
	public final boolean connected() {
		return this.connected;
	}

	/**
	 * Required - API name: {@code initial_connect_timeout}
	 */
	public final Time initialConnectTimeout() {
		return this.initialConnectTimeout;
	}

	/**
	 * Required - API name: {@code skip_unavailable}
	 */
	public final boolean skipUnavailable() {
		return this.skipUnavailable;
	}

	/**
	 * Required - API name: {@code proxy_address}
	 */
	public final String proxyAddress() {
		return this.proxyAddress;
	}

	/**
	 * Required - API name: {@code server_name}
	 */
	public final String serverName() {
		return this.serverName;
	}

	/**
	 * Required - API name: {@code num_proxy_sockets_connected}
	 */
	public final int numProxySocketsConnected() {
		return this.numProxySocketsConnected;
	}

	/**
	 * Required - API name: {@code max_proxy_socket_connections}
	 */
	public final int maxProxySocketConnections() {
		return this.maxProxySocketConnections;
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

		generator.write("mode", "proxy");

		generator.writeKey("connected");
		generator.write(this.connected);

		generator.writeKey("initial_connect_timeout");
		this.initialConnectTimeout.serialize(generator, mapper);

		generator.writeKey("skip_unavailable");
		generator.write(this.skipUnavailable);

		generator.writeKey("proxy_address");
		generator.write(this.proxyAddress);

		generator.writeKey("server_name");
		generator.write(this.serverName);

		generator.writeKey("num_proxy_sockets_connected");
		generator.write(this.numProxySocketsConnected);

		generator.writeKey("max_proxy_socket_connections");
		generator.write(this.maxProxySocketConnections);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ClusterRemoteProxyInfo}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ClusterRemoteProxyInfo> {
		private Boolean connected;

		private Time initialConnectTimeout;

		private Boolean skipUnavailable;

		private String proxyAddress;

		private String serverName;

		private Integer numProxySocketsConnected;

		private Integer maxProxySocketConnections;

		/**
		 * Required - API name: {@code connected}
		 */
		public final Builder connected(boolean value) {
			this.connected = value;
			return this;
		}

		/**
		 * Required - API name: {@code initial_connect_timeout}
		 */
		public final Builder initialConnectTimeout(Time value) {
			this.initialConnectTimeout = value;
			return this;
		}

		/**
		 * Required - API name: {@code initial_connect_timeout}
		 */
		public final Builder initialConnectTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.initialConnectTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Required - API name: {@code skip_unavailable}
		 */
		public final Builder skipUnavailable(boolean value) {
			this.skipUnavailable = value;
			return this;
		}

		/**
		 * Required - API name: {@code proxy_address}
		 */
		public final Builder proxyAddress(String value) {
			this.proxyAddress = value;
			return this;
		}

		/**
		 * Required - API name: {@code server_name}
		 */
		public final Builder serverName(String value) {
			this.serverName = value;
			return this;
		}

		/**
		 * Required - API name: {@code num_proxy_sockets_connected}
		 */
		public final Builder numProxySocketsConnected(int value) {
			this.numProxySocketsConnected = value;
			return this;
		}

		/**
		 * Required - API name: {@code max_proxy_socket_connections}
		 */
		public final Builder maxProxySocketConnections(int value) {
			this.maxProxySocketConnections = value;
			return this;
		}

		/**
		 * Builds a {@link ClusterRemoteProxyInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ClusterRemoteProxyInfo build() {
			_checkSingleUse();

			return new ClusterRemoteProxyInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ClusterRemoteProxyInfo}
	 */
	public static final JsonpDeserializer<ClusterRemoteProxyInfo> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ClusterRemoteProxyInfo::setupClusterRemoteProxyInfoDeserializer);

	protected static void setupClusterRemoteProxyInfoDeserializer(
			ObjectDeserializer<ClusterRemoteProxyInfo.Builder> op) {

		op.add(Builder::connected, JsonpDeserializer.booleanDeserializer(), "connected");
		op.add(Builder::initialConnectTimeout, Time._DESERIALIZER, "initial_connect_timeout");
		op.add(Builder::skipUnavailable, JsonpDeserializer.booleanDeserializer(), "skip_unavailable");
		op.add(Builder::proxyAddress, JsonpDeserializer.stringDeserializer(), "proxy_address");
		op.add(Builder::serverName, JsonpDeserializer.stringDeserializer(), "server_name");
		op.add(Builder::numProxySocketsConnected, JsonpDeserializer.integerDeserializer(),
				"num_proxy_sockets_connected");
		op.add(Builder::maxProxySocketConnections, JsonpDeserializer.integerDeserializer(),
				"max_proxy_socket_connections");

		op.ignore("mode");
	}

}
