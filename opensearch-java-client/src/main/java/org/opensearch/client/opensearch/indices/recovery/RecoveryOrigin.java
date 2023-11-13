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

package org.opensearch.client.opensearch.indices.recovery;

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

// typedef: indices.recovery.RecoveryOrigin


@JsonpDeserializable
public class RecoveryOrigin implements JsonpSerializable {
	@Nullable
	private final String hostname;

	@Nullable
	private final String host;

	@Nullable
	private final String transportAddress;

	@Nullable
	private final String id;

	@Nullable
	private final String ip;

	@Nullable
	private final String name;

	@Nullable
	private final Boolean bootstrapNewHistoryUuid;

	@Nullable
	private final String repository;

	@Nullable
	private final String snapshot;

	@Nullable
	private final String version;

	@Nullable
	private final String restoreuuid;

	@Nullable
	private final String index;

	// ---------------------------------------------------------------------------------------------

	private RecoveryOrigin(Builder builder) {

		this.hostname = builder.hostname;
		this.host = builder.host;
		this.transportAddress = builder.transportAddress;
		this.id = builder.id;
		this.ip = builder.ip;
		this.name = builder.name;
		this.bootstrapNewHistoryUuid = builder.bootstrapNewHistoryUuid;
		this.repository = builder.repository;
		this.snapshot = builder.snapshot;
		this.version = builder.version;
		this.restoreuuid = builder.restoreuuid;
		this.index = builder.index;

	}

	public static RecoveryOrigin of(Function<Builder, ObjectBuilder<RecoveryOrigin>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code hostname}
	 */
	@Nullable
	public final String hostname() {
		return this.hostname;
	}

	/**
	 * API name: {@code host}
	 */
	@Nullable
	public final String host() {
		return this.host;
	}

	/**
	 * API name: {@code transport_address}
	 */
	@Nullable
	public final String transportAddress() {
		return this.transportAddress;
	}

	/**
	 * API name: {@code id}
	 */
	@Nullable
	public final String id() {
		return this.id;
	}

	/**
	 * API name: {@code ip}
	 */
	@Nullable
	public final String ip() {
		return this.ip;
	}

	/**
	 * API name: {@code name}
	 */
	@Nullable
	public final String name() {
		return this.name;
	}

	/**
	 * API name: {@code bootstrap_new_history_uuid}
	 */
	@Nullable
	public final Boolean bootstrapNewHistoryUuid() {
		return this.bootstrapNewHistoryUuid;
	}

	/**
	 * API name: {@code repository}
	 */
	@Nullable
	public final String repository() {
		return this.repository;
	}

	/**
	 * API name: {@code snapshot}
	 */
	@Nullable
	public final String snapshot() {
		return this.snapshot;
	}

	/**
	 * API name: {@code version}
	 */
	@Nullable
	public final String version() {
		return this.version;
	}

	/**
	 * API name: {@code restoreUUID}
	 */
	@Nullable
	public final String restoreuuid() {
		return this.restoreuuid;
	}

