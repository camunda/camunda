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

package org.opensearch.client.opensearch.core.reindex;

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

// typedef: _global.reindex.RemoteSource


@JsonpDeserializable
public class RemoteSource implements JsonpSerializable {
	private final Time connectTimeout;

	private final String host;

	private final String username;

	private final String password;

	private final Time socketTimeout;

	// ---------------------------------------------------------------------------------------------

	private RemoteSource(Builder builder) {

		this.connectTimeout = ApiTypeHelper.requireNonNull(builder.connectTimeout, this, "connectTimeout");
		this.host = ApiTypeHelper.requireNonNull(builder.host, this, "host");
		this.username = ApiTypeHelper.requireNonNull(builder.username, this, "username");
		this.password = ApiTypeHelper.requireNonNull(builder.password, this, "password");
		this.socketTimeout = ApiTypeHelper.requireNonNull(builder.socketTimeout, this, "socketTimeout");

	}

	public static RemoteSource of(Function<Builder, ObjectBuilder<RemoteSource>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code connect_timeout}
	 */
	public final Time connectTimeout() {
		return this.connectTimeout;
	}

	/**
	 * Required - API name: {@code host}
	 */
	public final String host() {
		return this.host;
	}

	/**
	 * Required - API name: {@code username}
	 */
	public final String username() {
		return this.username;
	}

	/**
	 * Required - API name: {@code password}
	 */
	public final String password() {
		return this.password;
	}

	/**
	 * Required - API name: {@code socket_timeout}
	 */
	public final Time socketTimeout() {
		return this.socketTimeout;
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

		generator.writeKey("connect_timeout");
		this.connectTimeout.serialize(generator, mapper);

		generator.writeKey("host");
		generator.write(this.host);

		generator.writeKey("username");
		generator.write(this.username);

		generator.writeKey("password");
		generator.write(this.password);

		generator.writeKey("socket_timeout");
		this.socketTimeout.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RemoteSource}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RemoteSource> {
		private Time connectTimeout;

		private String host;

		private String username;

		private String password;

		private Time socketTimeout;

		/**
		 * Required - API name: {@code connect_timeout}
		 */
		public final Builder connectTimeout(Time value) {
			this.connectTimeout = value;
			return this;
		}

		/**
		 * Required - API name: {@code connect_timeout}
		 */
		public final Builder connectTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.connectTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Required - API name: {@code host}
		 */
		public final Builder host(String value) {
			this.host = value;
			return this;
		}

		/**
		 * Required - API name: {@code username}
		 */
		public final Builder username(String value) {
			this.username = value;
			return this;
		}

		/**
		 * Required - API name: {@code password}
		 */
		public final Builder password(String value) {
			this.password = value;
			return this;
		}

		/**
		 * Required - API name: {@code socket_timeout}
		 */
		public final Builder socketTimeout(Time value) {
			this.socketTimeout = value;
			return this;
		}

		/**
		 * Required - API name: {@code socket_timeout}
		 */
		public final Builder socketTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.socketTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Builds a {@link RemoteSource}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RemoteSource build() {
			_checkSingleUse();

			return new RemoteSource(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RemoteSource}
	 */
	public static final JsonpDeserializer<RemoteSource> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			RemoteSource::setupRemoteSourceDeserializer);

	protected static void setupRemoteSourceDeserializer(ObjectDeserializer<RemoteSource.Builder> op) {

		op.add(Builder::connectTimeout, Time._DESERIALIZER, "connect_timeout");
		op.add(Builder::host, JsonpDeserializer.stringDeserializer(), "host");
		op.add(Builder::username, JsonpDeserializer.stringDeserializer(), "username");
		op.add(Builder::password, JsonpDeserializer.stringDeserializer(), "password");
		op.add(Builder::socketTimeout, Time._DESERIALIZER, "socket_timeout");

	}

}
