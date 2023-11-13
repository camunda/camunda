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

package org.opensearch.client.opensearch.snapshot;

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
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: snapshot.create.Request

/**
 * Creates a snapshot in a repository.
 * 
 */
@JsonpDeserializable
public class CreateSnapshotRequest extends RequestBase implements JsonpSerializable {
	private final List<String> featureStates;

	@Nullable
	private final Boolean ignoreUnavailable;

	@Nullable
	private final Boolean includeGlobalState;

	private final List<String> indices;

	@Deprecated
	@Nullable
	private final Time masterTimeout;

	@Nullable
	private final Time clusterManagerTimeout;

	private final Map<String, JsonData> metadata;

	@Nullable
	private final Boolean partial;

	private final String repository;

	private final String snapshot;

	@Nullable
	private final Boolean waitForCompletion;

	// ---------------------------------------------------------------------------------------------

	private CreateSnapshotRequest(Builder builder) {

		this.featureStates = ApiTypeHelper.unmodifiable(builder.featureStates);
		this.ignoreUnavailable = builder.ignoreUnavailable;
		this.includeGlobalState = builder.includeGlobalState;
		this.indices = ApiTypeHelper.unmodifiable(builder.indices);
		this.masterTimeout = builder.masterTimeout;
		this.clusterManagerTimeout = builder.clusterManagerTimeout;
		this.metadata = ApiTypeHelper.unmodifiable(builder.metadata);
		this.partial = builder.partial;
		this.repository = ApiTypeHelper.requireNonNull(builder.repository, this, "repository");
		this.snapshot = ApiTypeHelper.requireNonNull(builder.snapshot, this, "snapshot");
		this.waitForCompletion = builder.waitForCompletion;

	}

