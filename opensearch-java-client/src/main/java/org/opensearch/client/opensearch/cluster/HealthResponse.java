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

import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.opensearch.cluster.health.IndexHealthStats;
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

import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: cluster.health.Response

@JsonpDeserializable
public class HealthResponse implements JsonpSerializable {
	private final int activePrimaryShards;

	private final int activeShards;

	private final String activeShardsPercentAsNumber;

	private final String clusterName;

	private final int delayedUnassignedShards;

	private final Map<String, IndexHealthStats> indices;

	private final int initializingShards;

	private final int numberOfDataNodes;

	private final int numberOfInFlightFetch;

	private final int numberOfNodes;

	private final int numberOfPendingTasks;

	private final int relocatingShards;

	private final HealthStatus status;

	private final String taskMaxWaitingInQueueMillis;

	private final boolean timedOut;

	private final int unassignedShards;

	// ---------------------------------------------------------------------------------------------

	private HealthResponse(Builder builder) {

		this.activePrimaryShards = ApiTypeHelper.requireNonNull(builder.activePrimaryShards, this,
				"activePrimaryShards");
		this.activeShards = ApiTypeHelper.requireNonNull(builder.activeShards, this, "activeShards");
		this.activeShardsPercentAsNumber = ApiTypeHelper.requireNonNull(builder.activeShardsPercentAsNumber, this,
				"activeShardsPercentAsNumber");
		this.clusterName = ApiTypeHelper.requireNonNull(builder.clusterName, this, "clusterName");
		this.delayedUnassignedShards = ApiTypeHelper.requireNonNull(builder.delayedUnassignedShards, this,
				"delayedUnassignedShards");
		this.indices = ApiTypeHelper.unmodifiable(builder.indices);
		this.initializingShards = ApiTypeHelper.requireNonNull(builder.initializingShards, this, "initializingShards");
		this.numberOfDataNodes = ApiTypeHelper.requireNonNull(builder.numberOfDataNodes, this, "numberOfDataNodes");
		this.numberOfInFlightFetch = ApiTypeHelper.requireNonNull(builder.numberOfInFlightFetch, this,
				"numberOfInFlightFetch");
		this.numberOfNodes = ApiTypeHelper.requireNonNull(builder.numberOfNodes, this, "numberOfNodes");
		this.numberOfPendingTasks = ApiTypeHelper.requireNonNull(builder.numberOfPendingTasks, this,
				"numberOfPendingTasks");
		this.relocatingShards = ApiTypeHelper.requireNonNull(builder.relocatingShards, this, "relocatingShards");
		this.status = ApiTypeHelper.requireNonNull(builder.status, this, "status");
		this.taskMaxWaitingInQueueMillis = ApiTypeHelper.requireNonNull(builder.taskMaxWaitingInQueueMillis, this,
				"taskMaxWaitingInQueueMillis");
		this.timedOut = ApiTypeHelper.requireNonNull(builder.timedOut, this, "timedOut");
		this.unassignedShards = ApiTypeHelper.requireNonNull(builder.unassignedShards, this, "unassignedShards");

	}

