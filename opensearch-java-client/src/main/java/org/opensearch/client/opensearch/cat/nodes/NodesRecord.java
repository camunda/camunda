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

package org.opensearch.client.opensearch.cat.nodes;

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

// typedef: cat.nodes.NodesRecord


@JsonpDeserializable
public class NodesRecord implements JsonpSerializable {
	@Nullable
	private final String id;

	@Nullable
	private final String pid;

	@Nullable
	private final String ip;

	@Nullable
	private final String port;

	@Nullable
	private final String httpAddress;

	@Nullable
	private final String version;

	@Nullable
	private final String flavor;

	@Nullable
	private final String type;

	@Nullable
	private final String build;

	@Nullable
	private final String jdk;

	@Nullable
	private final String diskTotal;

	@Nullable
	private final String diskUsed;

	@Nullable
	private final String diskAvail;

	@Nullable
	private final String diskUsedPercent;

	@Nullable
	private final String heapCurrent;

	@Nullable
	private final String heapPercent;

	@Nullable
	private final String heapMax;

	@Nullable
	private final String ramCurrent;

	@Nullable
	private final String ramPercent;

	@Nullable
	private final String ramMax;

	@Nullable
	private final String fileDescCurrent;

	@Nullable
	private final String fileDescPercent;

	@Nullable
	private final String fileDescMax;

	@Nullable
	private final String cpu;

	@Nullable
	private final String load1m;

	@Nullable
	private final String load5m;

	@Nullable
	private final String load15m;

	@Nullable
	private final String uptime;

	@Nullable
	private final String nodeRole;

	@Deprecated
	@Nullable
	private final String master;

	@Nullable
	private final String clusterManager;

	@Nullable
	private final String name;

	@Nullable
	private final String completionSize;

	@Nullable
	private final String fielddataMemorySize;

	@Nullable
	private final String fielddataEvictions;

	@Nullable
	private final String queryCacheMemorySize;

	@Nullable
	private final String queryCacheEvictions;

	@Nullable
	private final String queryCacheHitCount;

	@Nullable
	private final String queryCacheMissCount;

	@Nullable
	private final String requestCacheMemorySize;

	@Nullable
	private final String requestCacheEvictions;

	@Nullable
	private final String requestCacheHitCount;

	@Nullable
	private final String requestCacheMissCount;

	@Nullable
	private final String flushTotal;

	@Nullable
	private final String flushTotalTime;

	@Nullable
	private final String getCurrent;

	@Nullable
	private final String getTime;

	@Nullable
	private final String getTotal;

	@Nullable
	private final String getExistsTime;

	@Nullable
	private final String getExistsTotal;

	@Nullable
	private final String getMissingTime;

	@Nullable
	private final String getMissingTotal;

	@Nullable
	private final String indexingDeleteCurrent;

	@Nullable
	private final String indexingDeleteTime;

	@Nullable
	private final String indexingDeleteTotal;

	@Nullable
	private final String indexingIndexCurrent;

	@Nullable
	private final String indexingIndexTime;

	@Nullable
	private final String indexingIndexTotal;

	@Nullable
	private final String indexingIndexFailed;

	@Nullable
	private final String mergesCurrent;

	@Nullable
	private final String mergesCurrentDocs;

	@Nullable
	private final String mergesCurrentSize;

	@Nullable
	private final String mergesTotal;

	@Nullable
	private final String mergesTotalDocs;

	@Nullable
	private final String mergesTotalSize;

	@Nullable
	private final String mergesTotalTime;

	@Nullable
	private final String refreshTotal;

	@Nullable
	private final String refreshTime;

	@Nullable
	private final String refreshExternalTotal;

	@Nullable
	private final String refreshExternalTime;

	@Nullable
	private final String refreshListeners;

	@Nullable
	private final String scriptCompilations;

	@Nullable
	private final String scriptCacheEvictions;

	@Nullable
	private final String scriptCompilationLimitTriggered;

	@Nullable
	private final String searchFetchCurrent;

	@Nullable
	private final String searchFetchTime;

	@Nullable
	private final String searchFetchTotal;

	@Nullable
	private final String searchOpenContexts;

	@Nullable
	private final String searchQueryCurrent;

	@Nullable
	private final String searchQueryTime;

	@Nullable
	private final String searchQueryTotal;

	@Nullable
	private final String searchScrollCurrent;

	@Nullable
	private final String searchScrollTime;

	@Nullable
	private final String searchScrollTotal;

	@Nullable
	private final String segmentsCount;

	@Nullable
	private final String segmentsMemory;

	@Nullable
	private final String segmentsIndexWriterMemory;

	@Nullable
	private final String segmentsVersionMapMemory;

	@Nullable
	private final String segmentsFixedBitsetMemory;

	@Nullable
	private final String suggestCurrent;

	@Nullable
	private final String suggestTime;

	@Nullable
	private final String suggestTotal;

	@Nullable
	private final String bulkTotalOperations;

	@Nullable
	private final String bulkTotalTime;

	@Nullable
	private final String bulkTotalSizeInBytes;

	@Nullable
	private final String bulkAvgTime;

	@Nullable
	private final String bulkAvgSizeInBytes;

	// ---------------------------------------------------------------------------------------------

	private NodesRecord(Builder builder) {

		this.id = builder.id;
		this.pid = builder.pid;
		this.ip = builder.ip;
		this.port = builder.port;
		this.httpAddress = builder.httpAddress;
		this.version = builder.version;
		this.flavor = builder.flavor;
		this.type = builder.type;
		this.build = builder.build;
		this.jdk = builder.jdk;
		this.diskTotal = builder.diskTotal;
		this.diskUsed = builder.diskUsed;
		this.diskAvail = builder.diskAvail;
		this.diskUsedPercent = builder.diskUsedPercent;
		this.heapCurrent = builder.heapCurrent;
		this.heapPercent = builder.heapPercent;
		this.heapMax = builder.heapMax;
		this.ramCurrent = builder.ramCurrent;
		this.ramPercent = builder.ramPercent;
		this.ramMax = builder.ramMax;
		this.fileDescCurrent = builder.fileDescCurrent;
		this.fileDescPercent = builder.fileDescPercent;
		this.fileDescMax = builder.fileDescMax;
		this.cpu = builder.cpu;
		this.load1m = builder.load1m;
		this.load5m = builder.load5m;
		this.load15m = builder.load15m;
		this.uptime = builder.uptime;
		this.nodeRole = builder.nodeRole;
		this.master = builder.master;
		this.clusterManager = builder.clusterManager;
		this.name = builder.name;
		this.completionSize = builder.completionSize;
		this.fielddataMemorySize = builder.fielddataMemorySize;
		this.fielddataEvictions = builder.fielddataEvictions;
		this.queryCacheMemorySize = builder.queryCacheMemorySize;
		this.queryCacheEvictions = builder.queryCacheEvictions;
		this.queryCacheHitCount = builder.queryCacheHitCount;
		this.queryCacheMissCount = builder.queryCacheMissCount;
		this.requestCacheMemorySize = builder.requestCacheMemorySize;
		this.requestCacheEvictions = builder.requestCacheEvictions;
		this.requestCacheHitCount = builder.requestCacheHitCount;
		this.requestCacheMissCount = builder.requestCacheMissCount;
		this.flushTotal = builder.flushTotal;
		this.flushTotalTime = builder.flushTotalTime;
		this.getCurrent = builder.getCurrent;
		this.getTime = builder.getTime;
		this.getTotal = builder.getTotal;
		this.getExistsTime = builder.getExistsTime;
		this.getExistsTotal = builder.getExistsTotal;
		this.getMissingTime = builder.getMissingTime;
		this.getMissingTotal = builder.getMissingTotal;
		this.indexingDeleteCurrent = builder.indexingDeleteCurrent;
		this.indexingDeleteTime = builder.indexingDeleteTime;
		this.indexingDeleteTotal = builder.indexingDeleteTotal;
		this.indexingIndexCurrent = builder.indexingIndexCurrent;
		this.indexingIndexTime = builder.indexingIndexTime;
		this.indexingIndexTotal = builder.indexingIndexTotal;
		this.indexingIndexFailed = builder.indexingIndexFailed;
		this.mergesCurrent = builder.mergesCurrent;
		this.mergesCurrentDocs = builder.mergesCurrentDocs;
		this.mergesCurrentSize = builder.mergesCurrentSize;
		this.mergesTotal = builder.mergesTotal;
		this.mergesTotalDocs = builder.mergesTotalDocs;
		this.mergesTotalSize = builder.mergesTotalSize;
		this.mergesTotalTime = builder.mergesTotalTime;
		this.refreshTotal = builder.refreshTotal;
		this.refreshTime = builder.refreshTime;
		this.refreshExternalTotal = builder.refreshExternalTotal;
		this.refreshExternalTime = builder.refreshExternalTime;
		this.refreshListeners = builder.refreshListeners;
		this.scriptCompilations = builder.scriptCompilations;
		this.scriptCacheEvictions = builder.scriptCacheEvictions;
		this.scriptCompilationLimitTriggered = builder.scriptCompilationLimitTriggered;
		this.searchFetchCurrent = builder.searchFetchCurrent;
		this.searchFetchTime = builder.searchFetchTime;
		this.searchFetchTotal = builder.searchFetchTotal;
		this.searchOpenContexts = builder.searchOpenContexts;
		this.searchQueryCurrent = builder.searchQueryCurrent;
		this.searchQueryTime = builder.searchQueryTime;
		this.searchQueryTotal = builder.searchQueryTotal;
		this.searchScrollCurrent = builder.searchScrollCurrent;
		this.searchScrollTime = builder.searchScrollTime;
		this.searchScrollTotal = builder.searchScrollTotal;
		this.segmentsCount = builder.segmentsCount;
		this.segmentsMemory = builder.segmentsMemory;
		this.segmentsIndexWriterMemory = builder.segmentsIndexWriterMemory;
		this.segmentsVersionMapMemory = builder.segmentsVersionMapMemory;
		this.segmentsFixedBitsetMemory = builder.segmentsFixedBitsetMemory;
		this.suggestCurrent = builder.suggestCurrent;
		this.suggestTime = builder.suggestTime;
		this.suggestTotal = builder.suggestTotal;
		this.bulkTotalOperations = builder.bulkTotalOperations;
		this.bulkTotalTime = builder.bulkTotalTime;
		this.bulkTotalSizeInBytes = builder.bulkTotalSizeInBytes;
		this.bulkAvgTime = builder.bulkAvgTime;
		this.bulkAvgSizeInBytes = builder.bulkAvgSizeInBytes;

	}