	public static CreateSnapshotRequest of(Function<Builder, ObjectBuilder<CreateSnapshotRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Feature states to include in the snapshot. Each feature state includes one or
	 * more system indices containing related data. You can view a list of eligible
	 * features using the get features API. If <code>include_global_state</code> is
	 * <code>true</code>, all current feature states are included by default. If
	 * <code>include_global_state</code> is <code>false</code>, no feature states
	 * are included by default.
	 * <p>
	 * API name: {@code feature_states}
	 */
	public final List<String> featureStates() {
		return this.featureStates;
	}

	/**
	 * If <code>true</code>, the request ignores data streams and indices in
	 * <code>indices</code> that are missing or closed. If <code>false</code>, the
	 * request returns an error for any data stream or index that is missing or
	 * closed.
	 * <p>
	 * API name: {@code ignore_unavailable}
	 */
	@Nullable
	public final Boolean ignoreUnavailable() {
		return this.ignoreUnavailable;
	}

	/**
	 * If <code>true</code>, the current cluster state is included in the snapshot.
	 * The cluster state includes persistent cluster settings, composable index
	 * templates, legacy index templates, ingest pipelines, and ILM policies. It
	 * also includes data stored in system indices, such as Watches and task records
	 * (configurable via <code>feature_states</code>).
	 * <p>
	 * API name: {@code include_global_state}
	 */
	@Nullable
	public final Boolean includeGlobalState() {
		return this.includeGlobalState;
	}

	/**
	 * Data streams and indices to include in the snapshot. Supports multi-target
	 * syntax. Includes all data streams and indices by default.
	 * <p>
	 * API name: {@code indices}
	 */
	public final List<String> indices() {
		return this.indices;
	}

	/**
	 * Period to wait for a connection to the master node. If no response is
	 * received before the timeout expires, the request fails and returns an error.
	 * <p>
	 * API name: {@code master_timeout}
	 */
	@Deprecated
	@Nullable
	public final Time masterTimeout() {
		return this.masterTimeout;
	}

	/**
	 * Period to wait for a connection to the cluster-manager node. If no response is
	 * received before the timeout expires, the request fails and returns an error.
	 * <p>
	 * API name: {@code cluster_manager_timeout}
	 */
	@Nullable
	public final Time clusterManagerTimeout() {
		return this.clusterManagerTimeout;
	}

	/**
	 * Optional metadata for the snapshot. May have any contents. Must be less than
	 * 1024 bytes. This map is not automatically generated by Elasticsearch.
	 * <p>
	 * API name: {@code metadata}
	 */
	public final Map<String, JsonData> metadata() {
		return this.metadata;
	}

	/**
	 * If <code>true</code>, allows restoring a partial snapshot of indices with
	 * unavailable shards. Only shards that were successfully included in the
	 * snapshot will be restored. All missing shards will be recreated as empty. If
	 * <code>false</code>, the entire restore operation will fail if one or more
	 * indices included in the snapshot do not have all primary shards available.
	 * <p>
	 * API name: {@code partial}
	 */
	@Nullable
	public final Boolean partial() {
		return this.partial;
	}

	/**
	 * Required - Repository for the snapshot.
	 * <p>
	 * API name: {@code repository}
	 */
	public final String repository() {
		return this.repository;
	}

	/**
	 * Required - Name of the snapshot. Must be unique in the repository.
	 * <p>
	 * API name: {@code snapshot}
	 */
	public final String snapshot() {
		return this.snapshot;
	}

	/**
	 * If <code>true</code>, the request returns a response when the snapshot is
	 * complete. If <code>false</code>, the request returns a response when the
	 * snapshot initializes.
	 * <p>
	 * API name: {@code wait_for_completion}
	 */
	@Nullable
	public final Boolean waitForCompletion() {
		return this.waitForCompletion;
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

		if (ApiTypeHelper.isDefined(this.featureStates)) {
			generator.writeKey("feature_states");
			generator.writeStartArray();
			for (String item0 : this.featureStates) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.ignoreUnavailable != null) {
			generator.writeKey("ignore_unavailable");
			generator.write(this.ignoreUnavailable);

		}
		if (this.includeGlobalState != null) {
			generator.writeKey("include_global_state");
			generator.write(this.includeGlobalState);

		}
		if (ApiTypeHelper.isDefined(this.indices)) {
			generator.writeKey("indices");
			generator.writeStartArray();
			for (String item0 : this.indices) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.metadata)) {
			generator.writeKey("metadata");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.metadata.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.partial != null) {
			generator.writeKey("partial");
			generator.write(this.partial);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CreateSnapshotRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<CreateSnapshotRequest> {
		@Nullable
		private List<String> featureStates;

		@Nullable
		private Boolean ignoreUnavailable;

		@Nullable
		private Boolean includeGlobalState;

		@Nullable
		private List<String> indices;

		@Deprecated
		@Nullable
		private Time masterTimeout;

		@Nullable
		private Time clusterManagerTimeout;

		@Nullable
		private Map<String, JsonData> metadata;

		@Nullable
		private Boolean partial;

		private String repository;

		private String snapshot;

		@Nullable
		private Boolean waitForCompletion;

		/**
		 * Feature states to include in the snapshot. Each feature state includes one or
		 * more system indices containing related data. You can view a list of eligible
		 * features using the get features API. If <code>include_global_state</code> is
		 * <code>true</code>, all current feature states are included by default. If
		 * <code>include_global_state</code> is <code>false</code>, no feature states
		 * are included by default.
		 * <p>
		 * API name: {@code feature_states}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>featureStates</code>.
		 */
		public final Builder featureStates(List<String> list) {
			this.featureStates = _listAddAll(this.featureStates, list);
			return this;
		}

		/**
		 * Feature states to include in the snapshot. Each feature state includes one or
		 * more system indices containing related data. You can view a list of eligible
		 * features using the get features API. If <code>include_global_state</code> is
		 * <code>true</code>, all current feature states are included by default. If
		 * <code>include_global_state</code> is <code>false</code>, no feature states
		 * are included by default.
		 * <p>
		 * API name: {@code feature_states}
		 * <p>
		 * Adds one or more values to <code>featureStates</code>.
		 */
		public final Builder featureStates(String value, String... values) {
			this.featureStates = _listAdd(this.featureStates, value, values);
			return this;
		}

		/**
		 * If <code>true</code>, the request ignores data streams and indices in
		 * <code>indices</code> that are missing or closed. If <code>false</code>, the
		 * request returns an error for any data stream or index that is missing or
		 * closed.
		 * <p>
		 * API name: {@code ignore_unavailable}
		 */
		public final Builder ignoreUnavailable(@Nullable Boolean value) {
			this.ignoreUnavailable = value;
			return this;
		}

		/**
		 * If <code>true</code>, the current cluster state is included in the snapshot.
		 * The cluster state includes persistent cluster settings, composable index
		 * templates, legacy index templates, ingest pipelines, and ILM policies. It
		 * also includes data stored in system indices, such as Watches and task records
		 * (configurable via <code>feature_states</code>).
		 * <p>
		 * API name: {@code include_global_state}
		 */
		public final Builder includeGlobalState(@Nullable Boolean value) {
			this.includeGlobalState = value;
			return this;
		}

		/**
		 * Data streams and indices to include in the snapshot. Supports multi-target
		 * syntax. Includes all data streams and indices by default.
		 * <p>
		 * API name: {@code indices}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>indices</code>.
		 */
		public final Builder indices(List<String> list) {
			this.indices = _listAddAll(this.indices, list);
			return this;
		}

		/**
		 * Data streams and indices to include in the snapshot. Supports multi-target
		 * syntax. Includes all data streams and indices by default.
		 * <p>
		 * API name: {@code indices}
		 * <p>
		 * Adds one or more values to <code>indices</code>.
		 */
		public final Builder indices(String value, String... values) {
			this.indices = _listAdd(this.indices, value, values);
			return this;
		}

		/**
		 * Period to wait for a connection to the master node. If no response is
		 * received before the timeout expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code master_timeout}
		 */
		@Deprecated
		public final Builder masterTimeout(@Nullable Time value) {
			this.masterTimeout = value;
			return this;
		}

		/**
		 * Period to wait for a connection to the master node. If no response is
		 * received before the timeout expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code master_timeout}
		 */
		@Deprecated
		public final Builder masterTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.masterTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Period to wait for a connection to the cluster-manager node. If no response is
		 * received before the timeout expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(@Nullable Time value) {
			this.clusterManagerTimeout = value;
			return this;
		}

		/**
		 * Period to wait for a connection to the cluster-manager node. If no response is
		 * received before the timeout expires, the request fails and returns an error.
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.clusterManagerTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Optional metadata for the snapshot. May have any contents. Must be less than
		 * 1024 bytes. This map is not automatically generated by Elasticsearch.
		 * <p>
		 * API name: {@code metadata}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>metadata</code>.
		 */
		public final Builder metadata(Map<String, JsonData> map) {
			this.metadata = _mapPutAll(this.metadata, map);
			return this;
		}

		/**
		 * Optional metadata for the snapshot. May have any contents. Must be less than
		 * 1024 bytes. This map is not automatically generated by Elasticsearch.
		 * <p>
		 * API name: {@code metadata}
		 * <p>
		 * Adds an entry to <code>metadata</code>.
		 */
		public final Builder metadata(String key, JsonData value) {
			this.metadata = _mapPut(this.metadata, key, value);
			return this;
		}

		/**
		 * If <code>true</code>, allows restoring a partial snapshot of indices with
		 * unavailable shards. Only shards that were successfully included in the
		 * snapshot will be restored. All missing shards will be recreated as empty. If
		 * <code>false</code>, the entire restore operation will fail if one or more
		 * indices included in the snapshot do not have all primary shards available.
		 * <p>
		 * API name: {@code partial}
		 */
		public final Builder partial(@Nullable Boolean value) {
			this.partial = value;
			return this;
		}

		/**
		 * Required - Repository for the snapshot.
		 * <p>
		 * API name: {@code repository}
		 */
		public final Builder repository(String value) {
			this.repository = value;
			return this;
		}

		/**
		 * Required - Name of the snapshot. Must be unique in the repository.
		 * <p>
		 * API name: {@code snapshot}
		 */
		public final Builder snapshot(String value) {
			this.snapshot = value;
			return this;
		}

		/**
		 * If <code>true</code>, the request returns a response when the snapshot is
		 * complete. If <code>false</code>, the request returns a response when the
		 * snapshot initializes.
		 * <p>
		 * API name: {@code wait_for_completion}
		 */
		public final Builder waitForCompletion(@Nullable Boolean value) {
			this.waitForCompletion = value;
			return this;
		}

		/**
		 * Builds a {@link CreateSnapshotRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CreateSnapshotRequest build() {
			_checkSingleUse();

			return new CreateSnapshotRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CreateSnapshotRequest}
	 */
	public static final JsonpDeserializer<CreateSnapshotRequest> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, CreateSnapshotRequest::setupCreateSnapshotRequestDeserializer);

	protected static void setupCreateSnapshotRequestDeserializer(ObjectDeserializer<CreateSnapshotRequest.Builder> op) {

		op.add(Builder::featureStates, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"feature_states");
		op.add(Builder::ignoreUnavailable, JsonpDeserializer.booleanDeserializer(), "ignore_unavailable");
		op.add(Builder::includeGlobalState, JsonpDeserializer.booleanDeserializer(), "include_global_state");
		op.add(Builder::indices, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"indices");
		op.add(Builder::metadata, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "metadata");
		op.add(Builder::partial, JsonpDeserializer.booleanDeserializer(), "partial");

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code snapshot.create}".
	 */
	public static final Endpoint<CreateSnapshotRequest, CreateSnapshotResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "PUT";

			},

			// Request path
			request -> {
				final int _repository = 1 << 0;
				final int _snapshot = 1 << 1;

				int propsSet = 0;

				propsSet |= _repository;
				propsSet |= _snapshot;

				if (propsSet == (_repository | _snapshot)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_snapshot");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.repository, buf);
					buf.append("/");
					SimpleEndpoint.pathEncode(request.snapshot, buf);
					return buf.toString();
				}
				throw SimpleEndpoint.noPathTemplateFound("path");

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
				if (request.waitForCompletion != null) {
					params.put("wait_for_completion", String.valueOf(request.waitForCompletion));
				}
				return params;

			}, SimpleEndpoint.emptyMap(), true, CreateSnapshotResponse._DESERIALIZER);
}