	public static HealthResponse of(Function<Builder, ObjectBuilder<HealthResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - The number of active primary shards.
	 * <p>
	 * API name: {@code active_primary_shards}
	 */
	public final int activePrimaryShards() {
		return this.activePrimaryShards;
	}

	/**
	 * Required - The total number of active primary and replica shards.
	 * <p>
	 * API name: {@code active_shards}
	 */
	public final int activeShards() {
		return this.activeShards;
	}

	/**
	 * Required - The ratio of active shards in the cluster expressed as a
	 * percentage.
	 * <p>
	 * API name: {@code active_shards_percent_as_number}
	 */
	public final String activeShardsPercentAsNumber() {
		return this.activeShardsPercentAsNumber;
	}

	/**
	 * Required - The name of the cluster.
	 * <p>
	 * API name: {@code cluster_name}
	 */
	public final String clusterName() {
		return this.clusterName;
	}

	/**
	 * Required - The number of shards whose allocation has been delayed by the
	 * timeout settings.
	 * <p>
	 * API name: {@code delayed_unassigned_shards}
	 */
	public final int delayedUnassignedShards() {
		return this.delayedUnassignedShards;
	}

	/**
	 * API name: {@code indices}
	 */
	public final Map<String, IndexHealthStats> indices() {
		return this.indices;
	}

	/**
	 * Required - The number of shards that are under initialization.
	 * <p>
	 * API name: {@code initializing_shards}
	 */
	public final int initializingShards() {
		return this.initializingShards;
	}

	/**
	 * Required - The number of nodes that are dedicated data nodes.
	 * <p>
	 * API name: {@code number_of_data_nodes}
	 */
	public final int numberOfDataNodes() {
		return this.numberOfDataNodes;
	}

	/**
	 * Required - API name: {@code number_of_in_flight_fetch}
	 */
	public final int numberOfInFlightFetch() {
		return this.numberOfInFlightFetch;
	}

	/**
	 * Required - The number of nodes within the cluster.
	 * <p>
	 * API name: {@code number_of_nodes}
	 */
	public final int numberOfNodes() {
		return this.numberOfNodes;
	}

	/**
	 * Required - The number of cluster-level changes that have not yet been
	 * executed.
	 * <p>
	 * API name: {@code number_of_pending_tasks}
	 */
	public final int numberOfPendingTasks() {
		return this.numberOfPendingTasks;
	}

	/**
	 * Required - The number of shards that are under relocation.
	 * <p>
	 * API name: {@code relocating_shards}
	 */
	public final int relocatingShards() {
		return this.relocatingShards;
	}

	/**
	 * Required - API name: {@code status}
	 */
	public final HealthStatus status() {
		return this.status;
	}

	/**
	 * Required - The time expressed in milliseconds since the earliest initiated
	 * task is waiting for being performed.
	 * <p>
	 * API name: {@code task_max_waiting_in_queue_millis}
	 */
	public final String taskMaxWaitingInQueueMillis() {
		return this.taskMaxWaitingInQueueMillis;
	}

	/**
	 * Required - If false the response returned within the period of time that is
	 * specified by the timeout parameter (30s by default)
	 * <p>
	 * API name: {@code timed_out}
	 */
	public final boolean timedOut() {
		return this.timedOut;
	}

	/**
	 * Required - The number of shards that are not allocated.
	 * <p>
	 * API name: {@code unassigned_shards}
	 */
	public final int unassignedShards() {
		return this.unassignedShards;
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

		generator.writeKey("active_primary_shards");
		generator.write(this.activePrimaryShards);

		generator.writeKey("active_shards");
		generator.write(this.activeShards);

		generator.writeKey("active_shards_percent_as_number");
		generator.write(this.activeShardsPercentAsNumber);

		generator.writeKey("cluster_name");
		generator.write(this.clusterName);

		generator.writeKey("delayed_unassigned_shards");
		generator.write(this.delayedUnassignedShards);

		if (ApiTypeHelper.isDefined(this.indices)) {
			generator.writeKey("indices");
			generator.writeStartObject();
			for (Map.Entry<String, IndexHealthStats> item0 : this.indices.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("initializing_shards");
		generator.write(this.initializingShards);

		generator.writeKey("number_of_data_nodes");
		generator.write(this.numberOfDataNodes);

		generator.writeKey("number_of_in_flight_fetch");
		generator.write(this.numberOfInFlightFetch);

		generator.writeKey("number_of_nodes");
		generator.write(this.numberOfNodes);

		generator.writeKey("number_of_pending_tasks");
		generator.write(this.numberOfPendingTasks);

		generator.writeKey("relocating_shards");
		generator.write(this.relocatingShards);

		generator.writeKey("status");
		this.status.serialize(generator, mapper);
		generator.writeKey("task_max_waiting_in_queue_millis");
		generator.write(this.taskMaxWaitingInQueueMillis);

		generator.writeKey("timed_out");
		generator.write(this.timedOut);

		generator.writeKey("unassigned_shards");
		generator.write(this.unassignedShards);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HealthResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<HealthResponse> {
		private Integer activePrimaryShards;

		private Integer activeShards;

		private String activeShardsPercentAsNumber;

		private String clusterName;

		private Integer delayedUnassignedShards;

		@Nullable
		private Map<String, IndexHealthStats> indices;

		private Integer initializingShards;

		private Integer numberOfDataNodes;

		private Integer numberOfInFlightFetch;

		private Integer numberOfNodes;

		private Integer numberOfPendingTasks;

		private Integer relocatingShards;

		private HealthStatus status;

		private String taskMaxWaitingInQueueMillis;

		private Boolean timedOut;

		private Integer unassignedShards;

		/**
		 * Required - The number of active primary shards.
		 * <p>
		 * API name: {@code active_primary_shards}
		 */
		public final Builder activePrimaryShards(int value) {
			this.activePrimaryShards = value;
			return this;
		}

		/**
		 * Required - The total number of active primary and replica shards.
		 * <p>
		 * API name: {@code active_shards}
		 */
		public final Builder activeShards(int value) {
			this.activeShards = value;
			return this;
		}

		/**
		 * Required - The ratio of active shards in the cluster expressed as a
		 * percentage.
		 * <p>
		 * API name: {@code active_shards_percent_as_number}
		 */
		public final Builder activeShardsPercentAsNumber(String value) {
			this.activeShardsPercentAsNumber = value;
			return this;
		}

		/**
		 * Required - The name of the cluster.
		 * <p>
		 * API name: {@code cluster_name}
		 */
		public final Builder clusterName(String value) {
			this.clusterName = value;
			return this;
		}

		/**
		 * Required - The number of shards whose allocation has been delayed by the
		 * timeout settings.
		 * <p>
		 * API name: {@code delayed_unassigned_shards}
		 */
		public final Builder delayedUnassignedShards(int value) {
			this.delayedUnassignedShards = value;
			return this;
		}

		/**
		 * API name: {@code indices}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>indices</code>.
		 */
		public final Builder indices(Map<String, IndexHealthStats> map) {
			this.indices = _mapPutAll(this.indices, map);
			return this;
		}

		/**
		 * API name: {@code indices}
		 * <p>
		 * Adds an entry to <code>indices</code>.
		 */
		public final Builder indices(String key, IndexHealthStats value) {
			this.indices = _mapPut(this.indices, key, value);
			return this;
		}

		/**
		 * API name: {@code indices}
		 * <p>
		 * Adds an entry to <code>indices</code> using a builder lambda.
		 */
		public final Builder indices(String key,
				Function<IndexHealthStats.Builder, ObjectBuilder<IndexHealthStats>> fn) {
			return indices(key, fn.apply(new IndexHealthStats.Builder()).build());
		}

		/**
		 * Required - The number of shards that are under initialization.
		 * <p>
		 * API name: {@code initializing_shards}
		 */
		public final Builder initializingShards(int value) {
			this.initializingShards = value;
			return this;
		}

		/**
		 * Required - The number of nodes that are dedicated data nodes.
		 * <p>
		 * API name: {@code number_of_data_nodes}
		 */
		public final Builder numberOfDataNodes(int value) {
			this.numberOfDataNodes = value;
			return this;
		}

		/**
		 * Required - API name: {@code number_of_in_flight_fetch}
		 */
		public final Builder numberOfInFlightFetch(int value) {
			this.numberOfInFlightFetch = value;
			return this;
		}

		/**
		 * Required - The number of nodes within the cluster.
		 * <p>
		 * API name: {@code number_of_nodes}
		 */
		public final Builder numberOfNodes(int value) {
			this.numberOfNodes = value;
			return this;
		}

		/**
		 * Required - The number of cluster-level changes that have not yet been
		 * executed.
		 * <p>
		 * API name: {@code number_of_pending_tasks}
		 */
		public final Builder numberOfPendingTasks(int value) {
			this.numberOfPendingTasks = value;
			return this;
		}

		/**
		 * Required - The number of shards that are under relocation.
		 * <p>
		 * API name: {@code relocating_shards}
		 */
		public final Builder relocatingShards(int value) {
			this.relocatingShards = value;
			return this;
		}

		/**
		 * Required - API name: {@code status}
		 */
		public final Builder status(HealthStatus value) {
			this.status = value;
			return this;
		}

		/**
		 * Required - The time expressed in milliseconds since the earliest initiated
		 * task is waiting for being performed.
		 * <p>
		 * API name: {@code task_max_waiting_in_queue_millis}
		 */
		public final Builder taskMaxWaitingInQueueMillis(String value) {
			this.taskMaxWaitingInQueueMillis = value;
			return this;
		}

		/**
		 * Required - If false the response returned within the period of time that is
		 * specified by the timeout parameter (30s by default)
		 * <p>
		 * API name: {@code timed_out}
		 */
		public final Builder timedOut(boolean value) {
			this.timedOut = value;
			return this;
		}

		/**
		 * Required - The number of shards that are not allocated.
		 * <p>
		 * API name: {@code unassigned_shards}
		 */
		public final Builder unassignedShards(int value) {
			this.unassignedShards = value;
			return this;
		}

		/**
		 * Builds a {@link HealthResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HealthResponse build() {
			_checkSingleUse();

			return new HealthResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HealthResponse}
	 */
	public static final JsonpDeserializer<HealthResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			HealthResponse::setupHealthResponseDeserializer);

	protected static void setupHealthResponseDeserializer(ObjectDeserializer<HealthResponse.Builder> op) {

		op.add(Builder::activePrimaryShards, JsonpDeserializer.integerDeserializer(), "active_primary_shards");
		op.add(Builder::activeShards, JsonpDeserializer.integerDeserializer(), "active_shards");
		op.add(Builder::activeShardsPercentAsNumber, JsonpDeserializer.stringDeserializer(),
				"active_shards_percent_as_number");
		op.add(Builder::clusterName, JsonpDeserializer.stringDeserializer(), "cluster_name");
		op.add(Builder::delayedUnassignedShards, JsonpDeserializer.integerDeserializer(), "delayed_unassigned_shards");
		op.add(Builder::indices, JsonpDeserializer.stringMapDeserializer(IndexHealthStats._DESERIALIZER), "indices");
		op.add(Builder::initializingShards, JsonpDeserializer.integerDeserializer(), "initializing_shards");
		op.add(Builder::numberOfDataNodes, JsonpDeserializer.integerDeserializer(), "number_of_data_nodes");
		op.add(Builder::numberOfInFlightFetch, JsonpDeserializer.integerDeserializer(), "number_of_in_flight_fetch");
		op.add(Builder::numberOfNodes, JsonpDeserializer.integerDeserializer(), "number_of_nodes");
		op.add(Builder::numberOfPendingTasks, JsonpDeserializer.integerDeserializer(), "number_of_pending_tasks");
		op.add(Builder::relocatingShards, JsonpDeserializer.integerDeserializer(), "relocating_shards");
		op.add(Builder::status, HealthStatus._DESERIALIZER, "status");
		op.add(Builder::taskMaxWaitingInQueueMillis, JsonpDeserializer.stringDeserializer(),
				"task_max_waiting_in_queue_millis");
		op.add(Builder::timedOut, JsonpDeserializer.booleanDeserializer(), "timed_out");
		op.add(Builder::unassignedShards, JsonpDeserializer.integerDeserializer(), "unassigned_shards");

	}

}
