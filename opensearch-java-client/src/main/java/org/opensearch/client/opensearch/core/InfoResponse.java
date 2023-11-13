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

package org.opensearch.client.opensearch.core;

import org.opensearch.client.opensearch._types.OpenSearchVersionInfo;
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

// typedef: _global.info.Response


@JsonpDeserializable
public class InfoResponse implements JsonpSerializable {
	private final String clusterName;

	private final String clusterUuid;

	private final String name;

	private final String tagline;

	private final OpenSearchVersionInfo version;

	// ---------------------------------------------------------------------------------------------

	private InfoResponse(Builder builder) {

		this.clusterName = ApiTypeHelper.requireNonNull(builder.clusterName, this, "clusterName");
		this.clusterUuid = ApiTypeHelper.requireNonNull(builder.clusterUuid, this, "clusterUuid");
		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.tagline = ApiTypeHelper.requireNonNull(builder.tagline, this, "tagline");
		this.version = ApiTypeHelper.requireNonNull(builder.version, this, "version");

	}

	public static InfoResponse of(Function<Builder, ObjectBuilder<InfoResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code cluster_name}
	 */
	public final String clusterName() {
		return this.clusterName;
	}

	/**
	 * Required - API name: {@code cluster_uuid}
	 */
	public final String clusterUuid() {
		return this.clusterUuid;
	}

	/**
	 * Required - API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * Required - API name: {@code tagline}
	 */
	public final String tagline() {
		return this.tagline;
	}

	/**
	 * Required - API name: {@code version}
	 */
	public final OpenSearchVersionInfo version() {
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

		generator.writeKey("cluster_name");
		generator.write(this.clusterName);

		generator.writeKey("cluster_uuid");
		generator.write(this.clusterUuid);

		generator.writeKey("name");
		generator.write(this.name);

		generator.writeKey("tagline");
		generator.write(this.tagline);

		generator.writeKey("version");
		this.version.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link InfoResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<InfoResponse> {
		private String clusterName;

		private String clusterUuid;

		private String name;

		private String tagline;

		private OpenSearchVersionInfo version;

		/**
		 * Required - API name: {@code cluster_name}
		 */
		public final Builder clusterName(String value) {
			this.clusterName = value;
			return this;
		}

		/**
		 * Required - API name: {@code cluster_uuid}
		 */
		public final Builder clusterUuid(String value) {
			this.clusterUuid = value;
			return this;
		}

		/**
		 * Required - API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * Required - API name: {@code tagline}
		 */
		public final Builder tagline(String value) {
			this.tagline = value;
			return this;
		}

		/**
		 * Required - API name: {@code version}
		 */
		public final Builder version(OpenSearchVersionInfo value) {
			this.version = value;
			return this;
		}

		/**
		 * Required - API name: {@code version}
		 */
		public final Builder version(
				Function<OpenSearchVersionInfo.Builder, ObjectBuilder<OpenSearchVersionInfo>> fn) {
			return this.version(fn.apply(new OpenSearchVersionInfo.Builder()).build());
		}

		/**
		 * Builds a {@link InfoResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public InfoResponse build() {
			_checkSingleUse();

			return new InfoResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link InfoResponse}
	 */
	public static final JsonpDeserializer<InfoResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			InfoResponse::setupInfoResponseDeserializer);

	protected static void setupInfoResponseDeserializer(ObjectDeserializer<InfoResponse.Builder> op) {

		op.add(Builder::clusterName, JsonpDeserializer.stringDeserializer(), "cluster_name");
		op.add(Builder::clusterUuid, JsonpDeserializer.stringDeserializer(), "cluster_uuid");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::tagline, JsonpDeserializer.stringDeserializer(), "tagline");
		op.add(Builder::version, OpenSearchVersionInfo._DESERIALIZER, "version");

	}

}
