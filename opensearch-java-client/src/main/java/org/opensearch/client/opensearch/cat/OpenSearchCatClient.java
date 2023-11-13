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

package org.opensearch.client.opensearch.cat;

import org.opensearch.client.ApiClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.JsonEndpoint;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.util.ObjectBuilder;

import java.io.IOException;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * Client for the cat namespace.
 */
public class OpenSearchCatClient extends ApiClient<OpenSearchTransport, OpenSearchCatClient> {

	public OpenSearchCatClient(OpenSearchTransport transport) {
		super(transport, null);
	}

	public OpenSearchCatClient(OpenSearchTransport transport, @Nullable TransportOptions transportOptions) {
		super(transport, transportOptions);
	}

	@Override
	public OpenSearchCatClient withTransportOptions(@Nullable TransportOptions transportOptions) {
		return new OpenSearchCatClient(this.transport, transportOptions);
	}

	// ----- Endpoint: cat.aliases

	/**
	 * Shows information about currently configured aliases to indices including
	 * filter and routing infos.
	 * 
	 *
	 */

	public AliasesResponse aliases(AliasesRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<AliasesRequest, AliasesResponse, ErrorResponse> endpoint =
				(JsonEndpoint<AliasesRequest, AliasesResponse, ErrorResponse>) AliasesRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Shows information about currently configured aliases to indices including
	 * filter and routing infos.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link AliasesRequest}
	 *
	 */

	public final AliasesResponse aliases(Function<AliasesRequest.Builder, ObjectBuilder<AliasesRequest>> fn)
			throws IOException, OpenSearchException {
		return aliases(fn.apply(new AliasesRequest.Builder()).build());
	}

	/**
	 * Shows information about currently configured aliases to indices including
	 * filter and routing infos.
	 * 
	 *
	 */