	public static NodesRecord of(Function<Builder, ObjectBuilder<NodesRecord>> fn) {
		return fn.apply(new Builder()).build();
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
	 * bound http address
	 * <p>
	 * API name: {@code http_address}
	 */
	@Nullable
	public final String httpAddress() {
		return this.httpAddress;
	}

	/**
	 * es version
	 * <p>
	 * API name: {@code version}
	 */
	@Nullable
	public final String version() {
		return this.version;
	}

	/**
	 * es distribution flavor
	 * <p>
	 * API name: {@code flavor}
	 */
	@Nullable
	public final String flavor() {
		return this.flavor;
	}

	/**
	 * es distribution type
	 * <p>
	 * API name: {@code type}
	 */
	@Nullable
	public final String type() {
		return this.type;
	}

	/**
	 * es build hash
	 * <p>
	 * API name: {@code build}
	 */
	@Nullable
	public final String build() {
		return this.build;
	}

	/**
	 * jdk version
	 * <p>
	 * API name: {@code jdk}
	 */
	@Nullable
	public final String jdk() {
		return this.jdk;
	}

	/**
	 * total disk space
	 * <p>
	 * API name: {@code disk.total}
	 */
	@Nullable
	public final String diskTotal() {
		return this.diskTotal;
	}

	/**
	 * used disk space
	 * <p>
	 * API name: {@code disk.used}
	 */
	@Nullable
	public final String diskUsed() {
		return this.diskUsed;
	}

	/**
	 * available disk space
	 * <p>
	 * API name: {@code disk.avail}
	 */
	@Nullable
	public final String diskAvail() {
		return this.diskAvail;
	}

	/**
	 * used disk space percentage
	 * <p>
	 * API name: {@code disk.used_percent}
	 */
	@Nullable
	public final String diskUsedPercent() {
		return this.diskUsedPercent;
	}

	/**
	 * used heap
	 * <p>
	 * API name: {@code heap.current}
	 */
	@Nullable
	public final String heapCurrent() {
		return this.heapCurrent;
	}

	/**
	 * used heap ratio
	 * <p>
	 * API name: {@code heap.percent}
	 */
	@Nullable
	public final String heapPercent() {
		return this.heapPercent;
	}

	/**
	 * max configured heap
	 * <p>
	 * API name: {@code heap.max}
	 */
	@Nullable
	public final String heapMax() {
		return this.heapMax;
	}

	/**
	 * used machine memory
	 * <p>
	 * API name: {@code ram.current}
	 */
	@Nullable
	public final String ramCurrent() {
		return this.ramCurrent;
	}

	/**
	 * used machine memory ratio
	 * <p>
	 * API name: {@code ram.percent}
	 */
	@Nullable
	public final String ramPercent() {
		return this.ramPercent;
	}

	/**
	 * total machine memory
	 * <p>
	 * API name: {@code ram.max}
	 */
	@Nullable
	public final String ramMax() {
		return this.ramMax;
	}

	/**
	 * used file descriptors
	 * <p>
	 * API name: {@code file_desc.current}
	 */
	@Nullable
	public final String fileDescCurrent() {
		return this.fileDescCurrent;
	}

	/**
	 * used file descriptor ratio
	 * <p>
	 * API name: {@code file_desc.percent}
	 */
	@Nullable
	public final String fileDescPercent() {
		return this.fileDescPercent;
	}

	/**
	 * max file descriptors
	 * <p>
	 * API name: {@code file_desc.max}
	 */
	@Nullable
	public final String fileDescMax() {
		return this.fileDescMax;
	}

	/**
	 * recent cpu usage
	 * <p>
	 * API name: {@code cpu}
	 */
	@Nullable
	public final String cpu() {
		return this.cpu;
	}

	/**
	 * 1m load avg
	 * <p>
	 * API name: {@code load_1m}
	 */
	@Nullable
	public final String load1m() {
		return this.load1m;
	}

	/**
	 * 5m load avg
	 * <p>
	 * API name: {@code load_5m}
	 */
	@Nullable
	public final String load5m() {
		return this.load5m;
	}

	/**
	 * 15m load avg
	 * <p>
	 * API name: {@code load_15m}
	 */
	@Nullable
	public final String load15m() {
		return this.load15m;
	}

	/**
	 * node uptime
	 * <p>
	 * API name: {@code uptime}
	 */
	@Nullable
	public final String uptime() {
		return this.uptime;
	}

	/**
	 * m:cluster-manager eligible node, d:data node, i:ingest node, -:coordinating node only
	 * <p>
	 * API name: {@code node.role}
	 */
	@Nullable
	public final String nodeRole() {
		return this.nodeRole;
	}

	/**
	 * *:current master
	 * <p>
	 * API name: {@code master}
	 */
	@Deprecated
	@Nullable
	public final String master() {
		return this.master;
	}

	/**
	 * *:current cluster-manager
	 * <p>
	 * API name: {@code clusterManager}
	 */
	@Nullable
	public final String clusterManager() {
		return this.clusterManager;
	}

	/**
	 * node name
	 * <p>
	 * API name: {@code name}
	 */
	@Nullable
	public final String name() {
		return this.name;
	}

	/**
	 * size of completion
	 * <p>
	 * API name: {@code completion.size}
	 */
	@Nullable
	public final String completionSize() {
		return this.completionSize;
	}

	/**
	 * used fielddata cache
	 * <p>
	 * API name: {@code fielddata.memory_size}
	 */
	@Nullable
	public final String fielddataMemorySize() {
		return this.fielddataMemorySize;
	}

	/**
	 * fielddata evictions
	 * <p>
	 * API name: {@code fielddata.evictions}
	 */
	@Nullable
	public final String fielddataEvictions() {
		return this.fielddataEvictions;
	}

	/**
	 * used query cache
	 * <p>
	 * API name: {@code query_cache.memory_size}
	 */
	@Nullable
	public final String queryCacheMemorySize() {
		return this.queryCacheMemorySize;
	}

	/**
	 * query cache evictions
	 * <p>
	 * API name: {@code query_cache.evictions}
	 */
	@Nullable
	public final String queryCacheEvictions() {
		return this.queryCacheEvictions;
	}

	/**
	 * query cache hit counts
	 * <p>
	 * API name: {@code query_cache.hit_count}
	 */
	@Nullable
	public final String queryCacheHitCount() {
		return this.queryCacheHitCount;
	}

	/**
	 * query cache miss counts
	 * <p>
	 * API name: {@code query_cache.miss_count}
	 */
	@Nullable
	public final String queryCacheMissCount() {
		return this.queryCacheMissCount;
	}

	/**
	 * used request cache
	 * <p>
	 * API name: {@code request_cache.memory_size}
	 */
	@Nullable
	public final String requestCacheMemorySize() {
		return this.requestCacheMemorySize;
	}

	/**
	 * request cache evictions
	 * <p>
	 * API name: {@code request_cache.evictions}
	 */
	@Nullable
	public final String requestCacheEvictions() {
		return this.requestCacheEvictions;
	}

	/**
	 * request cache hit counts
	 * <p>
	 * API name: {@code request_cache.hit_count}
	 */
	@Nullable
	public final String requestCacheHitCount() {
		return this.requestCacheHitCount;
	}

	/**
	 * request cache miss counts
	 * <p>
	 * API name: {@code request_cache.miss_count}
	 */
	@Nullable
	public final String requestCacheMissCount() {
		return this.requestCacheMissCount;
	}

	/**
	 * number of flushes
	 * <p>
	 * API name: {@code flush.total}
	 */
	@Nullable
	public final String flushTotal() {
		return this.flushTotal;
	}

	/**
	 * time spent in flush
	 * <p>
	 * API name: {@code flush.total_time}
	 */
	@Nullable
	public final String flushTotalTime() {
		return this.flushTotalTime;
	}

	/**
	 * number of current get ops
	 * <p>
	 * API name: {@code get.current}
	 */
	@Nullable
	public final String getCurrent() {
		return this.getCurrent;
	}

	/**
	 * time spent in get
	 * <p>
	 * API name: {@code get.time}
	 */
	@Nullable
	public final String getTime() {
		return this.getTime;
	}

	/**
	 * number of get ops
	 * <p>
	 * API name: {@code get.total}
	 */
	@Nullable
	public final String getTotal() {
		return this.getTotal;
	}

	/**
	 * time spent in successful gets
	 * <p>
	 * API name: {@code get.exists_time}
	 */
	@Nullable
	public final String getExistsTime() {
		return this.getExistsTime;
	}

	/**
	 * number of successful gets
	 * <p>
	 * API name: {@code get.exists_total}
	 */
	@Nullable
	public final String getExistsTotal() {
		return this.getExistsTotal;
	}

	/**
	 * time spent in failed gets
	 * <p>
	 * API name: {@code get.missing_time}
	 */
	@Nullable
	public final String getMissingTime() {
		return this.getMissingTime;
	}

	/**
	 * number of failed gets
	 * <p>
	 * API name: {@code get.missing_total}
	 */
	@Nullable
	public final String getMissingTotal() {
		return this.getMissingTotal;
	}

	/**
	 * number of current deletions
	 * <p>
	 * API name: {@code indexing.delete_current}
	 */
	@Nullable
	public final String indexingDeleteCurrent() {
		return this.indexingDeleteCurrent;
	}

	/**
	 * time spent in deletions
	 * <p>
	 * API name: {@code indexing.delete_time}
	 */
	@Nullable
	public final String indexingDeleteTime() {
		return this.indexingDeleteTime;
	}

	/**
	 * number of delete ops
	 * <p>
	 * API name: {@code indexing.delete_total}
	 */
	@Nullable
	public final String indexingDeleteTotal() {
		return this.indexingDeleteTotal;
	}

	/**
	 * number of current indexing ops
	 * <p>
	 * API name: {@code indexing.index_current}
	 */
	@Nullable
	public final String indexingIndexCurrent() {
		return this.indexingIndexCurrent;
	}

	/**
	 * time spent in indexing
	 * <p>
	 * API name: {@code indexing.index_time}
	 */
	@Nullable
	public final String indexingIndexTime() {
		return this.indexingIndexTime;
	}

	/**
	 * number of indexing ops
	 * <p>
	 * API name: {@code indexing.index_total}
	 */
	@Nullable
	public final String indexingIndexTotal() {
		return this.indexingIndexTotal;
	}

	/**
	 * number of failed indexing ops
	 * <p>
	 * API name: {@code indexing.index_failed}
	 */
	@Nullable
	public final String indexingIndexFailed() {
		return this.indexingIndexFailed;
	}

	/**
	 * number of current merges
	 * <p>
	 * API name: {@code merges.current}
	 */
	@Nullable
	public final String mergesCurrent() {
		return this.mergesCurrent;
	}

	/**
	 * number of current merging docs
	 * <p>
	 * API name: {@code merges.current_docs}
	 */
	@Nullable
	public final String mergesCurrentDocs() {
		return this.mergesCurrentDocs;
	}

	/**
	 * size of current merges
	 * <p>
	 * API name: {@code merges.current_size}
	 */
	@Nullable
	public final String mergesCurrentSize() {
		return this.mergesCurrentSize;
	}

	/**
	 * number of completed merge ops
	 * <p>
	 * API name: {@code merges.total}
	 */
	@Nullable
	public final String mergesTotal() {
		return this.mergesTotal;
	}

	/**
	 * docs merged
	 * <p>
	 * API name: {@code merges.total_docs}
	 */
	@Nullable
	public final String mergesTotalDocs() {
		return this.mergesTotalDocs;
	}

	/**
	 * size merged
	 * <p>
	 * API name: {@code merges.total_size}
	 */
	@Nullable
	public final String mergesTotalSize() {
		return this.mergesTotalSize;
	}

	/**
	 * time spent in merges
	 * <p>
	 * API name: {@code merges.total_time}
	 */
	@Nullable
	public final String mergesTotalTime() {
		return this.mergesTotalTime;
	}

	/**
	 * total refreshes
	 * <p>
	 * API name: {@code refresh.total}
	 */
	@Nullable
	public final String refreshTotal() {
		return this.refreshTotal;
	}

	/**
	 * time spent in refreshes
	 * <p>
	 * API name: {@code refresh.time}
	 */
	@Nullable
	public final String refreshTime() {
		return this.refreshTime;
	}

	/**
	 * total external refreshes
	 * <p>
	 * API name: {@code refresh.external_total}
	 */
	@Nullable
	public final String refreshExternalTotal() {
		return this.refreshExternalTotal;
	}

	/**
	 * time spent in external refreshes
	 * <p>
	 * API name: {@code refresh.external_time}
	 */
	@Nullable
	public final String refreshExternalTime() {
		return this.refreshExternalTime;
	}

	/**
	 * number of pending refresh listeners
	 * <p>
	 * API name: {@code refresh.listeners}
	 */
	@Nullable
	public final String refreshListeners() {
		return this.refreshListeners;
	}

	/**
	 * script compilations
	 * <p>
	 * API name: {@code script.compilations}
	 */
	@Nullable
	public final String scriptCompilations() {
		return this.scriptCompilations;
	}

	/**
	 * script cache evictions
	 * <p>
	 * API name: {@code script.cache_evictions}
	 */
	@Nullable
	public final String scriptCacheEvictions() {
		return this.scriptCacheEvictions;
	}

	/**
	 * script cache compilation limit triggered
	 * <p>
	 * API name: {@code script.compilation_limit_triggered}
	 */
	@Nullable
	public final String scriptCompilationLimitTriggered() {
		return this.scriptCompilationLimitTriggered;
	}

	/**
	 * current fetch phase ops
	 * <p>
	 * API name: {@code search.fetch_current}
	 */
	@Nullable
	public final String searchFetchCurrent() {
		return this.searchFetchCurrent;
	}

	/**
	 * time spent in fetch phase
	 * <p>
	 * API name: {@code search.fetch_time}
	 */
	@Nullable
	public final String searchFetchTime() {
		return this.searchFetchTime;
	}

	/**
	 * total fetch ops
	 * <p>
	 * API name: {@code search.fetch_total}
	 */
	@Nullable
	public final String searchFetchTotal() {
		return this.searchFetchTotal;
	}

	/**
	 * open search contexts
	 * <p>
	 * API name: {@code search.open_contexts}
	 */
	@Nullable
	public final String searchOpenContexts() {
		return this.searchOpenContexts;
	}

	/**
	 * current query phase ops
	 * <p>
	 * API name: {@code search.query_current}
	 */
	@Nullable
	public final String searchQueryCurrent() {
		return this.searchQueryCurrent;
	}

	/**
	 * time spent in query phase
	 * <p>
	 * API name: {@code search.query_time}
	 */
	@Nullable
	public final String searchQueryTime() {
		return this.searchQueryTime;
	}

	/**
	 * total query phase ops
	 * <p>
	 * API name: {@code search.query_total}
	 */
	@Nullable
	public final String searchQueryTotal() {
		return this.searchQueryTotal;
	}

	/**
	 * open scroll contexts
	 * <p>
	 * API name: {@code search.scroll_current}
	 */
	@Nullable
	public final String searchScrollCurrent() {
		return this.searchScrollCurrent;
	}

	/**
	 * time scroll contexts held open
	 * <p>
	 * API name: {@code search.scroll_time}
	 */
	@Nullable
	public final String searchScrollTime() {
		return this.searchScrollTime;
	}

	/**
	 * completed scroll contexts
	 * <p>
	 * API name: {@code search.scroll_total}
	 */
	@Nullable
	public final String searchScrollTotal() {
		return this.searchScrollTotal;
	}

	/**
	 * number of segments
	 * <p>
	 * API name: {@code segments.count}
	 */
	@Nullable
	public final String segmentsCount() {
		return this.segmentsCount;
	}

	/**
	 * memory used by segments
	 * <p>
	 * API name: {@code segments.memory}
	 */
	@Nullable
	public final String segmentsMemory() {
		return this.segmentsMemory;
	}

	/**
	 * memory used by index writer
	 * <p>
	 * API name: {@code segments.index_writer_memory}
	 */
	@Nullable
	public final String segmentsIndexWriterMemory() {
		return this.segmentsIndexWriterMemory;
	}

	/**
	 * memory used by version map
	 * <p>
	 * API name: {@code segments.version_map_memory}
	 */
	@Nullable
	public final String segmentsVersionMapMemory() {
		return this.segmentsVersionMapMemory;
	}

	/**
	 * memory used by fixed bit sets for nested object field types and export type
	 * filters for types referred in _parent fields
	 * <p>
	 * API name: {@code segments.fixed_bitset_memory}
	 */
	@Nullable
	public final String segmentsFixedBitsetMemory() {
		return this.segmentsFixedBitsetMemory;
	}

	/**
	 * number of current suggest ops
	 * <p>
	 * API name: {@code suggest.current}
	 */
	@Nullable
	public final String suggestCurrent() {
		return this.suggestCurrent;
	}

	/**
	 * time spend in suggest
	 * <p>
	 * API name: {@code suggest.time}
	 */
	@Nullable
	public final String suggestTime() {
		return this.suggestTime;
	}

	/**
	 * number of suggest ops
	 * <p>
	 * API name: {@code suggest.total}
	 */
	@Nullable
	public final String suggestTotal() {
		return this.suggestTotal;
	}

	/**
	 * number of bulk shard ops
	 * <p>
	 * API name: {@code bulk.total_operations}
	 */
	@Nullable
	public final String bulkTotalOperations() {
		return this.bulkTotalOperations;
	}

	/**
	 * time spend in shard bulk
	 * <p>
	 * API name: {@code bulk.total_time}
	 */
	@Nullable
	public final String bulkTotalTime() {
		return this.bulkTotalTime;
	}

	/**
	 * total size in bytes of shard bulk
	 * <p>
	 * API name: {@code bulk.total_size_in_bytes}
	 */
	@Nullable
	public final String bulkTotalSizeInBytes() {
		return this.bulkTotalSizeInBytes;
	}

	/**
	 * average time spend in shard bulk
	 * <p>
	 * API name: {@code bulk.avg_time}
	 */
	@Nullable
	public final String bulkAvgTime() {
		return this.bulkAvgTime;
	}

	/**
	 * average size in bytes of shard bulk
	 * <p>
	 * API name: {@code bulk.avg_size_in_bytes}
	 */
	@Nullable
	public final String bulkAvgSizeInBytes() {
		return this.bulkAvgSizeInBytes;
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

		if (this.id != null) {
			generator.writeKey("id");
			generator.write(this.id);

		}
		if (this.pid != null) {
			generator.writeKey("pid");
			generator.write(this.pid);

		}
		if (this.ip != null) {
			generator.writeKey("ip");
			generator.write(this.ip);

		}
		if (this.port != null) {
			generator.writeKey("port");
			generator.write(this.port);

		}
		if (this.httpAddress != null) {
			generator.writeKey("http_address");
			generator.write(this.httpAddress);

		}
		if (this.version != null) {
			generator.writeKey("version");
			generator.write(this.version);

		}
		if (this.flavor != null) {
			generator.writeKey("flavor");
			generator.write(this.flavor);

		}
		if (this.type != null) {
			generator.writeKey("type");
			generator.write(this.type);

		}
		if (this.build != null) {
			generator.writeKey("build");
			generator.write(this.build);

		}
		if (this.jdk != null) {
			generator.writeKey("jdk");
			generator.write(this.jdk);

		}
		if (this.diskTotal != null) {
			generator.writeKey("disk.total");
			generator.write(this.diskTotal);

		}
		if (this.diskUsed != null) {
			generator.writeKey("disk.used");
			generator.write(this.diskUsed);

		}
		if (this.diskAvail != null) {
			generator.writeKey("disk.avail");
			generator.write(this.diskAvail);

		}
		if (this.diskUsedPercent != null) {
			generator.writeKey("disk.used_percent");
			generator.write(this.diskUsedPercent);

		}
		if (this.heapCurrent != null) {
			generator.writeKey("heap.current");
			generator.write(this.heapCurrent);

		}
		if (this.heapPercent != null) {
			generator.writeKey("heap.percent");
			generator.write(this.heapPercent);

		}
		if (this.heapMax != null) {
			generator.writeKey("heap.max");
			generator.write(this.heapMax);

		}
		if (this.ramCurrent != null) {
			generator.writeKey("ram.current");
			generator.write(this.ramCurrent);

		}
		if (this.ramPercent != null) {
			generator.writeKey("ram.percent");
			generator.write(this.ramPercent);

		}
		if (this.ramMax != null) {
			generator.writeKey("ram.max");
			generator.write(this.ramMax);

		}
		if (this.fileDescCurrent != null) {
			generator.writeKey("file_desc.current");
			generator.write(this.fileDescCurrent);

		}
		if (this.fileDescPercent != null) {
			generator.writeKey("file_desc.percent");
			generator.write(this.fileDescPercent);

		}
		if (this.fileDescMax != null) {
			generator.writeKey("file_desc.max");
			generator.write(this.fileDescMax);

		}
		if (this.cpu != null) {
			generator.writeKey("cpu");
			generator.write(this.cpu);

		}
		if (this.load1m != null) {
			generator.writeKey("load_1m");
			generator.write(this.load1m);

		}
		if (this.load5m != null) {
			generator.writeKey("load_5m");
			generator.write(this.load5m);

		}
		if (this.load15m != null) {
			generator.writeKey("load_15m");
			generator.write(this.load15m);

		}
		if (this.uptime != null) {
			generator.writeKey("uptime");
			generator.write(this.uptime);

		}
		if (this.nodeRole != null) {
			generator.writeKey("node.role");
			generator.write(this.nodeRole);

		}
		if (this.master != null) {
			generator.writeKey("master");
			generator.write(this.master);

		}
		if (this.clusterManager != null) {
			generator.writeKey("cluster_manager");
			generator.write(this.clusterManager);

		}
		if (this.name != null) {
			generator.writeKey("name");
			generator.write(this.name);

		}
		if (this.completionSize != null) {
			generator.writeKey("completion.size");
			generator.write(this.completionSize);

		}
		if (this.fielddataMemorySize != null) {
			generator.writeKey("fielddata.memory_size");
			generator.write(this.fielddataMemorySize);

		}
		if (this.fielddataEvictions != null) {
			generator.writeKey("fielddata.evictions");
			generator.write(this.fielddataEvictions);

		}
		if (this.queryCacheMemorySize != null) {
			generator.writeKey("query_cache.memory_size");
			generator.write(this.queryCacheMemorySize);

		}
		if (this.queryCacheEvictions != null) {
			generator.writeKey("query_cache.evictions");
			generator.write(this.queryCacheEvictions);

		}
		if (this.queryCacheHitCount != null) {
			generator.writeKey("query_cache.hit_count");
			generator.write(this.queryCacheHitCount);

		}
		if (this.queryCacheMissCount != null) {
			generator.writeKey("query_cache.miss_count");
			generator.write(this.queryCacheMissCount);

		}
		if (this.requestCacheMemorySize != null) {
			generator.writeKey("request_cache.memory_size");
			generator.write(this.requestCacheMemorySize);

		}
		if (this.requestCacheEvictions != null) {
			generator.writeKey("request_cache.evictions");
			generator.write(this.requestCacheEvictions);

		}
		if (this.requestCacheHitCount != null) {
			generator.writeKey("request_cache.hit_count");
			generator.write(this.requestCacheHitCount);

		}
		if (this.requestCacheMissCount != null) {
			generator.writeKey("request_cache.miss_count");
			generator.write(this.requestCacheMissCount);

		}
		if (this.flushTotal != null) {
			generator.writeKey("flush.total");
			generator.write(this.flushTotal);

		}
		if (this.flushTotalTime != null) {
			generator.writeKey("flush.total_time");
			generator.write(this.flushTotalTime);

		}
		if (this.getCurrent != null) {
			generator.writeKey("get.current");
			generator.write(this.getCurrent);

		}
		if (this.getTime != null) {
			generator.writeKey("get.time");
			generator.write(this.getTime);

		}
		if (this.getTotal != null) {
			generator.writeKey("get.total");
			generator.write(this.getTotal);

		}
		if (this.getExistsTime != null) {
			generator.writeKey("get.exists_time");
			generator.write(this.getExistsTime);

		}
		if (this.getExistsTotal != null) {
			generator.writeKey("get.exists_total");
			generator.write(this.getExistsTotal);

		}
		if (this.getMissingTime != null) {
			generator.writeKey("get.missing_time");
			generator.write(this.getMissingTime);

		}
		if (this.getMissingTotal != null) {
			generator.writeKey("get.missing_total");
			generator.write(this.getMissingTotal);

		}
		if (this.indexingDeleteCurrent != null) {
			generator.writeKey("indexing.delete_current");
			generator.write(this.indexingDeleteCurrent);

		}
		if (this.indexingDeleteTime != null) {
			generator.writeKey("indexing.delete_time");
			generator.write(this.indexingDeleteTime);

		}
		if (this.indexingDeleteTotal != null) {
			generator.writeKey("indexing.delete_total");
			generator.write(this.indexingDeleteTotal);

		}
		if (this.indexingIndexCurrent != null) {
			generator.writeKey("indexing.index_current");
			generator.write(this.indexingIndexCurrent);

		}
		if (this.indexingIndexTime != null) {
			generator.writeKey("indexing.index_time");
			generator.write(this.indexingIndexTime);

		}
		if (this.indexingIndexTotal != null) {
			generator.writeKey("indexing.index_total");
			generator.write(this.indexingIndexTotal);

		}
		if (this.indexingIndexFailed != null) {
			generator.writeKey("indexing.index_failed");
			generator.write(this.indexingIndexFailed);

		}
		if (this.mergesCurrent != null) {
			generator.writeKey("merges.current");
			generator.write(this.mergesCurrent);

		}
		if (this.mergesCurrentDocs != null) {
			generator.writeKey("merges.current_docs");
			generator.write(this.mergesCurrentDocs);

		}
		if (this.mergesCurrentSize != null) {
			generator.writeKey("merges.current_size");
			generator.write(this.mergesCurrentSize);

		}
		if (this.mergesTotal != null) {
			generator.writeKey("merges.total");
			generator.write(this.mergesTotal);

		}
		if (this.mergesTotalDocs != null) {
			generator.writeKey("merges.total_docs");
			generator.write(this.mergesTotalDocs);

		}
		if (this.mergesTotalSize != null) {
			generator.writeKey("merges.total_size");
			generator.write(this.mergesTotalSize);

		}
		if (this.mergesTotalTime != null) {
			generator.writeKey("merges.total_time");
			generator.write(this.mergesTotalTime);

		}
		if (this.refreshTotal != null) {
			generator.writeKey("refresh.total");
			generator.write(this.refreshTotal);

		}
		if (this.refreshTime != null) {
			generator.writeKey("refresh.time");
			generator.write(this.refreshTime);

		}
		if (this.refreshExternalTotal != null) {
			generator.writeKey("refresh.external_total");
			generator.write(this.refreshExternalTotal);

		}
		if (this.refreshExternalTime != null) {
			generator.writeKey("refresh.external_time");
			generator.write(this.refreshExternalTime);

		}
		if (this.refreshListeners != null) {
			generator.writeKey("refresh.listeners");
			generator.write(this.refreshListeners);

		}
		if (this.scriptCompilations != null) {
			generator.writeKey("script.compilations");
			generator.write(this.scriptCompilations);

		}
		if (this.scriptCacheEvictions != null) {
			generator.writeKey("script.cache_evictions");
			generator.write(this.scriptCacheEvictions);

		}
		if (this.scriptCompilationLimitTriggered != null) {
			generator.writeKey("script.compilation_limit_triggered");
			generator.write(this.scriptCompilationLimitTriggered);

		}
		if (this.searchFetchCurrent != null) {
			generator.writeKey("search.fetch_current");
			generator.write(this.searchFetchCurrent);

		}
		if (this.searchFetchTime != null) {
			generator.writeKey("search.fetch_time");
			generator.write(this.searchFetchTime);

		}
		if (this.searchFetchTotal != null) {
			generator.writeKey("search.fetch_total");
			generator.write(this.searchFetchTotal);

		}
		if (this.searchOpenContexts != null) {
			generator.writeKey("search.open_contexts");
			generator.write(this.searchOpenContexts);

		}
		if (this.searchQueryCurrent != null) {
			generator.writeKey("search.query_current");
			generator.write(this.searchQueryCurrent);

		}
		if (this.searchQueryTime != null) {
			generator.writeKey("search.query_time");
			generator.write(this.searchQueryTime);

		}
		if (this.searchQueryTotal != null) {
			generator.writeKey("search.query_total");
			generator.write(this.searchQueryTotal);

		}
		if (this.searchScrollCurrent != null) {
			generator.writeKey("search.scroll_current");
			generator.write(this.searchScrollCurrent);

		}
		if (this.searchScrollTime != null) {
			generator.writeKey("search.scroll_time");
			generator.write(this.searchScrollTime);

		}
		if (this.searchScrollTotal != null) {
			generator.writeKey("search.scroll_total");
			generator.write(this.searchScrollTotal);

		}
		if (this.segmentsCount != null) {
			generator.writeKey("segments.count");
			generator.write(this.segmentsCount);

		}
		if (this.segmentsMemory != null) {
			generator.writeKey("segments.memory");
			generator.write(this.segmentsMemory);

		}
		if (this.segmentsIndexWriterMemory != null) {
			generator.writeKey("segments.index_writer_memory");
			generator.write(this.segmentsIndexWriterMemory);

		}
		if (this.segmentsVersionMapMemory != null) {
			generator.writeKey("segments.version_map_memory");
			generator.write(this.segmentsVersionMapMemory);

		}
		if (this.segmentsFixedBitsetMemory != null) {
			generator.writeKey("segments.fixed_bitset_memory");
			generator.write(this.segmentsFixedBitsetMemory);

		}
		if (this.suggestCurrent != null) {
			generator.writeKey("suggest.current");
			generator.write(this.suggestCurrent);

		}
		if (this.suggestTime != null) {
			generator.writeKey("suggest.time");
			generator.write(this.suggestTime);

		}
		if (this.suggestTotal != null) {
			generator.writeKey("suggest.total");
			generator.write(this.suggestTotal);

		}
		if (this.bulkTotalOperations != null) {
			generator.writeKey("bulk.total_operations");
			generator.write(this.bulkTotalOperations);

		}
		if (this.bulkTotalTime != null) {
			generator.writeKey("bulk.total_time");
			generator.write(this.bulkTotalTime);

		}
		if (this.bulkTotalSizeInBytes != null) {
			generator.writeKey("bulk.total_size_in_bytes");
			generator.write(this.bulkTotalSizeInBytes);

		}
		if (this.bulkAvgTime != null) {
			generator.writeKey("bulk.avg_time");
			generator.write(this.bulkAvgTime);

		}
		if (this.bulkAvgSizeInBytes != null) {
			generator.writeKey("bulk.avg_size_in_bytes");
			generator.write(this.bulkAvgSizeInBytes);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodesRecord}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodesRecord> {
		@Nullable
		private String id;

		@Nullable
		private String pid;

		@Nullable
		private String ip;

		@Nullable
		private String port;

		@Nullable
		private String httpAddress;

		@Nullable
		private String version;

		@Nullable
		private String flavor;

		@Nullable
		private String type;

		@Nullable
		private String build;

		@Nullable
		private String jdk;

		@Nullable
		private String diskTotal;

		@Nullable
		private String diskUsed;

		@Nullable
		private String diskAvail;

		@Nullable
		private String diskUsedPercent;

		@Nullable
		private String heapCurrent;

		@Nullable
		private String heapPercent;

		@Nullable
		private String heapMax;

		@Nullable
		private String ramCurrent;

		@Nullable
		private String ramPercent;

		@Nullable
		private String ramMax;

		@Nullable
		private String fileDescCurrent;

		@Nullable
		private String fileDescPercent;

		@Nullable
		private String fileDescMax;

		@Nullable
		private String cpu;

		@Nullable
		private String load1m;

		@Nullable
		private String load5m;

		@Nullable
		private String load15m;

		@Nullable
		private String uptime;

		@Nullable
		private String nodeRole;

		@Deprecated
		@Nullable
		private String master;

		@Nullable
		private String clusterManager;

		@Nullable
		private String name;

		@Nullable
		private String completionSize;

		@Nullable
		private String fielddataMemorySize;

		@Nullable
		private String fielddataEvictions;

		@Nullable
		private String queryCacheMemorySize;

		@Nullable
		private String queryCacheEvictions;

		@Nullable
		private String queryCacheHitCount;

		@Nullable
		private String queryCacheMissCount;

		@Nullable
		private String requestCacheMemorySize;

		@Nullable
		private String requestCacheEvictions;

		@Nullable
		private String requestCacheHitCount;

		@Nullable
		private String requestCacheMissCount;

		@Nullable
		private String flushTotal;

		@Nullable
		private String flushTotalTime;

		@Nullable
		private String getCurrent;

		@Nullable
		private String getTime;

		@Nullable
		private String getTotal;

		@Nullable
		private String getExistsTime;

		@Nullable
		private String getExistsTotal;

		@Nullable
		private String getMissingTime;

		@Nullable
		private String getMissingTotal;

		@Nullable
		private String indexingDeleteCurrent;

		@Nullable
		private String indexingDeleteTime;

		@Nullable
		private String indexingDeleteTotal;

		@Nullable
		private String indexingIndexCurrent;

		@Nullable
		private String indexingIndexTime;

		@Nullable
		private String indexingIndexTotal;

		@Nullable
		private String indexingIndexFailed;

		@Nullable
		private String mergesCurrent;

		@Nullable
		private String mergesCurrentDocs;

		@Nullable
		private String mergesCurrentSize;

		@Nullable
		private String mergesTotal;

		@Nullable
		private String mergesTotalDocs;

		@Nullable
		private String mergesTotalSize;

		@Nullable
		private String mergesTotalTime;

		@Nullable
		private String refreshTotal;

		@Nullable
		private String refreshTime;

		@Nullable
		private String refreshExternalTotal;

		@Nullable
		private String refreshExternalTime;

		@Nullable
		private String refreshListeners;

		@Nullable
		private String scriptCompilations;

		@Nullable
		private String scriptCacheEvictions;

		@Nullable
		private String scriptCompilationLimitTriggered;

		@Nullable
		private String searchFetchCurrent;

		@Nullable
		private String searchFetchTime;

		@Nullable
		private String searchFetchTotal;

		@Nullable
		private String searchOpenContexts;

		@Nullable
		private String searchQueryCurrent;

		@Nullable
		private String searchQueryTime;

		@Nullable
		private String searchQueryTotal;

		@Nullable
		private String searchScrollCurrent;

		@Nullable
		private String searchScrollTime;

		@Nullable
		private String searchScrollTotal;

		@Nullable
		private String segmentsCount;

		@Nullable
		private String segmentsMemory;

		@Nullable
		private String segmentsIndexWriterMemory;

		@Nullable
		private String segmentsVersionMapMemory;

		@Nullable
		private String segmentsFixedBitsetMemory;

		@Nullable
		private String suggestCurrent;

		@Nullable
		private String suggestTime;

		@Nullable
		private String suggestTotal;

		@Nullable
		private String bulkTotalOperations;

		@Nullable
		private String bulkTotalTime;

		@Nullable
		private String bulkTotalSizeInBytes;

		@Nullable
		private String bulkAvgTime;

		@Nullable
		private String bulkAvgSizeInBytes;

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
		 * bound http address
		 * <p>
		 * API name: {@code http_address}
		 */
		public final Builder httpAddress(@Nullable String value) {
			this.httpAddress = value;
			return this;
		}

		/**
		 * es version
		 * <p>
		 * API name: {@code version}
		 */
		public final Builder version(@Nullable String value) {
			this.version = value;
			return this;
		}

		/**
		 * es distribution flavor
		 * <p>
		 * API name: {@code flavor}
		 */
		public final Builder flavor(@Nullable String value) {
			this.flavor = value;
			return this;
		}

		/**
		 * es distribution type
		 * <p>
		 * API name: {@code type}
		 */
		public final Builder type(@Nullable String value) {
			this.type = value;
			return this;
		}

		/**
		 * es build hash
		 * <p>
		 * API name: {@code build}
		 */
		public final Builder build(@Nullable String value) {
			this.build = value;
			return this;
		}

		/**
		 * jdk version
		 * <p>
		 * API name: {@code jdk}
		 */
		public final Builder jdk(@Nullable String value) {
			this.jdk = value;
			return this;
		}

		/**
		 * total disk space
		 * <p>
		 * API name: {@code disk.total}
		 */
		public final Builder diskTotal(@Nullable String value) {
			this.diskTotal = value;
			return this;
		}

		/**
		 * used disk space
		 * <p>
		 * API name: {@code disk.used}
		 */
		public final Builder diskUsed(@Nullable String value) {
			this.diskUsed = value;
			return this;
		}

		/**
		 * available disk space
		 * <p>
		 * API name: {@code disk.avail}
		 */
		public final Builder diskAvail(@Nullable String value) {
			this.diskAvail = value;
			return this;
		}

		/**
		 * used disk space percentage
		 * <p>
		 * API name: {@code disk.used_percent}
		 */
		public final Builder diskUsedPercent(@Nullable String value) {
			this.diskUsedPercent = value;
			return this;
		}

		/**
		 * used heap
		 * <p>
		 * API name: {@code heap.current}
		 */
		public final Builder heapCurrent(@Nullable String value) {
			this.heapCurrent = value;
			return this;
		}

		/**
		 * used heap ratio
		 * <p>
		 * API name: {@code heap.percent}
		 */
		public final Builder heapPercent(@Nullable String value) {
			this.heapPercent = value;
			return this;
		}

		/**
		 * max configured heap
		 * <p>
		 * API name: {@code heap.max}
		 */
		public final Builder heapMax(@Nullable String value) {
			this.heapMax = value;
			return this;
		}

		/**
		 * used machine memory
		 * <p>
		 * API name: {@code ram.current}
		 */
		public final Builder ramCurrent(@Nullable String value) {
			this.ramCurrent = value;
			return this;
		}

		/**
		 * used machine memory ratio
		 * <p>
		 * API name: {@code ram.percent}
		 */
		public final Builder ramPercent(@Nullable String value) {
			this.ramPercent = value;
			return this;
		}

		/**
		 * total machine memory
		 * <p>
		 * API name: {@code ram.max}
		 */
		public final Builder ramMax(@Nullable String value) {
			this.ramMax = value;
			return this;
		}

		/**
		 * used file descriptors
		 * <p>
		 * API name: {@code file_desc.current}
		 */
		public final Builder fileDescCurrent(@Nullable String value) {
			this.fileDescCurrent = value;
			return this;
		}

		/**
		 * used file descriptor ratio
		 * <p>
		 * API name: {@code file_desc.percent}
		 */
		public final Builder fileDescPercent(@Nullable String value) {
			this.fileDescPercent = value;
			return this;
		}

		/**
		 * max file descriptors
		 * <p>
		 * API name: {@code file_desc.max}
		 */
		public final Builder fileDescMax(@Nullable String value) {
			this.fileDescMax = value;
			return this;
		}

		/**
		 * recent cpu usage
		 * <p>
		 * API name: {@code cpu}
		 */
		public final Builder cpu(@Nullable String value) {
			this.cpu = value;
			return this;
		}

		/**
		 * 1m load avg
		 * <p>
		 * API name: {@code load_1m}
		 */
		public final Builder load1m(@Nullable String value) {
			this.load1m = value;
			return this;
		}

		/**
		 * 5m load avg
		 * <p>
		 * API name: {@code load_5m}
		 */
		public final Builder load5m(@Nullable String value) {
			this.load5m = value;
			return this;
		}

		/**
		 * 15m load avg
		 * <p>
		 * API name: {@code load_15m}
		 */
		public final Builder load15m(@Nullable String value) {
			this.load15m = value;
			return this;
		}

		/**
		 * node uptime
		 * <p>
		 * API name: {@code uptime}
		 */
		public final Builder uptime(@Nullable String value) {
			this.uptime = value;
			return this;
		}

		/**
		 * m:cluster-manager eligible node, d:data node, i:ingest node, -:coordinating node only
		 * <p>
		 * API name: {@code node.role}
		 */
		public final Builder nodeRole(@Nullable String value) {
			this.nodeRole = value;
			return this;
		}

		/**
		 * *:current master
		 * <p>
		 * API name: {@code master}
		 */
		@Deprecated
		public final Builder master(@Nullable String value) {
			this.master = value;
			return this;
		}

		/**
		 * *:current cluster-manager
		 * <p>
		 * API name: {@code clusterManager}
		 */
		public final Builder clusterManager(@Nullable String value) {
			this.clusterManager = value;
			return this;
		}

		/**
		 * node name
		 * <p>
		 * API name: {@code name}
		 */
		public final Builder name(@Nullable String value) {
			this.name = value;
			return this;
		}

		/**
		 * size of completion
		 * <p>
		 * API name: {@code completion.size}
		 */
		public final Builder completionSize(@Nullable String value) {
			this.completionSize = value;
			return this;
		}

		/**
		 * used fielddata cache
		 * <p>
		 * API name: {@code fielddata.memory_size}
		 */
		public final Builder fielddataMemorySize(@Nullable String value) {
			this.fielddataMemorySize = value;
			return this;
		}

		/**
		 * fielddata evictions
		 * <p>
		 * API name: {@code fielddata.evictions}
		 */
		public final Builder fielddataEvictions(@Nullable String value) {
			this.fielddataEvictions = value;
			return this;
		}

		/**
		 * used query cache
		 * <p>
		 * API name: {@code query_cache.memory_size}
		 */
		public final Builder queryCacheMemorySize(@Nullable String value) {
			this.queryCacheMemorySize = value;
			return this;
		}

		/**
		 * query cache evictions
		 * <p>
		 * API name: {@code query_cache.evictions}
		 */
		public final Builder queryCacheEvictions(@Nullable String value) {
			this.queryCacheEvictions = value;
			return this;
		}

		/**
		 * query cache hit counts
		 * <p>
		 * API name: {@code query_cache.hit_count}
		 */
		public final Builder queryCacheHitCount(@Nullable String value) {
			this.queryCacheHitCount = value;
			return this;
		}

		/**
		 * query cache miss counts
		 * <p>
		 * API name: {@code query_cache.miss_count}
		 */
		public final Builder queryCacheMissCount(@Nullable String value) {
			this.queryCacheMissCount = value;
			return this;
		}

		/**
		 * used request cache
		 * <p>
		 * API name: {@code request_cache.memory_size}
		 */
		public final Builder requestCacheMemorySize(@Nullable String value) {
			this.requestCacheMemorySize = value;
			return this;
		}

		/**
		 * request cache evictions
		 * <p>
		 * API name: {@code request_cache.evictions}
		 */
		public final Builder requestCacheEvictions(@Nullable String value) {
			this.requestCacheEvictions = value;
			return this;
		}

		/**
		 * request cache hit counts
		 * <p>
		 * API name: {@code request_cache.hit_count}
		 */
		public final Builder requestCacheHitCount(@Nullable String value) {
			this.requestCacheHitCount = value;
			return this;
		}

		/**
		 * request cache miss counts
		 * <p>
		 * API name: {@code request_cache.miss_count}
		 */
		public final Builder requestCacheMissCount(@Nullable String value) {
			this.requestCacheMissCount = value;
			return this;
		}

		/**
		 * number of flushes
		 * <p>
		 * API name: {@code flush.total}
		 */
		public final Builder flushTotal(@Nullable String value) {
			this.flushTotal = value;
			return this;
		}

		/**
		 * time spent in flush
		 * <p>
		 * API name: {@code flush.total_time}
		 */
		public final Builder flushTotalTime(@Nullable String value) {
			this.flushTotalTime = value;
			return this;
		}

		/**
		 * number of current get ops
		 * <p>
		 * API name: {@code get.current}
		 */
		public final Builder getCurrent(@Nullable String value) {
			this.getCurrent = value;
			return this;
		}

		/**
		 * time spent in get
		 * <p>
		 * API name: {@code get.time}
		 */
		public final Builder getTime(@Nullable String value) {
			this.getTime = value;
			return this;
		}

		/**
		 * number of get ops
		 * <p>
		 * API name: {@code get.total}
		 */
		public final Builder getTotal(@Nullable String value) {
			this.getTotal = value;
			return this;
		}

		/**
		 * time spent in successful gets
		 * <p>
		 * API name: {@code get.exists_time}
		 */
		public final Builder getExistsTime(@Nullable String value) {
			this.getExistsTime = value;
			return this;
		}

		/**
		 * number of successful gets
		 * <p>
		 * API name: {@code get.exists_total}
		 */
		public final Builder getExistsTotal(@Nullable String value) {
			this.getExistsTotal = value;
			return this;
		}

		/**
		 * time spent in failed gets
		 * <p>
		 * API name: {@code get.missing_time}
		 */
		public final Builder getMissingTime(@Nullable String value) {
			this.getMissingTime = value;
			return this;
		}

		/**
		 * number of failed gets
		 * <p>
		 * API name: {@code get.missing_total}
		 */
		public final Builder getMissingTotal(@Nullable String value) {
			this.getMissingTotal = value;
			return this;
		}

		/**
		 * number of current deletions
		 * <p>
		 * API name: {@code indexing.delete_current}
		 */
		public final Builder indexingDeleteCurrent(@Nullable String value) {
			this.indexingDeleteCurrent = value;
			return this;
		}

		/**
		 * time spent in deletions
		 * <p>
		 * API name: {@code indexing.delete_time}
		 */
		public final Builder indexingDeleteTime(@Nullable String value) {
			this.indexingDeleteTime = value;
			return this;
		}

		/**
		 * number of delete ops
		 * <p>
		 * API name: {@code indexing.delete_total}
		 */
		public final Builder indexingDeleteTotal(@Nullable String value) {
			this.indexingDeleteTotal = value;
			return this;
		}

		/**
		 * number of current indexing ops
		 * <p>
		 * API name: {@code indexing.index_current}
		 */
		public final Builder indexingIndexCurrent(@Nullable String value) {
			this.indexingIndexCurrent = value;
			return this;
		}

		/**
		 * time spent in indexing
		 * <p>
		 * API name: {@code indexing.index_time}
		 */
		public final Builder indexingIndexTime(@Nullable String value) {
			this.indexingIndexTime = value;
			return this;
		}

		/**
		 * number of indexing ops
		 * <p>
		 * API name: {@code indexing.index_total}
		 */
		public final Builder indexingIndexTotal(@Nullable String value) {
			this.indexingIndexTotal = value;
			return this;
		}

		/**
		 * number of failed indexing ops
		 * <p>
		 * API name: {@code indexing.index_failed}
		 */
		public final Builder indexingIndexFailed(@Nullable String value) {
			this.indexingIndexFailed = value;
			return this;
		}

		/**
		 * number of current merges
		 * <p>
		 * API name: {@code merges.current}
		 */
		public final Builder mergesCurrent(@Nullable String value) {
			this.mergesCurrent = value;
			return this;
		}

		/**
		 * number of current merging docs
		 * <p>
		 * API name: {@code merges.current_docs}
		 */
		public final Builder mergesCurrentDocs(@Nullable String value) {
			this.mergesCurrentDocs = value;
			return this;
		}

		/**
		 * size of current merges
		 * <p>
		 * API name: {@code merges.current_size}
		 */
		public final Builder mergesCurrentSize(@Nullable String value) {
			this.mergesCurrentSize = value;
			return this;
		}

		/**
		 * number of completed merge ops
		 * <p>
		 * API name: {@code merges.total}
		 */
		public final Builder mergesTotal(@Nullable String value) {
			this.mergesTotal = value;
			return this;
		}

		/**
		 * docs merged
		 * <p>
		 * API name: {@code merges.total_docs}
		 */
		public final Builder mergesTotalDocs(@Nullable String value) {
			this.mergesTotalDocs = value;
			return this;
		}

		/**
		 * size merged
		 * <p>
		 * API name: {@code merges.total_size}
		 */
		public final Builder mergesTotalSize(@Nullable String value) {
			this.mergesTotalSize = value;
			return this;
		}

		/**
		 * time spent in merges
		 * <p>
		 * API name: {@code merges.total_time}
		 */
		public final Builder mergesTotalTime(@Nullable String value) {
			this.mergesTotalTime = value;
			return this;
		}

		/**
		 * total refreshes
		 * <p>
		 * API name: {@code refresh.total}
		 */
		public final Builder refreshTotal(@Nullable String value) {
			this.refreshTotal = value;
			return this;
		}

		/**
		 * time spent in refreshes
		 * <p>
		 * API name: {@code refresh.time}
		 */
		public final Builder refreshTime(@Nullable String value) {
			this.refreshTime = value;
			return this;
		}

		/**
		 * total external refreshes
		 * <p>
		 * API name: {@code refresh.external_total}
		 */
		public final Builder refreshExternalTotal(@Nullable String value) {
			this.refreshExternalTotal = value;
			return this;
		}

		/**
		 * time spent in external refreshes
		 * <p>
		 * API name: {@code refresh.external_time}
		 */
		public final Builder refreshExternalTime(@Nullable String value) {
			this.refreshExternalTime = value;
			return this;
		}

		/**
		 * number of pending refresh listeners
		 * <p>
		 * API name: {@code refresh.listeners}
		 */
		public final Builder refreshListeners(@Nullable String value) {
			this.refreshListeners = value;
			return this;
		}

		/**
		 * script compilations
		 * <p>
		 * API name: {@code script.compilations}
		 */
		public final Builder scriptCompilations(@Nullable String value) {
			this.scriptCompilations = value;
			return this;
		}

		/**
		 * script cache evictions
		 * <p>
		 * API name: {@code script.cache_evictions}
		 */
		public final Builder scriptCacheEvictions(@Nullable String value) {
			this.scriptCacheEvictions = value;
			return this;
		}

		/**
		 * script cache compilation limit triggered
		 * <p>
		 * API name: {@code script.compilation_limit_triggered}
		 */
		public final Builder scriptCompilationLimitTriggered(@Nullable String value) {
			this.scriptCompilationLimitTriggered = value;
			return this;
		}

		/**
		 * current fetch phase ops
		 * <p>
		 * API name: {@code search.fetch_current}
		 */
		public final Builder searchFetchCurrent(@Nullable String value) {
			this.searchFetchCurrent = value;
			return this;
		}

		/**
		 * time spent in fetch phase
		 * <p>
		 * API name: {@code search.fetch_time}
		 */
		public final Builder searchFetchTime(@Nullable String value) {
			this.searchFetchTime = value;
			return this;
		}

		/**
		 * total fetch ops
		 * <p>
		 * API name: {@code search.fetch_total}
		 */
		public final Builder searchFetchTotal(@Nullable String value) {
			this.searchFetchTotal = value;
			return this;
		}

		/**
		 * open search contexts
		 * <p>
		 * API name: {@code search.open_contexts}
		 */
		public final Builder searchOpenContexts(@Nullable String value) {
			this.searchOpenContexts = value;
			return this;
		}

		/**
		 * current query phase ops
		 * <p>
		 * API name: {@code search.query_current}
		 */
		public final Builder searchQueryCurrent(@Nullable String value) {
			this.searchQueryCurrent = value;
			return this;
		}

		/**
		 * time spent in query phase
		 * <p>
		 * API name: {@code search.query_time}
		 */
		public final Builder searchQueryTime(@Nullable String value) {
			this.searchQueryTime = value;
			return this;
		}

		/**
		 * total query phase ops
		 * <p>
		 * API name: {@code search.query_total}
		 */
		public final Builder searchQueryTotal(@Nullable String value) {
			this.searchQueryTotal = value;
			return this;
		}

		/**
		 * open scroll contexts
		 * <p>
		 * API name: {@code search.scroll_current}
		 */
		public final Builder searchScrollCurrent(@Nullable String value) {
			this.searchScrollCurrent = value;
			return this;
		}

		/**
		 * time scroll contexts held open
		 * <p>
		 * API name: {@code search.scroll_time}
		 */
		public final Builder searchScrollTime(@Nullable String value) {
			this.searchScrollTime = value;
			return this;
		}

		/**
		 * completed scroll contexts
		 * <p>
		 * API name: {@code search.scroll_total}
		 */
		public final Builder searchScrollTotal(@Nullable String value) {
			this.searchScrollTotal = value;
			return this;
		}

		/**
		 * number of segments
		 * <p>
		 * API name: {@code segments.count}
		 */
		public final Builder segmentsCount(@Nullable String value) {
			this.segmentsCount = value;
			return this;
		}

		/**
		 * memory used by segments
		 * <p>
		 * API name: {@code segments.memory}
		 */
		public final Builder segmentsMemory(@Nullable String value) {
			this.segmentsMemory = value;
			return this;
		}

		/**
		 * memory used by index writer
		 * <p>
		 * API name: {@code segments.index_writer_memory}
		 */
		public final Builder segmentsIndexWriterMemory(@Nullable String value) {
			this.segmentsIndexWriterMemory = value;
			return this;
		}

		/**
		 * memory used by version map
		 * <p>
		 * API name: {@code segments.version_map_memory}
		 */
		public final Builder segmentsVersionMapMemory(@Nullable String value) {
			this.segmentsVersionMapMemory = value;
			return this;
		}

		/**
		 * memory used by fixed bit sets for nested object field types and export type
		 * filters for types referred in _parent fields
		 * <p>
		 * API name: {@code segments.fixed_bitset_memory}
		 */
		public final Builder segmentsFixedBitsetMemory(@Nullable String value) {
			this.segmentsFixedBitsetMemory = value;
			return this;
		}

		/**
		 * number of current suggest ops
		 * <p>
		 * API name: {@code suggest.current}
		 */
		public final Builder suggestCurrent(@Nullable String value) {
			this.suggestCurrent = value;
			return this;
		}

		/**
		 * time spend in suggest
		 * <p>
		 * API name: {@code suggest.time}
		 */
		public final Builder suggestTime(@Nullable String value) {
			this.suggestTime = value;
			return this;
		}

		/**
		 * number of suggest ops
		 * <p>
		 * API name: {@code suggest.total}
		 */
		public final Builder suggestTotal(@Nullable String value) {
			this.suggestTotal = value;
			return this;
		}

		/**
		 * number of bulk shard ops
		 * <p>
		 * API name: {@code bulk.total_operations}
		 */
		public final Builder bulkTotalOperations(@Nullable String value) {
			this.bulkTotalOperations = value;
			return this;
		}

		/**
		 * time spend in shard bulk
		 * <p>
		 * API name: {@code bulk.total_time}
		 */
		public final Builder bulkTotalTime(@Nullable String value) {
			this.bulkTotalTime = value;
			return this;
		}

		/**
		 * total size in bytes of shard bulk
		 * <p>
		 * API name: {@code bulk.total_size_in_bytes}
		 */
		public final Builder bulkTotalSizeInBytes(@Nullable String value) {
			this.bulkTotalSizeInBytes = value;
			return this;
		}

		/**
		 * average time spend in shard bulk
		 * <p>
		 * API name: {@code bulk.avg_time}
		 */
		public final Builder bulkAvgTime(@Nullable String value) {
			this.bulkAvgTime = value;
			return this;
		}

		/**
		 * average size in bytes of shard bulk
		 * <p>
		 * API name: {@code bulk.avg_size_in_bytes}
		 */
		public final Builder bulkAvgSizeInBytes(@Nullable String value) {
			this.bulkAvgSizeInBytes = value;
			return this;
		}

		/**
		 * Builds a {@link NodesRecord}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodesRecord build() {
			_checkSingleUse();

			return new NodesRecord(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodesRecord}
	 */
	public static final JsonpDeserializer<NodesRecord> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodesRecord::setupNodesRecordDeserializer);

	protected static void setupNodesRecordDeserializer(ObjectDeserializer<NodesRecord.Builder> op) {

		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "id", "nodeId");
		op.add(Builder::pid, JsonpDeserializer.stringDeserializer(), "pid", "p");
		op.add(Builder::ip, JsonpDeserializer.stringDeserializer(), "ip", "i");
		op.add(Builder::port, JsonpDeserializer.stringDeserializer(), "port", "po");
		op.add(Builder::httpAddress, JsonpDeserializer.stringDeserializer(), "http_address", "http");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version", "v");
		op.add(Builder::flavor, JsonpDeserializer.stringDeserializer(), "flavor", "f");
		op.add(Builder::type, JsonpDeserializer.stringDeserializer(), "type", "t");
		op.add(Builder::build, JsonpDeserializer.stringDeserializer(), "build", "b");
		op.add(Builder::jdk, JsonpDeserializer.stringDeserializer(), "jdk", "j");
		op.add(Builder::diskTotal, JsonpDeserializer.stringDeserializer(), "disk.total", "dt", "diskTotal");
		op.add(Builder::diskUsed, JsonpDeserializer.stringDeserializer(), "disk.used", "du", "diskUsed");
		op.add(Builder::diskAvail, JsonpDeserializer.stringDeserializer(), "disk.avail", "d", "da", "disk",
				"diskAvail");
		op.add(Builder::diskUsedPercent, JsonpDeserializer.stringDeserializer(), "disk.used_percent", "dup",
				"diskUsedPercent");
		op.add(Builder::heapCurrent, JsonpDeserializer.stringDeserializer(), "heap.current", "hc", "heapCurrent");
		op.add(Builder::heapPercent, JsonpDeserializer.stringDeserializer(), "heap.percent", "hp", "heapPercent");
		op.add(Builder::heapMax, JsonpDeserializer.stringDeserializer(), "heap.max", "hm", "heapMax");
		op.add(Builder::ramCurrent, JsonpDeserializer.stringDeserializer(), "ram.current", "rc", "ramCurrent");
		op.add(Builder::ramPercent, JsonpDeserializer.stringDeserializer(), "ram.percent", "rp", "ramPercent");
		op.add(Builder::ramMax, JsonpDeserializer.stringDeserializer(), "ram.max", "rn", "ramMax");
		op.add(Builder::fileDescCurrent, JsonpDeserializer.stringDeserializer(), "file_desc.current", "fdc",
				"fileDescriptorCurrent");
		op.add(Builder::fileDescPercent, JsonpDeserializer.stringDeserializer(), "file_desc.percent", "fdp",
				"fileDescriptorPercent");
		op.add(Builder::fileDescMax, JsonpDeserializer.stringDeserializer(), "file_desc.max", "fdm",
				"fileDescriptorMax");
		op.add(Builder::cpu, JsonpDeserializer.stringDeserializer(), "cpu");
		op.add(Builder::load1m, JsonpDeserializer.stringDeserializer(), "load_1m");
		op.add(Builder::load5m, JsonpDeserializer.stringDeserializer(), "load_5m");
		op.add(Builder::load15m, JsonpDeserializer.stringDeserializer(), "load_15m", "l");
		op.add(Builder::uptime, JsonpDeserializer.stringDeserializer(), "uptime", "u");
		op.add(Builder::nodeRole, JsonpDeserializer.stringDeserializer(), "node.role", "r", "role", "nodeRole");
		op.add(Builder::master, JsonpDeserializer.stringDeserializer(), "master", "m");
		op.add(Builder::clusterManager, JsonpDeserializer.stringDeserializer(), "cluster_manager", "m");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name", "n");
		op.add(Builder::completionSize, JsonpDeserializer.stringDeserializer(), "completion.size", "cs",
				"completionSize");
		op.add(Builder::fielddataMemorySize, JsonpDeserializer.stringDeserializer(), "fielddata.memory_size", "fm",
				"fielddataMemory");
		op.add(Builder::fielddataEvictions, JsonpDeserializer.stringDeserializer(), "fielddata.evictions", "fe",
				"fielddataEvictions");
		op.add(Builder::queryCacheMemorySize, JsonpDeserializer.stringDeserializer(), "query_cache.memory_size", "qcm",
				"queryCacheMemory");
		op.add(Builder::queryCacheEvictions, JsonpDeserializer.stringDeserializer(), "query_cache.evictions", "qce",
				"queryCacheEvictions");
		op.add(Builder::queryCacheHitCount, JsonpDeserializer.stringDeserializer(), "query_cache.hit_count", "qchc",
				"queryCacheHitCount");
		op.add(Builder::queryCacheMissCount, JsonpDeserializer.stringDeserializer(), "query_cache.miss_count", "qcmc",
				"queryCacheMissCount");
		op.add(Builder::requestCacheMemorySize, JsonpDeserializer.stringDeserializer(), "request_cache.memory_size",
				"rcm", "requestCacheMemory");
		op.add(Builder::requestCacheEvictions, JsonpDeserializer.stringDeserializer(), "request_cache.evictions", "rce",
				"requestCacheEvictions");
		op.add(Builder::requestCacheHitCount, JsonpDeserializer.stringDeserializer(), "request_cache.hit_count", "rchc",
				"requestCacheHitCount");
		op.add(Builder::requestCacheMissCount, JsonpDeserializer.stringDeserializer(), "request_cache.miss_count",
				"rcmc", "requestCacheMissCount");
		op.add(Builder::flushTotal, JsonpDeserializer.stringDeserializer(), "flush.total", "ft", "flushTotal");
		op.add(Builder::flushTotalTime, JsonpDeserializer.stringDeserializer(), "flush.total_time", "ftt",
				"flushTotalTime");
		op.add(Builder::getCurrent, JsonpDeserializer.stringDeserializer(), "get.current", "gc", "getCurrent");
		op.add(Builder::getTime, JsonpDeserializer.stringDeserializer(), "get.time", "gti", "getTime");
		op.add(Builder::getTotal, JsonpDeserializer.stringDeserializer(), "get.total", "gto", "getTotal");
		op.add(Builder::getExistsTime, JsonpDeserializer.stringDeserializer(), "get.exists_time", "geti",
				"getExistsTime");
		op.add(Builder::getExistsTotal, JsonpDeserializer.stringDeserializer(), "get.exists_total", "geto",
				"getExistsTotal");
		op.add(Builder::getMissingTime, JsonpDeserializer.stringDeserializer(), "get.missing_time", "gmti",
				"getMissingTime");
		op.add(Builder::getMissingTotal, JsonpDeserializer.stringDeserializer(), "get.missing_total", "gmto",
				"getMissingTotal");
		op.add(Builder::indexingDeleteCurrent, JsonpDeserializer.stringDeserializer(), "indexing.delete_current", "idc",
				"indexingDeleteCurrent");
		op.add(Builder::indexingDeleteTime, JsonpDeserializer.stringDeserializer(), "indexing.delete_time", "idti",
				"indexingDeleteTime");
		op.add(Builder::indexingDeleteTotal, JsonpDeserializer.stringDeserializer(), "indexing.delete_total", "idto",
				"indexingDeleteTotal");
		op.add(Builder::indexingIndexCurrent, JsonpDeserializer.stringDeserializer(), "indexing.index_current", "iic",
				"indexingIndexCurrent");
		op.add(Builder::indexingIndexTime, JsonpDeserializer.stringDeserializer(), "indexing.index_time", "iiti",
				"indexingIndexTime");
		op.add(Builder::indexingIndexTotal, JsonpDeserializer.stringDeserializer(), "indexing.index_total", "iito",
				"indexingIndexTotal");
		op.add(Builder::indexingIndexFailed, JsonpDeserializer.stringDeserializer(), "indexing.index_failed", "iif",
				"indexingIndexFailed");
		op.add(Builder::mergesCurrent, JsonpDeserializer.stringDeserializer(), "merges.current", "mc", "mergesCurrent");
		op.add(Builder::mergesCurrentDocs, JsonpDeserializer.stringDeserializer(), "merges.current_docs", "mcd",
				"mergesCurrentDocs");
		op.add(Builder::mergesCurrentSize, JsonpDeserializer.stringDeserializer(), "merges.current_size", "mcs",
				"mergesCurrentSize");
		op.add(Builder::mergesTotal, JsonpDeserializer.stringDeserializer(), "merges.total", "mt", "mergesTotal");
		op.add(Builder::mergesTotalDocs, JsonpDeserializer.stringDeserializer(), "merges.total_docs", "mtd",
				"mergesTotalDocs");
		op.add(Builder::mergesTotalSize, JsonpDeserializer.stringDeserializer(), "merges.total_size", "mts",
				"mergesTotalSize");
		op.add(Builder::mergesTotalTime, JsonpDeserializer.stringDeserializer(), "merges.total_time", "mtt",
				"mergesTotalTime");
		op.add(Builder::refreshTotal, JsonpDeserializer.stringDeserializer(), "refresh.total");
		op.add(Builder::refreshTime, JsonpDeserializer.stringDeserializer(), "refresh.time");
		op.add(Builder::refreshExternalTotal, JsonpDeserializer.stringDeserializer(), "refresh.external_total", "rto",
				"refreshTotal");
		op.add(Builder::refreshExternalTime, JsonpDeserializer.stringDeserializer(), "refresh.external_time", "rti",
				"refreshTime");
		op.add(Builder::refreshListeners, JsonpDeserializer.stringDeserializer(), "refresh.listeners", "rli",
				"refreshListeners");
		op.add(Builder::scriptCompilations, JsonpDeserializer.stringDeserializer(), "script.compilations", "scrcc",
				"scriptCompilations");
		op.add(Builder::scriptCacheEvictions, JsonpDeserializer.stringDeserializer(), "script.cache_evictions", "scrce",
				"scriptCacheEvictions");
		op.add(Builder::scriptCompilationLimitTriggered, JsonpDeserializer.stringDeserializer(),
				"script.compilation_limit_triggered", "scrclt", "scriptCacheCompilationLimitTriggered");
		op.add(Builder::searchFetchCurrent, JsonpDeserializer.stringDeserializer(), "search.fetch_current", "sfc",
				"searchFetchCurrent");
		op.add(Builder::searchFetchTime, JsonpDeserializer.stringDeserializer(), "search.fetch_time", "sfti",
				"searchFetchTime");
		op.add(Builder::searchFetchTotal, JsonpDeserializer.stringDeserializer(), "search.fetch_total", "sfto",
				"searchFetchTotal");
		op.add(Builder::searchOpenContexts, JsonpDeserializer.stringDeserializer(), "search.open_contexts", "so",
				"searchOpenContexts");
		op.add(Builder::searchQueryCurrent, JsonpDeserializer.stringDeserializer(), "search.query_current", "sqc",
				"searchQueryCurrent");
		op.add(Builder::searchQueryTime, JsonpDeserializer.stringDeserializer(), "search.query_time", "sqti",
				"searchQueryTime");
		op.add(Builder::searchQueryTotal, JsonpDeserializer.stringDeserializer(), "search.query_total", "sqto",
				"searchQueryTotal");
		op.add(Builder::searchScrollCurrent, JsonpDeserializer.stringDeserializer(), "search.scroll_current", "scc",
				"searchScrollCurrent");
		op.add(Builder::searchScrollTime, JsonpDeserializer.stringDeserializer(), "search.scroll_time", "scti",
				"searchScrollTime");
		op.add(Builder::searchScrollTotal, JsonpDeserializer.stringDeserializer(), "search.scroll_total", "scto",
				"searchScrollTotal");
		op.add(Builder::segmentsCount, JsonpDeserializer.stringDeserializer(), "segments.count", "sc", "segmentsCount");
		op.add(Builder::segmentsMemory, JsonpDeserializer.stringDeserializer(), "segments.memory", "sm",
				"segmentsMemory");
		op.add(Builder::segmentsIndexWriterMemory, JsonpDeserializer.stringDeserializer(),
				"segments.index_writer_memory", "siwm", "segmentsIndexWriterMemory");
		op.add(Builder::segmentsVersionMapMemory, JsonpDeserializer.stringDeserializer(), "segments.version_map_memory",
				"svmm", "segmentsVersionMapMemory");
		op.add(Builder::segmentsFixedBitsetMemory, JsonpDeserializer.stringDeserializer(),
				"segments.fixed_bitset_memory", "sfbm", "fixedBitsetMemory");
		op.add(Builder::suggestCurrent, JsonpDeserializer.stringDeserializer(), "suggest.current", "suc",
				"suggestCurrent");
		op.add(Builder::suggestTime, JsonpDeserializer.stringDeserializer(), "suggest.time", "suti", "suggestTime");
		op.add(Builder::suggestTotal, JsonpDeserializer.stringDeserializer(), "suggest.total", "suto", "suggestTotal");
		op.add(Builder::bulkTotalOperations, JsonpDeserializer.stringDeserializer(), "bulk.total_operations", "bto",
				"bulkTotalOperations");
		op.add(Builder::bulkTotalTime, JsonpDeserializer.stringDeserializer(), "bulk.total_time", "btti",
				"bulkTotalTime");
		op.add(Builder::bulkTotalSizeInBytes, JsonpDeserializer.stringDeserializer(), "bulk.total_size_in_bytes",
				"btsi", "bulkTotalSizeInBytes");
		op.add(Builder::bulkAvgTime, JsonpDeserializer.stringDeserializer(), "bulk.avg_time", "bati", "bulkAvgTime");
		op.add(Builder::bulkAvgSizeInBytes, JsonpDeserializer.stringDeserializer(), "bulk.avg_size_in_bytes", "basi",
				"bulkAvgSizeInBytes");

	}

}