	/**
	 * API name: {@code index}
	 */
	@Nullable
	public final String index() {
		return this.index;
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

		if (this.hostname != null) {
			generator.writeKey("hostname");
			generator.write(this.hostname);

		}
		if (this.host != null) {
			generator.writeKey("host");
			generator.write(this.host);

		}
		if (this.transportAddress != null) {
			generator.writeKey("transport_address");
			generator.write(this.transportAddress);

		}
		if (this.id != null) {
			generator.writeKey("id");
			generator.write(this.id);

		}
		if (this.ip != null) {
			generator.writeKey("ip");
			generator.write(this.ip);

		}
		if (this.name != null) {
			generator.writeKey("name");
			generator.write(this.name);

		}
		if (this.bootstrapNewHistoryUuid != null) {
			generator.writeKey("bootstrap_new_history_uuid");
			generator.write(this.bootstrapNewHistoryUuid);

		}
		if (this.repository != null) {
			generator.writeKey("repository");
			generator.write(this.repository);

		}
		if (this.snapshot != null) {
			generator.writeKey("snapshot");
			generator.write(this.snapshot);

		}
		if (this.version != null) {
			generator.writeKey("version");
			generator.write(this.version);

		}
		if (this.restoreuuid != null) {
			generator.writeKey("restoreUUID");
			generator.write(this.restoreuuid);

		}
		if (this.index != null) {
			generator.writeKey("index");
			generator.write(this.index);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RecoveryOrigin}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RecoveryOrigin> {
		@Nullable
		private String hostname;

		@Nullable
		private String host;

		@Nullable
		private String transportAddress;

		@Nullable
		private String id;

		@Nullable
		private String ip;

		@Nullable
		private String name;

		@Nullable
		private Boolean bootstrapNewHistoryUuid;

		@Nullable
		private String repository;

		@Nullable
		private String snapshot;

		@Nullable
		private String version;

		@Nullable
		private String restoreuuid;

		@Nullable
		private String index;

		/**
		 * API name: {@code hostname}
		 */
		public final Builder hostname(@Nullable String value) {
			this.hostname = value;
			return this;
		}

		/**
		 * API name: {@code host}
		 */
		public final Builder host(@Nullable String value) {
			this.host = value;
			return this;
		}

		/**
		 * API name: {@code transport_address}
		 */
		public final Builder transportAddress(@Nullable String value) {
			this.transportAddress = value;
			return this;
		}

		/**
		 * API name: {@code id}
		 */
		public final Builder id(@Nullable String value) {
			this.id = value;
			return this;
		}

		/**
		 * API name: {@code ip}
		 */
		public final Builder ip(@Nullable String value) {
			this.ip = value;
			return this;
		}

		/**
		 * API name: {@code name}
		 */
		public final Builder name(@Nullable String value) {
			this.name = value;
			return this;
		}

		/**
		 * API name: {@code bootstrap_new_history_uuid}
		 */
		public final Builder bootstrapNewHistoryUuid(@Nullable Boolean value) {
			this.bootstrapNewHistoryUuid = value;
			return this;
		}

		/**
		 * API name: {@code repository}
		 */
		public final Builder repository(@Nullable String value) {
			this.repository = value;
			return this;
		}

		/**
		 * API name: {@code snapshot}
		 */
		public final Builder snapshot(@Nullable String value) {
			this.snapshot = value;
			return this;
		}

		/**
		 * API name: {@code version}
		 */
		public final Builder version(@Nullable String value) {
			this.version = value;
			return this;
		}

		/**
		 * API name: {@code restoreUUID}
		 */
		public final Builder restoreuuid(@Nullable String value) {
			this.restoreuuid = value;
			return this;
		}

		/**
		 * API name: {@code index}
		 */
		public final Builder index(@Nullable String value) {
			this.index = value;
			return this;
		}

		/**
		 * Builds a {@link RecoveryOrigin}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RecoveryOrigin build() {
			_checkSingleUse();

			return new RecoveryOrigin(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RecoveryOrigin}
	 */
	public static final JsonpDeserializer<RecoveryOrigin> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			RecoveryOrigin::setupRecoveryOriginDeserializer);

	protected static void setupRecoveryOriginDeserializer(ObjectDeserializer<RecoveryOrigin.Builder> op) {

		op.add(Builder::hostname, JsonpDeserializer.stringDeserializer(), "hostname");
		op.add(Builder::host, JsonpDeserializer.stringDeserializer(), "host");
		op.add(Builder::transportAddress, JsonpDeserializer.stringDeserializer(), "transport_address");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "id");
		op.add(Builder::ip, JsonpDeserializer.stringDeserializer(), "ip");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::bootstrapNewHistoryUuid, JsonpDeserializer.booleanDeserializer(), "bootstrap_new_history_uuid");
		op.add(Builder::repository, JsonpDeserializer.stringDeserializer(), "repository");
		op.add(Builder::snapshot, JsonpDeserializer.stringDeserializer(), "snapshot");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version");
		op.add(Builder::restoreuuid, JsonpDeserializer.stringDeserializer(), "restoreUUID");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index");

	}

}