	public AliasesResponse aliases() throws IOException, OpenSearchException {
		return this.transport.performRequest(new AliasesRequest.Builder().build(), AliasesRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.allocation

	/**
	 * Provides a snapshot of how many shards are allocated to each data node and
	 * how much disk space they are using.
	 * 
	 *
	 */

	public AllocationResponse allocation(AllocationRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<AllocationRequest, AllocationResponse, ErrorResponse> endpoint =
				(JsonEndpoint<AllocationRequest, AllocationResponse, ErrorResponse>) AllocationRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Provides a snapshot of how many shards are allocated to each data node and
	 * how much disk space they are using.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link AllocationRequest}
	 *
	 */

	public final AllocationResponse allocation(Function<AllocationRequest.Builder, ObjectBuilder<AllocationRequest>> fn)
			throws IOException, OpenSearchException {
		return allocation(fn.apply(new AllocationRequest.Builder()).build());
	}

	/**
	 * Provides a snapshot of how many shards are allocated to each data node and
	 * how much disk space they are using.
	 * 
	 *
	 */

	public AllocationResponse allocation() throws IOException, OpenSearchException {
		return this.transport.performRequest(new AllocationRequest.Builder().build(), AllocationRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.count

	/**
	 * Provides quick access to the document count of the entire cluster, or
	 * individual indices.
	 * 
	 *
	 */

	public CountResponse count(CountRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<CountRequest, CountResponse, ErrorResponse> endpoint =
				(JsonEndpoint<CountRequest, CountResponse, ErrorResponse>) CountRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Provides quick access to the document count of the entire cluster, or
	 * individual indices.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link CountRequest}
	 *
	 */

	public final CountResponse count(Function<CountRequest.Builder, ObjectBuilder<CountRequest>> fn)
			throws IOException, OpenSearchException {
		return count(fn.apply(new CountRequest.Builder()).build());
	}

	/**
	 * Provides quick access to the document count of the entire cluster, or
	 * individual indices.
	 * 
	 *
	 */

	public CountResponse count() throws IOException, OpenSearchException {
		return this.transport.performRequest(new CountRequest.Builder().build(), CountRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.fielddata

	/**
	 * Shows how much heap memory is currently being used by fielddata on every data
	 * node in the cluster.
	 * 
	 *
	 */

	public FielddataResponse fielddata(FielddataRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<FielddataRequest, FielddataResponse, ErrorResponse> endpoint =
				(JsonEndpoint<FielddataRequest, FielddataResponse, ErrorResponse>) FielddataRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Shows how much heap memory is currently being used by fielddata on every data
	 * node in the cluster.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link FielddataRequest}
	 *
	 */

	public final FielddataResponse fielddata(Function<FielddataRequest.Builder, ObjectBuilder<FielddataRequest>> fn)
			throws IOException, OpenSearchException {
		return fielddata(fn.apply(new FielddataRequest.Builder()).build());
	}

	/**
	 * Shows how much heap memory is currently being used by fielddata on every data
	 * node in the cluster.
	 * 
	 *
	 */

	public FielddataResponse fielddata() throws IOException, OpenSearchException {
		return this.transport.performRequest(new FielddataRequest.Builder().build(), FielddataRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.health

	/**
	 * Returns a concise representation of the cluster health.
	 * 
	 *
	 */

	public HealthResponse health(HealthRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<HealthRequest, HealthResponse, ErrorResponse> endpoint =
				(JsonEndpoint<HealthRequest, HealthResponse, ErrorResponse>) HealthRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns a concise representation of the cluster health.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link HealthRequest}
	 *
	 */

	public final HealthResponse health(Function<HealthRequest.Builder, ObjectBuilder<HealthRequest>> fn)
			throws IOException, OpenSearchException {
		return health(fn.apply(new HealthRequest.Builder()).build());
	}

	/**
	 * Returns a concise representation of the cluster health.
	 * 
	 *
	 */

	public HealthResponse health() throws IOException, OpenSearchException {
		return this.transport.performRequest(new HealthRequest.Builder().build(), HealthRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.help

	/**
	 * Returns help for the Cat APIs.
	 * 
	 *
	 */
	public HelpResponse help() throws IOException, OpenSearchException {
		return this.transport.performRequest(HelpRequest._INSTANCE, HelpRequest._ENDPOINT, this.transportOptions);
	}

	// ----- Endpoint: cat.indices

	/**
	 * Returns information about indices: number of primaries and replicas, document
	 * counts, disk size, ...
	 * 
	 *
	 */

	public IndicesResponse indices(IndicesRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<IndicesRequest, IndicesResponse, ErrorResponse> endpoint =
				(JsonEndpoint<IndicesRequest, IndicesResponse, ErrorResponse>) IndicesRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns information about indices: number of primaries and replicas, document
	 * counts, disk size, ...
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link IndicesRequest}
	 *
	 */

	public final IndicesResponse indices(Function<IndicesRequest.Builder, ObjectBuilder<IndicesRequest>> fn)
			throws IOException, OpenSearchException {
		return indices(fn.apply(new IndicesRequest.Builder()).build());
	}

	/**
	 * Returns information about indices: number of primaries and replicas, document
	 * counts, disk size, ...
	 * 
	 *
	 */

	public IndicesResponse indices() throws IOException, OpenSearchException {
		return this.transport.performRequest(new IndicesRequest.Builder().build(), IndicesRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.master

	/**
	 * Returns information about the master node.
	 * 
	 *
	 */
	@Deprecated
	public ClusterManagerResponse master() throws IOException, OpenSearchException {
		return this.transport.performRequest(ClusterManagerRequest._INSTANCE, ClusterManagerRequest._DEPRECATED_ENDPOINT, this.transportOptions);
	}

	// ----- Endpoint: cat.cluster_manager

	/**
	 * Returns information about the cluster-manager node.
	 * 
	 *
	 */
	public ClusterManagerResponse clusterManager() throws IOException, OpenSearchException {
		return this.transport.performRequest(ClusterManagerRequest._INSTANCE, ClusterManagerRequest._ENDPOINT, this.transportOptions);
	}

	// ----- Endpoint: cat.nodeattrs

	/**
	 * Returns information about custom node attributes.
	 * 
	 *
	 */
	public NodeattrsResponse nodeattrs() throws IOException, OpenSearchException {
		return this.transport.performRequest(NodeattrsRequest._INSTANCE, NodeattrsRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.nodes

	/**
	 * Returns basic statistics about performance of cluster nodes.
	 * 
	 *
	 */

	public NodesResponse nodes(NodesRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<NodesRequest, NodesResponse, ErrorResponse> endpoint =
				(JsonEndpoint<NodesRequest, NodesResponse, ErrorResponse>) NodesRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns basic statistics about performance of cluster nodes.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link NodesRequest}
	 *
	 */

	public final NodesResponse nodes(Function<NodesRequest.Builder, ObjectBuilder<NodesRequest>> fn)
			throws IOException, OpenSearchException {
		return nodes(fn.apply(new NodesRequest.Builder()).build());
	}

	/**
	 * Returns basic statistics about performance of cluster nodes.
	 * 
	 *
	 */

	public NodesResponse nodes() throws IOException, OpenSearchException {
		return this.transport.performRequest(new NodesRequest.Builder().build(), NodesRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.point_in_time_segments

	/**
	 * Provides low-level information about the disk utilization of a PIT by
	 * describing its Lucene segments.
	 * 
	 *
	 */
	public SegmentsResponse pitSegments(PitSegmentsRequest request)
			throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<PitSegmentsRequest, SegmentsResponse, ErrorResponse> endpoint = (JsonEndpoint<PitSegmentsRequest, SegmentsResponse, ErrorResponse>) PitSegmentsRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}
	
	/**
	 * Provides low-level information about the disk utilization of a PIT by
	 * describing its Lucene segments.
	 * 
	 * * @param fn
	 * a function that initializes a builder to create the
	 * {@link PitSegmentsRequest}
	 */

	public final SegmentsResponse pitSegments(Function<PitSegmentsRequest.Builder, ObjectBuilder<PitSegmentsRequest>> fn)
			throws IOException, OpenSearchException {
		return pitSegments(fn.apply(new PitSegmentsRequest.Builder()).build());
	}

	/**
	 * Provides low-level information about the disk utilization of a PIT by
	 * describing its Lucene segments.
	 * 
	 */
	public SegmentsResponse pitSegments() throws IOException, OpenSearchException {
		return this.transport.performRequest(new PitSegmentsRequest.Builder().build(), 
				PitSegmentsRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.pending_tasks

	/**
	 * Returns a concise representation of the cluster pending tasks.
	 * 
	 *
	 */
	public PendingTasksResponse pendingTasks() throws IOException, OpenSearchException {
		return this.transport.performRequest(PendingTasksRequest._INSTANCE, PendingTasksRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.plugins

	/**
	 * Returns information about installed plugins across nodes node.
	 * 
	 *
	 */
	public PluginsResponse plugins() throws IOException, OpenSearchException {
		return this.transport.performRequest(PluginsRequest._INSTANCE, PluginsRequest._ENDPOINT, this.transportOptions);
	}

	// ----- Endpoint: cat.recovery

	/**
	 * Returns information about index shard recoveries, both on-going completed.
	 * 
	 *
	 */

	public RecoveryResponse recovery(RecoveryRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<RecoveryRequest, RecoveryResponse, ErrorResponse> endpoint =
				(JsonEndpoint<RecoveryRequest, RecoveryResponse, ErrorResponse>) RecoveryRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns information about index shard recoveries, both on-going completed.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link RecoveryRequest}
	 *
	 */

	public final RecoveryResponse recovery(Function<RecoveryRequest.Builder, ObjectBuilder<RecoveryRequest>> fn)
			throws IOException, OpenSearchException {
		return recovery(fn.apply(new RecoveryRequest.Builder()).build());
	}

	/**
	 * Returns information about index shard recoveries, both on-going completed.
	 * 
	 *
	 */

	public RecoveryResponse recovery() throws IOException, OpenSearchException {
		return this.transport.performRequest(new RecoveryRequest.Builder().build(), RecoveryRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.repositories

	/**
	 * Returns information about snapshot repositories registered in the cluster.
	 * 
	 *
	 */
	public RepositoriesResponse repositories() throws IOException, OpenSearchException {
		return this.transport.performRequest(RepositoriesRequest._INSTANCE, RepositoriesRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.segments

	/**
	 * Provides low-level information about the segments in the shards of an index.
	 * 
	 *
	 */

	public SegmentsResponse segments(SegmentsRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<SegmentsRequest, SegmentsResponse, ErrorResponse> endpoint =
				(JsonEndpoint<SegmentsRequest, SegmentsResponse, ErrorResponse>) SegmentsRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Provides low-level information about the segments in the shards of an index.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link SegmentsRequest}
	 *
	 */

	public final SegmentsResponse segments(Function<SegmentsRequest.Builder, ObjectBuilder<SegmentsRequest>> fn)
			throws IOException, OpenSearchException {
		return segments(fn.apply(new SegmentsRequest.Builder()).build());
	}

	/**
	 * Provides low-level information about the segments in the shards of an index.
	 * 
	 *
	 */

	public SegmentsResponse segments() throws IOException, OpenSearchException {
		return this.transport.performRequest(new SegmentsRequest.Builder().build(), SegmentsRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.shards

	/**
	 * Provides a detailed view of shard allocation on nodes.
	 * 
	 *
	 */

	public ShardsResponse shards(ShardsRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<ShardsRequest, ShardsResponse, ErrorResponse> endpoint =
				(JsonEndpoint<ShardsRequest, ShardsResponse, ErrorResponse>) ShardsRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Provides a detailed view of shard allocation on nodes.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link ShardsRequest}
	 *
	 */

	public final ShardsResponse shards(Function<ShardsRequest.Builder, ObjectBuilder<ShardsRequest>> fn)
			throws IOException, OpenSearchException {
		return shards(fn.apply(new ShardsRequest.Builder()).build());
	}

	/**
	 * Provides a detailed view of shard allocation on nodes.
	 * 
	 *
	 */

	public ShardsResponse shards() throws IOException, OpenSearchException {
		return this.transport.performRequest(new ShardsRequest.Builder().build(), ShardsRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.snapshots

	/**
	 * Returns all snapshots in a specific repository.
	 * 
	 *
	 */

	public SnapshotsResponse snapshots(SnapshotsRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<SnapshotsRequest, SnapshotsResponse, ErrorResponse> endpoint =
				(JsonEndpoint<SnapshotsRequest, SnapshotsResponse, ErrorResponse>) SnapshotsRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns all snapshots in a specific repository.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link SnapshotsRequest}
	 *
	 */

	public final SnapshotsResponse snapshots(Function<SnapshotsRequest.Builder, ObjectBuilder<SnapshotsRequest>> fn)
			throws IOException, OpenSearchException {
		return snapshots(fn.apply(new SnapshotsRequest.Builder()).build());
	}

	/**
	 * Returns all snapshots in a specific repository.
	 * 
	 *
	 */

	public SnapshotsResponse snapshots() throws IOException, OpenSearchException {
		return this.transport.performRequest(new SnapshotsRequest.Builder().build(), SnapshotsRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.tasks

	/**
	 * Returns information about the tasks currently executing on one or more nodes
	 * in the cluster.
	 * 
	 *
	 */

	public TasksResponse tasks(TasksRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<TasksRequest, TasksResponse, ErrorResponse> endpoint =
				(JsonEndpoint<TasksRequest, TasksResponse, ErrorResponse>) TasksRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns information about the tasks currently executing on one or more nodes
	 * in the cluster.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link TasksRequest}
	 *
	 */

	public final TasksResponse tasks(Function<TasksRequest.Builder, ObjectBuilder<TasksRequest>> fn)
			throws IOException, OpenSearchException {
		return tasks(fn.apply(new TasksRequest.Builder()).build());
	}

	/**
	 * Returns information about the tasks currently executing on one or more nodes
	 * in the cluster.
	 * 
	 *
	 */

	public TasksResponse tasks() throws IOException, OpenSearchException {
		return this.transport.performRequest(new TasksRequest.Builder().build(), TasksRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.templates

	/**
	 * Returns information about existing templates.
	 * 
	 *
	 */

	public TemplatesResponse templates(TemplatesRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<TemplatesRequest, TemplatesResponse, ErrorResponse> endpoint =
				(JsonEndpoint<TemplatesRequest, TemplatesResponse, ErrorResponse>) TemplatesRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns information about existing templates.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link TemplatesRequest}
	 *
	 */

	public final TemplatesResponse templates(Function<TemplatesRequest.Builder, ObjectBuilder<TemplatesRequest>> fn)
			throws IOException, OpenSearchException {
		return templates(fn.apply(new TemplatesRequest.Builder()).build());
	}

	/**
	 * Returns information about existing templates.
	 * 
	 *
	 */

	public TemplatesResponse templates() throws IOException, OpenSearchException {
		return this.transport.performRequest(new TemplatesRequest.Builder().build(), TemplatesRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: cat.thread_pool

	/**
	 * Returns cluster-wide thread pool statistics per node. By default the active,
	 * queue and rejected statistics are returned for all thread pools.
	 * 
	 *
	 */

	public ThreadPoolResponse threadPool(ThreadPoolRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<ThreadPoolRequest, ThreadPoolResponse, ErrorResponse> endpoint =
				(JsonEndpoint<ThreadPoolRequest, ThreadPoolResponse, ErrorResponse>) ThreadPoolRequest._ENDPOINT;

		return this.transport.performRequest(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns cluster-wide thread pool statistics per node. By default the active,
	 * queue and rejected statistics are returned for all thread pools.
	 * 
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link ThreadPoolRequest}
	 *
	 */

	public final ThreadPoolResponse threadPool(Function<ThreadPoolRequest.Builder, ObjectBuilder<ThreadPoolRequest>> fn)
			throws IOException, OpenSearchException {
		return threadPool(fn.apply(new ThreadPoolRequest.Builder()).build());
	}

	/**
	 * Returns cluster-wide thread pool statistics per node. By default the active,
	 * queue and rejected statistics are returned for all thread pools.
	 * 
	 *
	 */

	public ThreadPoolResponse threadPool() throws IOException, OpenSearchException {
		return this.transport.performRequest(new ThreadPoolRequest.Builder().build(), ThreadPoolRequest._ENDPOINT,
				this.transportOptions);
	}

}
