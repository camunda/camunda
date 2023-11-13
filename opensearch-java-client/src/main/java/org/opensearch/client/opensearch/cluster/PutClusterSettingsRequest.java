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

package org.opensearch.client.opensearch.cluster;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: cluster.put_settings.Request

/**
 * Updates the cluster settings.
 * 
 */
@JsonpDeserializable
public class PutClusterSettingsRequest extends RequestBase implements JsonpSerializable {
	@Nullable
	private final Boolean flatSettings;

	@Deprecated
	@Nullable
	private final Time masterTimeout;

	@Nullable
	private final Time clusterManagerTimeout;

	private final Map<String, JsonData> persistent;

	@Nullable
	private final Time timeout;

	private final Map<String, JsonData> transient_;

	// ---------------------------------------------------------------------------------------------

	private PutClusterSettingsRequest(Builder builder) {

		this.flatSettings = builder.flatSettings;
		this.masterTimeout = builder.masterTimeout;
		this.clusterManagerTimeout = builder.clusterManagerTimeout;
		this.persistent = ApiTypeHelper.unmodifiable(builder.persistent);
		this.timeout = builder.timeout;
		this.transient_ = ApiTypeHelper.unmodifiable(builder.transient_);

	}

	public static PutClusterSettingsRequest of(Function<Builder, ObjectBuilder<PutClusterSettingsRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Return settings in flat format (default: false)
	 * <p>
	 * API name: {@code flat_settings}
	 */
	@Nullable
	public final Boolean flatSettings() {
		return this.flatSettings;
	}

	/**
	 * Explicit operation timeout for connection to master node
	 * <p>
	 * API name: {@code master_timeout}
	 */
	@Deprecated
	@Nullable
	public final Time masterTimeout() {
		return this.masterTimeout;
	}

	/**
	 * Explicit operation timeout for connection to cluster-manager node
	 * <p>
	 * API name: {@code cluster_manager_timeout}
	 */
	@Nullable
	public final Time clusterManagerTimeout() {
		return this.clusterManagerTimeout;
	}

	/**
	 * API name: {@code persistent}
	 */
	public final Map<String, JsonData> persistent() {
		return this.persistent;
	}

	/**
	 * Explicit operation timeout
	 * <p>
	 * API name: {@code timeout}
	 */
	@Nullable
	public final Time timeout() {
		return this.timeout;
	}

	/**
	 * API name: {@code transient}
	 */
	public final Map<String, JsonData> transient_() {
		return this.transient_;
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

		if (ApiTypeHelper.isDefined(this.persistent)) {
			generator.writeKey("persistent");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.persistent.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.transient_)) {
			generator.writeKey("transient");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.transient_.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link PutClusterSettingsRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<PutClusterSettingsRequest> {
		@Nullable
		private Boolean flatSettings;

		@Deprecated
		@Nullable
		private Time masterTimeout;

		@Nullable
		private Time clusterManagerTimeout;

		@Nullable
		private Map<String, JsonData> persistent;

		@Nullable
		private Time timeout;

		@Nullable
		private Map<String, JsonData> transient_;

		/**
		 * Return settings in flat format (default: false)
		 * <p>
		 * API name: {@code flat_settings}
		 */
		public final Builder flatSettings(@Nullable Boolean value) {
			this.flatSettings = value;
			return this;
		}

		/**
		 * Explicit operation timeout for connection to master node
		 * <p>
		 * API name: {@code master_timeout}
		 */
		public final Builder masterTimeout(@Nullable Time value) {
			this.masterTimeout = value;
			return this;
		}

		/**
		 * Explicit operation timeout for connection to master node
		 * <p>
		 * API name: {@code master_timeout}
		 */
		public final Builder masterTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.masterTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Explicit operation timeout for connection to cluster-manager node
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(@Nullable Time value) {
			this.clusterManagerTimeout = value;
			return this;
		}

		/**
		 * Explicit operation timeout for connection to cluster-manager node
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.clusterManagerTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code persistent}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>persistent</code>.
		 */
		public final Builder persistent(Map<String, JsonData> map) {
			this.persistent = _mapPutAll(this.persistent, map);
			return this;
		}

		/**
		 * API name: {@code persistent}
		 * <p>
		 * Adds an entry to <code>persistent</code>.
		 */
		public final Builder persistent(String key, JsonData value) {
			this.persistent = _mapPut(this.persistent, key, value);
			return this;
		}

		/**
		 * Explicit operation timeout
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder timeout(@Nullable Time value) {
			this.timeout = value;
			return this;
		}

		/**
		 * Explicit operation timeout
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder timeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.timeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code transient}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>transient_</code>.
		 */
		public final Builder transient_(Map<String, JsonData> map) {
			this.transient_ = _mapPutAll(this.transient_, map);
			return this;
		}

		/**
		 * API name: {@code transient}
		 * <p>
		 * Adds an entry to <code>transient_</code>.
		 */
		public final Builder transient_(String key, JsonData value) {
			this.transient_ = _mapPut(this.transient_, key, value);
			return this;
		}

		/**
		 * Builds a {@link PutClusterSettingsRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public PutClusterSettingsRequest build() {
			_checkSingleUse();

			return new PutClusterSettingsRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link PutClusterSettingsRequest}
	 */
	public static final JsonpDeserializer<PutClusterSettingsRequest> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, PutClusterSettingsRequest::setupPutClusterSettingsRequestDeserializer);

	protected static void setupPutClusterSettingsRequestDeserializer(
			ObjectDeserializer<PutClusterSettingsRequest.Builder> op) {

		op.add(Builder::persistent, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "persistent");
		op.add(Builder::transient_, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "transient");

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code cluster.put_settings}".
	 */
	public static final Endpoint<PutClusterSettingsRequest, PutClusterSettingsResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "PUT";

			},

			// Request path
			request -> {
				return "/_cluster/settings";

			},

			// Request parameters
			request -> {
				Map<String, String> params = new HashMap<>();
				if (request.masterTimeout != null) {
					params.put("master_timeout", request.masterTimeout._toJsonString());
				}
				if (request.clusterManagerTimeout != null) {
					params.put("cluster_manager_timeout", request.clusterManagerTimeout._toJsonString());
				}
				if (request.flatSettings != null) {
					params.put("flat_settings", String.valueOf(request.flatSettings));
				}
				if (request.timeout != null) {
					params.put("timeout", request.timeout._toJsonString());
				}
				return params;

			}, SimpleEndpoint.emptyMap(), true, PutClusterSettingsResponse._DESERIALIZER);
}
