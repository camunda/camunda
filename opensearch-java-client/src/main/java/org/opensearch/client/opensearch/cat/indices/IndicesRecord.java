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

package org.opensearch.client.opensearch.cat.indices;

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

// typedef: cat.indices.IndicesRecord


@JsonpDeserializable
public class IndicesRecord implements JsonpSerializable {
	@Nullable
	private final String health;

	@Nullable
	private final String status;

	@Nullable
	private final String index;

	@Nullable
	private final String uuid;

	@Nullable
	private final String pri;

	@Nullable
	private final String rep;

	@Nullable
	private final String docsCount;

	@Nullable
	private final String docsDeleted;

	@Nullable
	private final String creationDate;

	@Nullable
	private final String creationDateString;

	@Nullable
	private final String storeSize;

	@Nullable
	private final String priStoreSize;

	@Nullable
	private final String completionSize;

	@Nullable
	private final String priCompletionSize;

	@Nullable
	private final String fielddataMemorySize;

	@Nullable
	private final String priFielddataMemorySize;

	@Nullable
	private final String fielddataEvictions;

	@Nullable
	private final String priFielddataEvictions;

	@Nullable
	private final String queryCacheMemorySize;

	@Nullable
	private final String priQueryCacheMemorySize;

	@Nullable
	private final String queryCacheEvictions;

	@Nullable
	private final String priQueryCacheEvictions;

	@Nullable
	private final String requestCacheMemorySize;

	@Nullable
	private final String priRequestCacheMemorySize;

	@Nullable
	private final String requestCacheEvictions;

	@Nullable
	private final String priRequestCacheEvictions;

	@Nullable
	private final String requestCacheHitCount;

	@Nullable
	private final String priRequestCacheHitCount;

	@Nullable
	private final String requestCacheMissCount;

	@Nullable
	private final String priRequestCacheMissCount;

	@Nullable
	private final String flushTotal;

	@Nullable
	private final String priFlushTotal;

	@Nullable
	private final String flushTotalTime;

	@Nullable
	private final String priFlushTotalTime;

	@Nullable
	private final String getCurrent;

	@Nullable
	private final String priGetCurrent;

	@Nullable
	private final String getTime;

	@Nullable
	private final String priGetTime;

	@Nullable
	private final String getTotal;

	@Nullable
	private final String priGetTotal;

	@Nullable
	private final String getExistsTime;

	@Nullable
	private final String priGetExistsTime;

	@Nullable
	private final String getExistsTotal;

	@Nullable
	private final String priGetExistsTotal;

	@Nullable
	private final String getMissingTime;

	@Nullable
	private final String priGetMissingTime;

	@Nullable
	private final String getMissingTotal;

	@Nullable
	private final String priGetMissingTotal;

	@Nullable
	private final String indexingDeleteCurrent;

	@Nullable
	private final String priIndexingDeleteCurrent;

	@Nullable
	private final String indexingDeleteTime;

	@Nullable
	private final String priIndexingDeleteTime;

	@Nullable
	private final String indexingDeleteTotal;

	@Nullable
	private final String priIndexingDeleteTotal;

	@Nullable
	private final String indexingIndexCurrent;

	@Nullable
	private final String priIndexingIndexCurrent;

	@Nullable
	private final String indexingIndexTime;

	@Nullable
	private final String priIndexingIndexTime;

	@Nullable
	private final String indexingIndexTotal;

	@Nullable
	private final String priIndexingIndexTotal;

	@Nullable
	private final String indexingIndexFailed;

	@Nullable
	private final String priIndexingIndexFailed;

	@Nullable
	private final String mergesCurrent;

	@Nullable
	private final String priMergesCurrent;

	@Nullable
	private final String mergesCurrentDocs;

	@Nullable
	private final String priMergesCurrentDocs;

	@Nullable
	private final String mergesCurrentSize;

	@Nullable
	private final String priMergesCurrentSize;

	@Nullable
	private final String mergesTotal;

	@Nullable
	private final String priMergesTotal;

	@Nullable
	private final String mergesTotalDocs;

	@Nullable
	private final String priMergesTotalDocs;

	@Nullable
	private final String mergesTotalSize;

	@Nullable
	private final String priMergesTotalSize;

	@Nullable
	private final String mergesTotalTime;

	@Nullable
	private final String priMergesTotalTime;

	@Nullable
	private final String refreshTotal;

	@Nullable
	private final String priRefreshTotal;

	@Nullable
	private final String refreshTime;

	@Nullable
	private final String priRefreshTime;

	@Nullable
	private final String refreshExternalTotal;

	@Nullable
	private final String priRefreshExternalTotal;

	@Nullable
	private final String refreshExternalTime;

	@Nullable
	private final String priRefreshExternalTime;

	@Nullable
	private final String refreshListeners;

	@Nullable
	private final String priRefreshListeners;

	@Nullable
	private final String searchFetchCurrent;

	@Nullable
	private final String priSearchFetchCurrent;

	@Nullable
	private final String searchFetchTime;

	@Nullable
	private final String priSearchFetchTime;

	@Nullable
	private final String searchFetchTotal;

	@Nullable
	private final String priSearchFetchTotal;

	@Nullable
	private final String searchOpenContexts;

	@Nullable
	private final String priSearchOpenContexts;

	@Nullable
	private final String searchQueryCurrent;

	@Nullable
	private final String priSearchQueryCurrent;

	@Nullable
	private final String searchQueryTime;

	@Nullable
	private final String priSearchQueryTime;

	@Nullable
	private final String searchQueryTotal;

	@Nullable
	private final String priSearchQueryTotal;

	@Nullable
	private final String searchScrollCurrent;

	@Nullable
	private final String priSearchScrollCurrent;

	@Nullable
	private final String searchScrollTime;

	@Nullable
	private final String priSearchScrollTime;

	@Nullable
	private final String searchScrollTotal;

	@Nullable
	private final String priSearchScrollTotal;

	@Nullable
	private final String segmentsCount;

	@Nullable
	private final String priSegmentsCount;

	@Nullable
	private final String segmentsMemory;

	@Nullable
	private final String priSegmentsMemory;

	@Nullable
	private final String segmentsIndexWriterMemory;

	@Nullable
	private final String priSegmentsIndexWriterMemory;

	@Nullable
	private final String segmentsVersionMapMemory;

	@Nullable
	private final String priSegmentsVersionMapMemory;

	@Nullable
	private final String segmentsFixedBitsetMemory;

	@Nullable
	private final String priSegmentsFixedBitsetMemory;

	@Nullable
	private final String warmerCurrent;

	@Nullable
	private final String priWarmerCurrent;

	@Nullable
	private final String warmerTotal;

	@Nullable
	private final String priWarmerTotal;

	@Nullable
	private final String warmerTotalTime;

	@Nullable
	private final String priWarmerTotalTime;

	@Nullable
	private final String suggestCurrent;

	@Nullable
	private final String priSuggestCurrent;

	@Nullable
	private final String suggestTime;

	@Nullable
	private final String priSuggestTime;

	@Nullable
	private final String suggestTotal;

	@Nullable
	private final String priSuggestTotal;

	@Nullable
	private final String memoryTotal;

	@Nullable
	private final String priMemoryTotal;

	@Nullable
	private final String searchThrottled;

	@Nullable
	private final String bulkTotalOperations;

	@Nullable
	private final String priBulkTotalOperations;

	@Nullable
	private final String bulkTotalTime;

	@Nullable
	private final String priBulkTotalTime;

	@Nullable
	private final String bulkTotalSizeInBytes;

	@Nullable
	private final String priBulkTotalSizeInBytes;

	@Nullable
	private final String bulkAvgTime;

	@Nullable
	private final String priBulkAvgTime;

	@Nullable
	private final String bulkAvgSizeInBytes;

	@Nullable
	private final String priBulkAvgSizeInBytes;

	// ---------------------------------------------------------------------------------------------

	private IndicesRecord(Builder builder) {

		this.health = builder.health;
		this.status = builder.status;
		this.index = builder.index;
		this.uuid = builder.uuid;
		this.pri = builder.pri;
		this.rep = builder.rep;
		this.docsCount = builder.docsCount;
		this.docsDeleted = builder.docsDeleted;
		this.creationDate = builder.creationDate;
		this.creationDateString = builder.creationDateString;
		this.storeSize = builder.storeSize;
		this.priStoreSize = builder.priStoreSize;
		this.completionSize = builder.completionSize;
		this.priCompletionSize = builder.priCompletionSize;
		this.fielddataMemorySize = builder.fielddataMemorySize;
		this.priFielddataMemorySize = builder.priFielddataMemorySize;
		this.fielddataEvictions = builder.fielddataEvictions;
		this.priFielddataEvictions = builder.priFielddataEvictions;
		this.queryCacheMemorySize = builder.queryCacheMemorySize;
		this.priQueryCacheMemorySize = builder.priQueryCacheMemorySize;
		this.queryCacheEvictions = builder.queryCacheEvictions;
		this.priQueryCacheEvictions = builder.priQueryCacheEvictions;
		this.requestCacheMemorySize = builder.requestCacheMemorySize;
		this.priRequestCacheMemorySize = builder.priRequestCacheMemorySize;
		this.requestCacheEvictions = builder.requestCacheEvictions;
		this.priRequestCacheEvictions = builder.priRequestCacheEvictions;
		this.requestCacheHitCount = builder.requestCacheHitCount;
		this.priRequestCacheHitCount = builder.priRequestCacheHitCount;
		this.requestCacheMissCount = builder.requestCacheMissCount;
		this.priRequestCacheMissCount = builder.priRequestCacheMissCount;
		this.flushTotal = builder.flushTotal;
		this.priFlushTotal = builder.priFlushTotal;
		this.flushTotalTime = builder.flushTotalTime;
		this.priFlushTotalTime = builder.priFlushTotalTime;
		this.getCurrent = builder.getCurrent;
		this.priGetCurrent = builder.priGetCurrent;
		this.getTime = builder.getTime;
		this.priGetTime = builder.priGetTime;
		this.getTotal = builder.getTotal;
		this.priGetTotal = builder.priGetTotal;
		this.getExistsTime = builder.getExistsTime;
		this.priGetExistsTime = builder.priGetExistsTime;
		this.getExistsTotal = builder.getExistsTotal;
		this.priGetExistsTotal = builder.priGetExistsTotal;
		this.getMissingTime = builder.getMissingTime;
		this.priGetMissingTime = builder.priGetMissingTime;
		this.getMissingTotal = builder.getMissingTotal;
		this.priGetMissingTotal = builder.priGetMissingTotal;
		this.indexingDeleteCurrent = builder.indexingDeleteCurrent;
		this.priIndexingDeleteCurrent = builder.priIndexingDeleteCurrent;
		this.indexingDeleteTime = builder.indexingDeleteTime;
		this.priIndexingDeleteTime = builder.priIndexingDeleteTime;
		this.indexingDeleteTotal = builder.indexingDeleteTotal;
		this.priIndexingDeleteTotal = builder.priIndexingDeleteTotal;
		this.indexingIndexCurrent = builder.indexingIndexCurrent;
		this.priIndexingIndexCurrent = builder.priIndexingIndexCurrent;
		this.indexingIndexTime = builder.indexingIndexTime;
		this.priIndexingIndexTime = builder.priIndexingIndexTime;
		this.indexingIndexTotal = builder.indexingIndexTotal;
		this.priIndexingIndexTotal = builder.priIndexingIndexTotal;
		this.indexingIndexFailed = builder.indexingIndexFailed;
		this.priIndexingIndexFailed = builder.priIndexingIndexFailed;
		this.mergesCurrent = builder.mergesCurrent;
		this.priMergesCurrent = builder.priMergesCurrent;
		this.mergesCurrentDocs = builder.mergesCurrentDocs;
		this.priMergesCurrentDocs = builder.priMergesCurrentDocs;
		this.mergesCurrentSize = builder.mergesCurrentSize;
		this.priMergesCurrentSize = builder.priMergesCurrentSize;
		this.mergesTotal = builder.mergesTotal;
		this.priMergesTotal = builder.priMergesTotal;
		this.mergesTotalDocs = builder.mergesTotalDocs;
		this.priMergesTotalDocs = builder.priMergesTotalDocs;
		this.mergesTotalSize = builder.mergesTotalSize;
		this.priMergesTotalSize = builder.priMergesTotalSize;
		this.mergesTotalTime = builder.mergesTotalTime;
		this.priMergesTotalTime = builder.priMergesTotalTime;
		this.refreshTotal = builder.refreshTotal;
		this.priRefreshTotal = builder.priRefreshTotal;
		this.refreshTime = builder.refreshTime;
		this.priRefreshTime = builder.priRefreshTime;
		this.refreshExternalTotal = builder.refreshExternalTotal;
		this.priRefreshExternalTotal = builder.priRefreshExternalTotal;
		this.refreshExternalTime = builder.refreshExternalTime;
		this.priRefreshExternalTime = builder.priRefreshExternalTime;
		this.refreshListeners = builder.refreshListeners;
		this.priRefreshListeners = builder.priRefreshListeners;
		this.searchFetchCurrent = builder.searchFetchCurrent;
		this.priSearchFetchCurrent = builder.priSearchFetchCurrent;
		this.searchFetchTime = builder.searchFetchTime;
		this.priSearchFetchTime = builder.priSearchFetchTime;
		this.searchFetchTotal = builder.searchFetchTotal;
		this.priSearchFetchTotal = builder.priSearchFetchTotal;
		this.searchOpenContexts = builder.searchOpenContexts;
		this.priSearchOpenContexts = builder.priSearchOpenContexts;
		this.searchQueryCurrent = builder.searchQueryCurrent;
		this.priSearchQueryCurrent = builder.priSearchQueryCurrent;
		this.searchQueryTime = builder.searchQueryTime;
		this.priSearchQueryTime = builder.priSearchQueryTime;
		this.searchQueryTotal = builder.searchQueryTotal;
		this.priSearchQueryTotal = builder.priSearchQueryTotal;
		this.searchScrollCurrent = builder.searchScrollCurrent;
		this.priSearchScrollCurrent = builder.priSearchScrollCurrent;
		this.searchScrollTime = builder.searchScrollTime;
		this.priSearchScrollTime = builder.priSearchScrollTime;
		this.searchScrollTotal = builder.searchScrollTotal;
		this.priSearchScrollTotal = builder.priSearchScrollTotal;
		this.segmentsCount = builder.segmentsCount;
		this.priSegmentsCount = builder.priSegmentsCount;
		this.segmentsMemory = builder.segmentsMemory;
		this.priSegmentsMemory = builder.priSegmentsMemory;
		this.segmentsIndexWriterMemory = builder.segmentsIndexWriterMemory;
		this.priSegmentsIndexWriterMemory = builder.priSegmentsIndexWriterMemory;
		this.segmentsVersionMapMemory = builder.segmentsVersionMapMemory;
		this.priSegmentsVersionMapMemory = builder.priSegmentsVersionMapMemory;
		this.segmentsFixedBitsetMemory = builder.segmentsFixedBitsetMemory;
		this.priSegmentsFixedBitsetMemory = builder.priSegmentsFixedBitsetMemory;
		this.warmerCurrent = builder.warmerCurrent;
		this.priWarmerCurrent = builder.priWarmerCurrent;
		this.warmerTotal = builder.warmerTotal;
		this.priWarmerTotal = builder.priWarmerTotal;
		this.warmerTotalTime = builder.warmerTotalTime;
		this.priWarmerTotalTime = builder.priWarmerTotalTime;
		this.suggestCurrent = builder.suggestCurrent;
		this.priSuggestCurrent = builder.priSuggestCurrent;
		this.suggestTime = builder.suggestTime;
		this.priSuggestTime = builder.priSuggestTime;
		this.suggestTotal = builder.suggestTotal;
		this.priSuggestTotal = builder.priSuggestTotal;
		this.memoryTotal = builder.memoryTotal;
		this.priMemoryTotal = builder.priMemoryTotal;
		this.searchThrottled = builder.searchThrottled;
		this.bulkTotalOperations = builder.bulkTotalOperations;
		this.priBulkTotalOperations = builder.priBulkTotalOperations;
		this.bulkTotalTime = builder.bulkTotalTime;
		this.priBulkTotalTime = builder.priBulkTotalTime;
		this.bulkTotalSizeInBytes = builder.bulkTotalSizeInBytes;
		this.priBulkTotalSizeInBytes = builder.priBulkTotalSizeInBytes;
		this.bulkAvgTime = builder.bulkAvgTime;
		this.priBulkAvgTime = builder.priBulkAvgTime;
		this.bulkAvgSizeInBytes = builder.bulkAvgSizeInBytes;
		this.priBulkAvgSizeInBytes = builder.priBulkAvgSizeInBytes;

	}

	public static IndicesRecord of(Function<Builder, ObjectBuilder<IndicesRecord>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * current health status
	 * <p>
	 * API name: {@code health}
	 */
	@Nullable
	public final String health() {
		return this.health;
	}

	/**
	 * open/close status
	 * <p>
	 * API name: {@code status}
	 */
	@Nullable
	public final String status() {
		return this.status;
	}

	/**
	 * index name
	 * <p>
	 * API name: {@code index}
	 */
	@Nullable
	public final String index() {
		return this.index;
	}

	/**
	 * index uuid
	 * <p>
	 * API name: {@code uuid}
	 */
	@Nullable
	public final String uuid() {
		return this.uuid;
	}

	/**
	 * number of primary shards
	 * <p>
	 * API name: {@code pri}
	 */
	@Nullable
	public final String pri() {
		return this.pri;
	}

	/**
	 * number of replica shards
	 * <p>
	 * API name: {@code rep}
	 */
	@Nullable
	public final String rep() {
		return this.rep;
	}

	/**
	 * available docs
	 * <p>
	 * API name: {@code docs.count}
	 */
	@Nullable
	public final String docsCount() {
		return this.docsCount;
	}

	/**
	 * deleted docs
	 * <p>
	 * API name: {@code docs.deleted}
	 */
	@Nullable
	public final String docsDeleted() {
		return this.docsDeleted;
	}

	/**
	 * index creation date (millisecond value)
	 * <p>
	 * API name: {@code creation.date}
	 */
	@Nullable
	public final String creationDate() {
		return this.creationDate;
	}

	/**
	 * index creation date (as string)
	 * <p>
	 * API name: {@code creation.date.string}
	 */
	@Nullable
	public final String creationDateString() {
		return this.creationDateString;
	}

	/**
	 * store size of primaries &amp; replicas
	 * <p>
	 * API name: {@code store.size}
	 */
	@Nullable
	public final String storeSize() {
		return this.storeSize;
	}

	/**
	 * store size of primaries
	 * <p>
	 * API name: {@code pri.store.size}
	 */
	@Nullable
	public final String priStoreSize() {
		return this.priStoreSize;
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
	 * size of completion
	 * <p>
	 * API name: {@code pri.completion.size}
	 */
	@Nullable
	public final String priCompletionSize() {
		return this.priCompletionSize;
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
	 * used fielddata cache
	 * <p>
	 * API name: {@code pri.fielddata.memory_size}
	 */
	@Nullable
	public final String priFielddataMemorySize() {
		return this.priFielddataMemorySize;
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
	 * fielddata evictions
	 * <p>
	 * API name: {@code pri.fielddata.evictions}
	 */
	@Nullable
	public final String priFielddataEvictions() {
		return this.priFielddataEvictions;
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
	 * used query cache
	 * <p>
	 * API name: {@code pri.query_cache.memory_size}
	 */
	@Nullable
	public final String priQueryCacheMemorySize() {
		return this.priQueryCacheMemorySize;
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
	 * query cache evictions
	 * <p>
	 * API name: {@code pri.query_cache.evictions}
	 */
	@Nullable
	public final String priQueryCacheEvictions() {
		return this.priQueryCacheEvictions;
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
	 * used request cache
	 * <p>
	 * API name: {@code pri.request_cache.memory_size}
	 */
	@Nullable
	public final String priRequestCacheMemorySize() {
		return this.priRequestCacheMemorySize;
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
	 * request cache evictions
	 * <p>
	 * API name: {@code pri.request_cache.evictions}
	 */
	@Nullable
	public final String priRequestCacheEvictions() {
		return this.priRequestCacheEvictions;
	}

	/**
	 * request cache hit count
	 * <p>
	 * API name: {@code request_cache.hit_count}
	 */
	@Nullable
	public final String requestCacheHitCount() {
		return this.requestCacheHitCount;
	}

	/**
	 * request cache hit count
	 * <p>
	 * API name: {@code pri.request_cache.hit_count}
	 */
	@Nullable
	public final String priRequestCacheHitCount() {
		return this.priRequestCacheHitCount;
	}

	/**
	 * request cache miss count
	 * <p>
	 * API name: {@code request_cache.miss_count}
	 */
	@Nullable
	public final String requestCacheMissCount() {
		return this.requestCacheMissCount;
	}

	/**
	 * request cache miss count
	 * <p>
	 * API name: {@code pri.request_cache.miss_count}
	 */
	@Nullable
	public final String priRequestCacheMissCount() {
		return this.priRequestCacheMissCount;
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
	 * number of flushes
	 * <p>
	 * API name: {@code pri.flush.total}
	 */
	@Nullable
	public final String priFlushTotal() {
		return this.priFlushTotal;
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
	 * time spent in flush
	 * <p>
	 * API name: {@code pri.flush.total_time}
	 */
	@Nullable
	public final String priFlushTotalTime() {
		return this.priFlushTotalTime;
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
	 * number of current get ops
	 * <p>
	 * API name: {@code pri.get.current}
	 */
	@Nullable
	public final String priGetCurrent() {
		return this.priGetCurrent;
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
	 * time spent in get
	 * <p>
	 * API name: {@code pri.get.time}
	 */
	@Nullable
	public final String priGetTime() {
		return this.priGetTime;
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
	 * number of get ops
	 * <p>
	 * API name: {@code pri.get.total}
	 */
	@Nullable
	public final String priGetTotal() {
		return this.priGetTotal;
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
	 * time spent in successful gets
	 * <p>
	 * API name: {@code pri.get.exists_time}
	 */
	@Nullable
	public final String priGetExistsTime() {
		return this.priGetExistsTime;
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
	 * number of successful gets
	 * <p>
	 * API name: {@code pri.get.exists_total}
	 */
	@Nullable
	public final String priGetExistsTotal() {
		return this.priGetExistsTotal;
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
	 * time spent in failed gets
	 * <p>
	 * API name: {@code pri.get.missing_time}
	 */
	@Nullable
	public final String priGetMissingTime() {
		return this.priGetMissingTime;
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
	 * number of failed gets
	 * <p>
	 * API name: {@code pri.get.missing_total}
	 */
	@Nullable
	public final String priGetMissingTotal() {
		return this.priGetMissingTotal;
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
	 * number of current deletions
	 * <p>
	 * API name: {@code pri.indexing.delete_current}
	 */
	@Nullable
	public final String priIndexingDeleteCurrent() {
		return this.priIndexingDeleteCurrent;
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
	 * time spent in deletions
	 * <p>
	 * API name: {@code pri.indexing.delete_time}
	 */
	@Nullable
	public final String priIndexingDeleteTime() {
		return this.priIndexingDeleteTime;
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
	 * number of delete ops
	 * <p>
	 * API name: {@code pri.indexing.delete_total}
	 */
	@Nullable
	public final String priIndexingDeleteTotal() {
		return this.priIndexingDeleteTotal;
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
	 * number of current indexing ops
	 * <p>
	 * API name: {@code pri.indexing.index_current}
	 */
	@Nullable
	public final String priIndexingIndexCurrent() {
		return this.priIndexingIndexCurrent;
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
	 * time spent in indexing
	 * <p>
	 * API name: {@code pri.indexing.index_time}
	 */
	@Nullable
	public final String priIndexingIndexTime() {
		return this.priIndexingIndexTime;
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
	 * number of indexing ops
	 * <p>
	 * API name: {@code pri.indexing.index_total}
	 */
	@Nullable
	public final String priIndexingIndexTotal() {
		return this.priIndexingIndexTotal;
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
	 * number of failed indexing ops
	 * <p>
	 * API name: {@code pri.indexing.index_failed}
	 */
	@Nullable
	public final String priIndexingIndexFailed() {
		return this.priIndexingIndexFailed;
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
	 * number of current merges
	 * <p>
	 * API name: {@code pri.merges.current}
	 */
	@Nullable
	public final String priMergesCurrent() {
		return this.priMergesCurrent;
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
	 * number of current merging docs
	 * <p>
	 * API name: {@code pri.merges.current_docs}
	 */
	@Nullable
	public final String priMergesCurrentDocs() {
		return this.priMergesCurrentDocs;
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
	 * size of current merges
	 * <p>
	 * API name: {@code pri.merges.current_size}
	 */
	@Nullable
	public final String priMergesCurrentSize() {
		return this.priMergesCurrentSize;
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
	 * number of completed merge ops
	 * <p>
	 * API name: {@code pri.merges.total}
	 */
	@Nullable
	public final String priMergesTotal() {
		return this.priMergesTotal;
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
	 * docs merged
	 * <p>
	 * API name: {@code pri.merges.total_docs}
	 */
	@Nullable
	public final String priMergesTotalDocs() {
		return this.priMergesTotalDocs;
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
	 * size merged
	 * <p>
	 * API name: {@code pri.merges.total_size}
	 */
	@Nullable
	public final String priMergesTotalSize() {
		return this.priMergesTotalSize;
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
	 * time spent in merges
	 * <p>
	 * API name: {@code pri.merges.total_time}
	 */
	@Nullable
	public final String priMergesTotalTime() {
		return this.priMergesTotalTime;
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
	 * total refreshes
	 * <p>
	 * API name: {@code pri.refresh.total}
	 */
	@Nullable
	public final String priRefreshTotal() {
		return this.priRefreshTotal;
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
	 * time spent in refreshes
	 * <p>
	 * API name: {@code pri.refresh.time}
	 */
	@Nullable
	public final String priRefreshTime() {
		return this.priRefreshTime;
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
	 * total external refreshes
	 * <p>
	 * API name: {@code pri.refresh.external_total}
	 */
	@Nullable
	public final String priRefreshExternalTotal() {
		return this.priRefreshExternalTotal;
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
	 * time spent in external refreshes
	 * <p>
	 * API name: {@code pri.refresh.external_time}
	 */
	@Nullable
	public final String priRefreshExternalTime() {
		return this.priRefreshExternalTime;
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
	 * number of pending refresh listeners
	 * <p>
	 * API name: {@code pri.refresh.listeners}
	 */
	@Nullable
	public final String priRefreshListeners() {
		return this.priRefreshListeners;
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
	 * current fetch phase ops
	 * <p>
	 * API name: {@code pri.search.fetch_current}
	 */
	@Nullable
	public final String priSearchFetchCurrent() {
		return this.priSearchFetchCurrent;
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
	 * time spent in fetch phase
	 * <p>
	 * API name: {@code pri.search.fetch_time}
	 */
	@Nullable
	public final String priSearchFetchTime() {
		return this.priSearchFetchTime;
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
	 * total fetch ops
	 * <p>
	 * API name: {@code pri.search.fetch_total}
	 */
	@Nullable
	public final String priSearchFetchTotal() {
		return this.priSearchFetchTotal;
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
	 * open search contexts
	 * <p>
	 * API name: {@code pri.search.open_contexts}
	 */
	@Nullable
	public final String priSearchOpenContexts() {
		return this.priSearchOpenContexts;
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
	 * current query phase ops
	 * <p>
	 * API name: {@code pri.search.query_current}
	 */
	@Nullable
	public final String priSearchQueryCurrent() {
		return this.priSearchQueryCurrent;
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
	 * time spent in query phase
	 * <p>
	 * API name: {@code pri.search.query_time}
	 */
	@Nullable
	public final String priSearchQueryTime() {
		return this.priSearchQueryTime;
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
	 * total query phase ops
	 * <p>
	 * API name: {@code pri.search.query_total}
	 */
	@Nullable
	public final String priSearchQueryTotal() {
		return this.priSearchQueryTotal;
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
	 * open scroll contexts
	 * <p>
	 * API name: {@code pri.search.scroll_current}
	 */
	@Nullable
	public final String priSearchScrollCurrent() {
		return this.priSearchScrollCurrent;
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
	 * time scroll contexts held open
	 * <p>
	 * API name: {@code pri.search.scroll_time}
	 */
	@Nullable
	public final String priSearchScrollTime() {
		return this.priSearchScrollTime;
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
	 * completed scroll contexts
	 * <p>
	 * API name: {@code pri.search.scroll_total}
	 */
	@Nullable
	public final String priSearchScrollTotal() {
		return this.priSearchScrollTotal;
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
	 * number of segments
	 * <p>
	 * API name: {@code pri.segments.count}
	 */
	@Nullable
	public final String priSegmentsCount() {
		return this.priSegmentsCount;
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
	 * memory used by segments
	 * <p>
	 * API name: {@code pri.segments.memory}
	 */
	@Nullable
	public final String priSegmentsMemory() {
		return this.priSegmentsMemory;
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
	 * memory used by index writer
	 * <p>
	 * API name: {@code pri.segments.index_writer_memory}
	 */
	@Nullable
	public final String priSegmentsIndexWriterMemory() {
		return this.priSegmentsIndexWriterMemory;
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
	 * memory used by version map
	 * <p>
	 * API name: {@code pri.segments.version_map_memory}
	 */
	@Nullable
	public final String priSegmentsVersionMapMemory() {
		return this.priSegmentsVersionMapMemory;
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
	 * memory used by fixed bit sets for nested object field types and export type
	 * filters for types referred in _parent fields
	 * <p>
	 * API name: {@code pri.segments.fixed_bitset_memory}
	 */
	@Nullable
	public final String priSegmentsFixedBitsetMemory() {
		return this.priSegmentsFixedBitsetMemory;
	}

	/**
	 * current warmer ops
	 * <p>
	 * API name: {@code warmer.current}
	 */
	@Nullable
	public final String warmerCurrent() {
		return this.warmerCurrent;
	}

	/**
	 * current warmer ops
	 * <p>
	 * API name: {@code pri.warmer.current}
	 */
	@Nullable
	public final String priWarmerCurrent() {
		return this.priWarmerCurrent;
	}

	/**
	 * total warmer ops
	 * <p>
	 * API name: {@code warmer.total}
	 */
	@Nullable
	public final String warmerTotal() {
		return this.warmerTotal;
	}

	/**
	 * total warmer ops
	 * <p>
	 * API name: {@code pri.warmer.total}
	 */
	@Nullable
	public final String priWarmerTotal() {
		return this.priWarmerTotal;
	}

	/**
	 * time spent in warmers
	 * <p>
	 * API name: {@code warmer.total_time}
	 */
	@Nullable
	public final String warmerTotalTime() {
		return this.warmerTotalTime;
	}

	/**
	 * time spent in warmers
	 * <p>
	 * API name: {@code pri.warmer.total_time}
	 */
	@Nullable
	public final String priWarmerTotalTime() {
		return this.priWarmerTotalTime;
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
	 * number of current suggest ops
	 * <p>
	 * API name: {@code pri.suggest.current}
	 */
	@Nullable
	public final String priSuggestCurrent() {
		return this.priSuggestCurrent;
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
	 * time spend in suggest
	 * <p>
	 * API name: {@code pri.suggest.time}
	 */
	@Nullable
	public final String priSuggestTime() {
		return this.priSuggestTime;
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
	 * number of suggest ops
	 * <p>
	 * API name: {@code pri.suggest.total}
	 */
	@Nullable
	public final String priSuggestTotal() {
		return this.priSuggestTotal;
	}

	/**
	 * total used memory
	 * <p>
	 * API name: {@code memory.total}
	 */
	@Nullable
	public final String memoryTotal() {
		return this.memoryTotal;
	}

	/**
	 * total user memory
	 * <p>
	 * API name: {@code pri.memory.total}
	 */
	@Nullable
	public final String priMemoryTotal() {
		return this.priMemoryTotal;
	}

	/**
	 * indicates if the index is search throttled
	 * <p>
	 * API name: {@code search.throttled}
	 */
	@Nullable
	public final String searchThrottled() {
		return this.searchThrottled;
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
	 * number of bulk shard ops
	 * <p>
	 * API name: {@code pri.bulk.total_operations}
	 */
	@Nullable
	public final String priBulkTotalOperations() {
		return this.priBulkTotalOperations;
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
	 * time spend in shard bulk
	 * <p>
	 * API name: {@code pri.bulk.total_time}
	 */
	@Nullable
	public final String priBulkTotalTime() {
		return this.priBulkTotalTime;
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
	 * total size in bytes of shard bulk
	 * <p>
	 * API name: {@code pri.bulk.total_size_in_bytes}
	 */
	@Nullable
	public final String priBulkTotalSizeInBytes() {
		return this.priBulkTotalSizeInBytes;
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
	 * average time spend in shard bulk
	 * <p>
	 * API name: {@code pri.bulk.avg_time}
	 */
	@Nullable
	public final String priBulkAvgTime() {
		return this.priBulkAvgTime;
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
	 * average size in bytes of shard bulk
	 * <p>
	 * API name: {@code pri.bulk.avg_size_in_bytes}
	 */
	@Nullable
	public final String priBulkAvgSizeInBytes() {
		return this.priBulkAvgSizeInBytes;
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

		if (this.health != null) {
			generator.writeKey("health");
			generator.write(this.health);

		}
		if (this.status != null) {
			generator.writeKey("status");
			generator.write(this.status);

		}
		if (this.index != null) {
			generator.writeKey("index");
			generator.write(this.index);

		}
		if (this.uuid != null) {
			generator.writeKey("uuid");
			generator.write(this.uuid);

		}
		if (this.pri != null) {
			generator.writeKey("pri");
			generator.write(this.pri);

		}
		if (this.rep != null) {
			generator.writeKey("rep");
			generator.write(this.rep);

		}
		if (this.docsCount != null) {
			generator.writeKey("docs.count");
			generator.write(this.docsCount);

		}
		if (this.docsDeleted != null) {
			generator.writeKey("docs.deleted");
			generator.write(this.docsDeleted);

		}
		if (this.creationDate != null) {
			generator.writeKey("creation.date");
			generator.write(this.creationDate);

		}
		if (this.creationDateString != null) {
			generator.writeKey("creation.date.string");
			generator.write(this.creationDateString);

		}
		if (this.storeSize != null) {
			generator.writeKey("store.size");
			generator.write(this.storeSize);

		}
		if (this.priStoreSize != null) {
			generator.writeKey("pri.store.size");
			generator.write(this.priStoreSize);

		}
		if (this.completionSize != null) {
			generator.writeKey("completion.size");
			generator.write(this.completionSize);

		}
		if (this.priCompletionSize != null) {
			generator.writeKey("pri.completion.size");
			generator.write(this.priCompletionSize);

		}
		if (this.fielddataMemorySize != null) {
			generator.writeKey("fielddata.memory_size");
			generator.write(this.fielddataMemorySize);

		}
		if (this.priFielddataMemorySize != null) {
			generator.writeKey("pri.fielddata.memory_size");
			generator.write(this.priFielddataMemorySize);

		}
		if (this.fielddataEvictions != null) {
			generator.writeKey("fielddata.evictions");
			generator.write(this.fielddataEvictions);

		}
		if (this.priFielddataEvictions != null) {
			generator.writeKey("pri.fielddata.evictions");
			generator.write(this.priFielddataEvictions);

		}
		if (this.queryCacheMemorySize != null) {
			generator.writeKey("query_cache.memory_size");
			generator.write(this.queryCacheMemorySize);

		}
		if (this.priQueryCacheMemorySize != null) {
			generator.writeKey("pri.query_cache.memory_size");
			generator.write(this.priQueryCacheMemorySize);

		}
		if (this.queryCacheEvictions != null) {
			generator.writeKey("query_cache.evictions");
			generator.write(this.queryCacheEvictions);

		}
		if (this.priQueryCacheEvictions != null) {
			generator.writeKey("pri.query_cache.evictions");
			generator.write(this.priQueryCacheEvictions);

		}
		if (this.requestCacheMemorySize != null) {
			generator.writeKey("request_cache.memory_size");
			generator.write(this.requestCacheMemorySize);

		}
		if (this.priRequestCacheMemorySize != null) {
			generator.writeKey("pri.request_cache.memory_size");
			generator.write(this.priRequestCacheMemorySize);

		}
		if (this.requestCacheEvictions != null) {
			generator.writeKey("request_cache.evictions");
			generator.write(this.requestCacheEvictions);

		}
		if (this.priRequestCacheEvictions != null) {
			generator.writeKey("pri.request_cache.evictions");
			generator.write(this.priRequestCacheEvictions);

		}
		if (this.requestCacheHitCount != null) {
			generator.writeKey("request_cache.hit_count");
			generator.write(this.requestCacheHitCount);

		}
		if (this.priRequestCacheHitCount != null) {
			generator.writeKey("pri.request_cache.hit_count");
			generator.write(this.priRequestCacheHitCount);

		}
		if (this.requestCacheMissCount != null) {
			generator.writeKey("request_cache.miss_count");
			generator.write(this.requestCacheMissCount);

		}
		if (this.priRequestCacheMissCount != null) {
			generator.writeKey("pri.request_cache.miss_count");
			generator.write(this.priRequestCacheMissCount);

		}
		if (this.flushTotal != null) {
			generator.writeKey("flush.total");
			generator.write(this.flushTotal);

		}
		if (this.priFlushTotal != null) {
			generator.writeKey("pri.flush.total");
			generator.write(this.priFlushTotal);

		}
		if (this.flushTotalTime != null) {
			generator.writeKey("flush.total_time");
			generator.write(this.flushTotalTime);

		}
		if (this.priFlushTotalTime != null) {
			generator.writeKey("pri.flush.total_time");
			generator.write(this.priFlushTotalTime);

		}
		if (this.getCurrent != null) {
			generator.writeKey("get.current");
			generator.write(this.getCurrent);

		}
		if (this.priGetCurrent != null) {
			generator.writeKey("pri.get.current");
			generator.write(this.priGetCurrent);

		}
		if (this.getTime != null) {
			generator.writeKey("get.time");
			generator.write(this.getTime);

		}
		if (this.priGetTime != null) {
			generator.writeKey("pri.get.time");
			generator.write(this.priGetTime);

		}
		if (this.getTotal != null) {
			generator.writeKey("get.total");
			generator.write(this.getTotal);

		}
		if (this.priGetTotal != null) {
			generator.writeKey("pri.get.total");
			generator.write(this.priGetTotal);

		}
		if (this.getExistsTime != null) {
			generator.writeKey("get.exists_time");
			generator.write(this.getExistsTime);

		}
		if (this.priGetExistsTime != null) {
			generator.writeKey("pri.get.exists_time");
			generator.write(this.priGetExistsTime);

		}
		if (this.getExistsTotal != null) {
			generator.writeKey("get.exists_total");
			generator.write(this.getExistsTotal);

		}
		if (this.priGetExistsTotal != null) {
			generator.writeKey("pri.get.exists_total");
			generator.write(this.priGetExistsTotal);

		}
		if (this.getMissingTime != null) {
			generator.writeKey("get.missing_time");
			generator.write(this.getMissingTime);

		}
		if (this.priGetMissingTime != null) {
			generator.writeKey("pri.get.missing_time");
			generator.write(this.priGetMissingTime);

		}
		if (this.getMissingTotal != null) {
			generator.writeKey("get.missing_total");
			generator.write(this.getMissingTotal);

		}
		if (this.priGetMissingTotal != null) {
			generator.writeKey("pri.get.missing_total");
			generator.write(this.priGetMissingTotal);

		}
		if (this.indexingDeleteCurrent != null) {
			generator.writeKey("indexing.delete_current");
			generator.write(this.indexingDeleteCurrent);

		}
		if (this.priIndexingDeleteCurrent != null) {
			generator.writeKey("pri.indexing.delete_current");
			generator.write(this.priIndexingDeleteCurrent);

		}
		if (this.indexingDeleteTime != null) {
			generator.writeKey("indexing.delete_time");
			generator.write(this.indexingDeleteTime);

		}
		if (this.priIndexingDeleteTime != null) {
			generator.writeKey("pri.indexing.delete_time");
			generator.write(this.priIndexingDeleteTime);

		}
		if (this.indexingDeleteTotal != null) {
			generator.writeKey("indexing.delete_total");
			generator.write(this.indexingDeleteTotal);

		}
		if (this.priIndexingDeleteTotal != null) {
			generator.writeKey("pri.indexing.delete_total");
			generator.write(this.priIndexingDeleteTotal);

		}
		if (this.indexingIndexCurrent != null) {
			generator.writeKey("indexing.index_current");
			generator.write(this.indexingIndexCurrent);

		}
		if (this.priIndexingIndexCurrent != null) {
			generator.writeKey("pri.indexing.index_current");
			generator.write(this.priIndexingIndexCurrent);

		}
		if (this.indexingIndexTime != null) {
			generator.writeKey("indexing.index_time");
			generator.write(this.indexingIndexTime);

		}
		if (this.priIndexingIndexTime != null) {
			generator.writeKey("pri.indexing.index_time");
			generator.write(this.priIndexingIndexTime);

		}
		if (this.indexingIndexTotal != null) {
			generator.writeKey("indexing.index_total");
			generator.write(this.indexingIndexTotal);

		}
		if (this.priIndexingIndexTotal != null) {
			generator.writeKey("pri.indexing.index_total");
			generator.write(this.priIndexingIndexTotal);

		}
		if (this.indexingIndexFailed != null) {
			generator.writeKey("indexing.index_failed");
			generator.write(this.indexingIndexFailed);

		}
		if (this.priIndexingIndexFailed != null) {
			generator.writeKey("pri.indexing.index_failed");
			generator.write(this.priIndexingIndexFailed);

		}
		if (this.mergesCurrent != null) {
			generator.writeKey("merges.current");
			generator.write(this.mergesCurrent);

		}
		if (this.priMergesCurrent != null) {
			generator.writeKey("pri.merges.current");
			generator.write(this.priMergesCurrent);

		}
		if (this.mergesCurrentDocs != null) {
			generator.writeKey("merges.current_docs");
			generator.write(this.mergesCurrentDocs);

		}
		if (this.priMergesCurrentDocs != null) {
			generator.writeKey("pri.merges.current_docs");
			generator.write(this.priMergesCurrentDocs);

		}
		if (this.mergesCurrentSize != null) {
			generator.writeKey("merges.current_size");
			generator.write(this.mergesCurrentSize);

		}
		if (this.priMergesCurrentSize != null) {
			generator.writeKey("pri.merges.current_size");
			generator.write(this.priMergesCurrentSize);

		}
		if (this.mergesTotal != null) {
			generator.writeKey("merges.total");
			generator.write(this.mergesTotal);

		}
		if (this.priMergesTotal != null) {
			generator.writeKey("pri.merges.total");
			generator.write(this.priMergesTotal);

		}
		if (this.mergesTotalDocs != null) {
			generator.writeKey("merges.total_docs");
			generator.write(this.mergesTotalDocs);

		}
		if (this.priMergesTotalDocs != null) {
			generator.writeKey("pri.merges.total_docs");
			generator.write(this.priMergesTotalDocs);

		}
		if (this.mergesTotalSize != null) {
			generator.writeKey("merges.total_size");
			generator.write(this.mergesTotalSize);

		}
		if (this.priMergesTotalSize != null) {
			generator.writeKey("pri.merges.total_size");
			generator.write(this.priMergesTotalSize);

		}
		if (this.mergesTotalTime != null) {
			generator.writeKey("merges.total_time");
			generator.write(this.mergesTotalTime);

		}
		if (this.priMergesTotalTime != null) {
			generator.writeKey("pri.merges.total_time");
			generator.write(this.priMergesTotalTime);

		}
		if (this.refreshTotal != null) {
			generator.writeKey("refresh.total");
			generator.write(this.refreshTotal);

		}
		if (this.priRefreshTotal != null) {
			generator.writeKey("pri.refresh.total");
			generator.write(this.priRefreshTotal);

		}
		if (this.refreshTime != null) {
			generator.writeKey("refresh.time");
			generator.write(this.refreshTime);

		}
		if (this.priRefreshTime != null) {
			generator.writeKey("pri.refresh.time");
			generator.write(this.priRefreshTime);

		}
		if (this.refreshExternalTotal != null) {
			generator.writeKey("refresh.external_total");
			generator.write(this.refreshExternalTotal);

		}
		if (this.priRefreshExternalTotal != null) {
			generator.writeKey("pri.refresh.external_total");
			generator.write(this.priRefreshExternalTotal);

		}
		if (this.refreshExternalTime != null) {
			generator.writeKey("refresh.external_time");
			generator.write(this.refreshExternalTime);

		}
		if (this.priRefreshExternalTime != null) {
			generator.writeKey("pri.refresh.external_time");
			generator.write(this.priRefreshExternalTime);

		}
		if (this.refreshListeners != null) {
			generator.writeKey("refresh.listeners");
			generator.write(this.refreshListeners);

		}
		if (this.priRefreshListeners != null) {
			generator.writeKey("pri.refresh.listeners");
			generator.write(this.priRefreshListeners);

		}
		if (this.searchFetchCurrent != null) {
			generator.writeKey("search.fetch_current");
			generator.write(this.searchFetchCurrent);

		}
		if (this.priSearchFetchCurrent != null) {
			generator.writeKey("pri.search.fetch_current");
			generator.write(this.priSearchFetchCurrent);

		}
		if (this.searchFetchTime != null) {
			generator.writeKey("search.fetch_time");
			generator.write(this.searchFetchTime);

		}
		if (this.priSearchFetchTime != null) {
			generator.writeKey("pri.search.fetch_time");
			generator.write(this.priSearchFetchTime);

		}
		if (this.searchFetchTotal != null) {
			generator.writeKey("search.fetch_total");
			generator.write(this.searchFetchTotal);

		}
		if (this.priSearchFetchTotal != null) {
			generator.writeKey("pri.search.fetch_total");
			generator.write(this.priSearchFetchTotal);

		}
		if (this.searchOpenContexts != null) {
			generator.writeKey("search.open_contexts");
			generator.write(this.searchOpenContexts);

		}
		if (this.priSearchOpenContexts != null) {
			generator.writeKey("pri.search.open_contexts");
			generator.write(this.priSearchOpenContexts);

		}
		if (this.searchQueryCurrent != null) {
			generator.writeKey("search.query_current");
			generator.write(this.searchQueryCurrent);

		}
		if (this.priSearchQueryCurrent != null) {
			generator.writeKey("pri.search.query_current");
			generator.write(this.priSearchQueryCurrent);

		}
		if (this.searchQueryTime != null) {
			generator.writeKey("search.query_time");
			generator.write(this.searchQueryTime);

		}
		if (this.priSearchQueryTime != null) {
			generator.writeKey("pri.search.query_time");
			generator.write(this.priSearchQueryTime);

		}
		if (this.searchQueryTotal != null) {
			generator.writeKey("search.query_total");
			generator.write(this.searchQueryTotal);

		}
		if (this.priSearchQueryTotal != null) {
			generator.writeKey("pri.search.query_total");
			generator.write(this.priSearchQueryTotal);

		}
		if (this.searchScrollCurrent != null) {
			generator.writeKey("search.scroll_current");
			generator.write(this.searchScrollCurrent);

		}
		if (this.priSearchScrollCurrent != null) {
			generator.writeKey("pri.search.scroll_current");
			generator.write(this.priSearchScrollCurrent);

		}
		if (this.searchScrollTime != null) {
			generator.writeKey("search.scroll_time");
			generator.write(this.searchScrollTime);

		}
		if (this.priSearchScrollTime != null) {
			generator.writeKey("pri.search.scroll_time");
			generator.write(this.priSearchScrollTime);

		}
		if (this.searchScrollTotal != null) {
			generator.writeKey("search.scroll_total");
			generator.write(this.searchScrollTotal);

		}
		if (this.priSearchScrollTotal != null) {
			generator.writeKey("pri.search.scroll_total");
			generator.write(this.priSearchScrollTotal);

		}
		if (this.segmentsCount != null) {
			generator.writeKey("segments.count");
			generator.write(this.segmentsCount);

		}
		if (this.priSegmentsCount != null) {
			generator.writeKey("pri.segments.count");
			generator.write(this.priSegmentsCount);

		}
		if (this.segmentsMemory != null) {
			generator.writeKey("segments.memory");
			generator.write(this.segmentsMemory);

		}
		if (this.priSegmentsMemory != null) {
			generator.writeKey("pri.segments.memory");
			generator.write(this.priSegmentsMemory);

		}
		if (this.segmentsIndexWriterMemory != null) {
			generator.writeKey("segments.index_writer_memory");
			generator.write(this.segmentsIndexWriterMemory);

		}
		if (this.priSegmentsIndexWriterMemory != null) {
			generator.writeKey("pri.segments.index_writer_memory");
			generator.write(this.priSegmentsIndexWriterMemory);

		}
		if (this.segmentsVersionMapMemory != null) {
			generator.writeKey("segments.version_map_memory");
			generator.write(this.segmentsVersionMapMemory);

		}
		if (this.priSegmentsVersionMapMemory != null) {
			generator.writeKey("pri.segments.version_map_memory");
			generator.write(this.priSegmentsVersionMapMemory);

		}
		if (this.segmentsFixedBitsetMemory != null) {
			generator.writeKey("segments.fixed_bitset_memory");
			generator.write(this.segmentsFixedBitsetMemory);

		}
		if (this.priSegmentsFixedBitsetMemory != null) {
			generator.writeKey("pri.segments.fixed_bitset_memory");
			generator.write(this.priSegmentsFixedBitsetMemory);

		}
		if (this.warmerCurrent != null) {
			generator.writeKey("warmer.current");
			generator.write(this.warmerCurrent);

		}
		if (this.priWarmerCurrent != null) {
			generator.writeKey("pri.warmer.current");
			generator.write(this.priWarmerCurrent);

		}
		if (this.warmerTotal != null) {
			generator.writeKey("warmer.total");
			generator.write(this.warmerTotal);

		}
		if (this.priWarmerTotal != null) {
			generator.writeKey("pri.warmer.total");
			generator.write(this.priWarmerTotal);

		}
		if (this.warmerTotalTime != null) {
			generator.writeKey("warmer.total_time");
			generator.write(this.warmerTotalTime);

		}
		if (this.priWarmerTotalTime != null) {
			generator.writeKey("pri.warmer.total_time");
			generator.write(this.priWarmerTotalTime);

		}
		if (this.suggestCurrent != null) {
			generator.writeKey("suggest.current");
			generator.write(this.suggestCurrent);

		}
		if (this.priSuggestCurrent != null) {
			generator.writeKey("pri.suggest.current");
			generator.write(this.priSuggestCurrent);

		}
		if (this.suggestTime != null) {
			generator.writeKey("suggest.time");
			generator.write(this.suggestTime);

		}
		if (this.priSuggestTime != null) {
			generator.writeKey("pri.suggest.time");
			generator.write(this.priSuggestTime);

		}
		if (this.suggestTotal != null) {
			generator.writeKey("suggest.total");
			generator.write(this.suggestTotal);

		}
		if (this.priSuggestTotal != null) {
			generator.writeKey("pri.suggest.total");
			generator.write(this.priSuggestTotal);

		}
		if (this.memoryTotal != null) {
			generator.writeKey("memory.total");
			generator.write(this.memoryTotal);

		}
		if (this.priMemoryTotal != null) {
			generator.writeKey("pri.memory.total");
			generator.write(this.priMemoryTotal);

		}
		if (this.searchThrottled != null) {
			generator.writeKey("search.throttled");
			generator.write(this.searchThrottled);

		}
		if (this.bulkTotalOperations != null) {
			generator.writeKey("bulk.total_operations");
			generator.write(this.bulkTotalOperations);

		}
		if (this.priBulkTotalOperations != null) {
			generator.writeKey("pri.bulk.total_operations");
			generator.write(this.priBulkTotalOperations);

		}
		if (this.bulkTotalTime != null) {
			generator.writeKey("bulk.total_time");
			generator.write(this.bulkTotalTime);

		}
		if (this.priBulkTotalTime != null) {
			generator.writeKey("pri.bulk.total_time");
			generator.write(this.priBulkTotalTime);

		}
		if (this.bulkTotalSizeInBytes != null) {
			generator.writeKey("bulk.total_size_in_bytes");
			generator.write(this.bulkTotalSizeInBytes);

		}
		if (this.priBulkTotalSizeInBytes != null) {
			generator.writeKey("pri.bulk.total_size_in_bytes");
			generator.write(this.priBulkTotalSizeInBytes);

		}
		if (this.bulkAvgTime != null) {
			generator.writeKey("bulk.avg_time");
			generator.write(this.bulkAvgTime);

		}
		if (this.priBulkAvgTime != null) {
			generator.writeKey("pri.bulk.avg_time");
			generator.write(this.priBulkAvgTime);

		}
		if (this.bulkAvgSizeInBytes != null) {
			generator.writeKey("bulk.avg_size_in_bytes");
			generator.write(this.bulkAvgSizeInBytes);

		}
		if (this.priBulkAvgSizeInBytes != null) {
			generator.writeKey("pri.bulk.avg_size_in_bytes");
			generator.write(this.priBulkAvgSizeInBytes);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndicesRecord}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndicesRecord> {
		@Nullable
		private String health;

		@Nullable
		private String status;

		@Nullable
		private String index;

		@Nullable
		private String uuid;

		@Nullable
		private String pri;

		@Nullable
		private String rep;

		@Nullable
		private String docsCount;

		@Nullable
		private String docsDeleted;

		@Nullable
		private String creationDate;

		@Nullable
		private String creationDateString;

		@Nullable
		private String storeSize;

		@Nullable
		private String priStoreSize;

		@Nullable
		private String completionSize;

		@Nullable
		private String priCompletionSize;

		@Nullable
		private String fielddataMemorySize;

		@Nullable
		private String priFielddataMemorySize;

		@Nullable
		private String fielddataEvictions;

		@Nullable
		private String priFielddataEvictions;

		@Nullable
		private String queryCacheMemorySize;

		@Nullable
		private String priQueryCacheMemorySize;

		@Nullable
		private String queryCacheEvictions;

		@Nullable
		private String priQueryCacheEvictions;

		@Nullable
		private String requestCacheMemorySize;

		@Nullable
		private String priRequestCacheMemorySize;

		@Nullable
		private String requestCacheEvictions;

		@Nullable
		private String priRequestCacheEvictions;

		@Nullable
		private String requestCacheHitCount;

		@Nullable
		private String priRequestCacheHitCount;

		@Nullable
		private String requestCacheMissCount;

		@Nullable
		private String priRequestCacheMissCount;

		@Nullable
		private String flushTotal;

		@Nullable
		private String priFlushTotal;

		@Nullable
		private String flushTotalTime;

		@Nullable
		private String priFlushTotalTime;

		@Nullable
		private String getCurrent;

		@Nullable
		private String priGetCurrent;

		@Nullable
		private String getTime;

		@Nullable
		private String priGetTime;

		@Nullable
		private String getTotal;

		@Nullable
		private String priGetTotal;

		@Nullable
		private String getExistsTime;

		@Nullable
		private String priGetExistsTime;

		@Nullable
		private String getExistsTotal;

		@Nullable
		private String priGetExistsTotal;

		@Nullable
		private String getMissingTime;

		@Nullable
		private String priGetMissingTime;

		@Nullable
		private String getMissingTotal;

		@Nullable
		private String priGetMissingTotal;

		@Nullable
		private String indexingDeleteCurrent;

		@Nullable
		private String priIndexingDeleteCurrent;

		@Nullable
		private String indexingDeleteTime;

		@Nullable
		private String priIndexingDeleteTime;

		@Nullable
		private String indexingDeleteTotal;

		@Nullable
		private String priIndexingDeleteTotal;

		@Nullable
		private String indexingIndexCurrent;

		@Nullable
		private String priIndexingIndexCurrent;

		@Nullable
		private String indexingIndexTime;

		@Nullable
		private String priIndexingIndexTime;

		@Nullable
		private String indexingIndexTotal;

		@Nullable
		private String priIndexingIndexTotal;

		@Nullable
		private String indexingIndexFailed;

		@Nullable
		private String priIndexingIndexFailed;

		@Nullable
		private String mergesCurrent;

		@Nullable
		private String priMergesCurrent;

		@Nullable
		private String mergesCurrentDocs;

		@Nullable
		private String priMergesCurrentDocs;

		@Nullable
		private String mergesCurrentSize;

		@Nullable
		private String priMergesCurrentSize;

		@Nullable
		private String mergesTotal;

		@Nullable
		private String priMergesTotal;

		@Nullable
		private String mergesTotalDocs;

		@Nullable
		private String priMergesTotalDocs;

		@Nullable
		private String mergesTotalSize;

		@Nullable
		private String priMergesTotalSize;

		@Nullable
		private String mergesTotalTime;

		@Nullable
		private String priMergesTotalTime;

		@Nullable
		private String refreshTotal;

		@Nullable
		private String priRefreshTotal;

		@Nullable
		private String refreshTime;

		@Nullable
		private String priRefreshTime;

		@Nullable
		private String refreshExternalTotal;

		@Nullable
		private String priRefreshExternalTotal;

		@Nullable
		private String refreshExternalTime;

		@Nullable
		private String priRefreshExternalTime;

		@Nullable
		private String refreshListeners;

		@Nullable
		private String priRefreshListeners;

		@Nullable
		private String searchFetchCurrent;

		@Nullable
		private String priSearchFetchCurrent;

		@Nullable
		private String searchFetchTime;

		@Nullable
		private String priSearchFetchTime;

		@Nullable
		private String searchFetchTotal;

		@Nullable
		private String priSearchFetchTotal;

		@Nullable
		private String searchOpenContexts;

		@Nullable
		private String priSearchOpenContexts;

		@Nullable
		private String searchQueryCurrent;

		@Nullable
		private String priSearchQueryCurrent;

		@Nullable
		private String searchQueryTime;

		@Nullable
		private String priSearchQueryTime;

		@Nullable
		private String searchQueryTotal;

		@Nullable
		private String priSearchQueryTotal;

		@Nullable
		private String searchScrollCurrent;

		@Nullable
		private String priSearchScrollCurrent;

		@Nullable
		private String searchScrollTime;

		@Nullable
		private String priSearchScrollTime;

		@Nullable
		private String searchScrollTotal;

		@Nullable
		private String priSearchScrollTotal;

		@Nullable
		private String segmentsCount;

		@Nullable
		private String priSegmentsCount;

		@Nullable
		private String segmentsMemory;

		@Nullable
		private String priSegmentsMemory;

		@Nullable
		private String segmentsIndexWriterMemory;

		@Nullable
		private String priSegmentsIndexWriterMemory;

		@Nullable
		private String segmentsVersionMapMemory;

		@Nullable
		private String priSegmentsVersionMapMemory;

		@Nullable
		private String segmentsFixedBitsetMemory;

		@Nullable
		private String priSegmentsFixedBitsetMemory;

		@Nullable
		private String warmerCurrent;

		@Nullable
		private String priWarmerCurrent;

		@Nullable
		private String warmerTotal;

		@Nullable
		private String priWarmerTotal;

		@Nullable
		private String warmerTotalTime;

		@Nullable
		private String priWarmerTotalTime;

		@Nullable
		private String suggestCurrent;

		@Nullable
		private String priSuggestCurrent;

		@Nullable
		private String suggestTime;

		@Nullable
		private String priSuggestTime;

		@Nullable
		private String suggestTotal;

		@Nullable
		private String priSuggestTotal;

		@Nullable
		private String memoryTotal;

		@Nullable
		private String priMemoryTotal;

		@Nullable
		private String searchThrottled;

		@Nullable
		private String bulkTotalOperations;

		@Nullable
		private String priBulkTotalOperations;

		@Nullable
		private String bulkTotalTime;

		@Nullable
		private String priBulkTotalTime;

		@Nullable
		private String bulkTotalSizeInBytes;

		@Nullable
		private String priBulkTotalSizeInBytes;

		@Nullable
		private String bulkAvgTime;

		@Nullable
		private String priBulkAvgTime;

		@Nullable
		private String bulkAvgSizeInBytes;

		@Nullable
		private String priBulkAvgSizeInBytes;

		/**
		 * current health status
		 * <p>
		 * API name: {@code health}
		 */
		public final Builder health(@Nullable String value) {
			this.health = value;
			return this;
		}

		/**
		 * open/close status
		 * <p>
		 * API name: {@code status}
		 */
		public final Builder status(@Nullable String value) {
			this.status = value;
			return this;
		}

		/**
		 * index name
		 * <p>
		 * API name: {@code index}
		 */
		public final Builder index(@Nullable String value) {
			this.index = value;
			return this;
		}

		/**
		 * index uuid
		 * <p>
		 * API name: {@code uuid}
		 */
		public final Builder uuid(@Nullable String value) {
			this.uuid = value;
			return this;
		}

		/**
		 * number of primary shards
		 * <p>
		 * API name: {@code pri}
		 */
		public final Builder pri(@Nullable String value) {
			this.pri = value;
			return this;
		}

		/**
		 * number of replica shards
		 * <p>
		 * API name: {@code rep}
		 */
		public final Builder rep(@Nullable String value) {
			this.rep = value;
			return this;
		}

		/**
		 * available docs
		 * <p>
		 * API name: {@code docs.count}
		 */
		public final Builder docsCount(@Nullable String value) {
			this.docsCount = value;
			return this;
		}

		/**
		 * deleted docs
		 * <p>
		 * API name: {@code docs.deleted}
		 */
		public final Builder docsDeleted(@Nullable String value) {
			this.docsDeleted = value;
			return this;
		}

		/**
		 * index creation date (millisecond value)
		 * <p>
		 * API name: {@code creation.date}
		 */
		public final Builder creationDate(@Nullable String value) {
			this.creationDate = value;
			return this;
		}

		/**
		 * index creation date (as string)
		 * <p>
		 * API name: {@code creation.date.string}
		 */
		public final Builder creationDateString(@Nullable String value) {
			this.creationDateString = value;
			return this;
		}

		/**
		 * store size of primaries &amp; replicas
		 * <p>
		 * API name: {@code store.size}
		 */
		public final Builder storeSize(@Nullable String value) {
			this.storeSize = value;
			return this;
		}

		/**
		 * store size of primaries
		 * <p>
		 * API name: {@code pri.store.size}
		 */
		public final Builder priStoreSize(@Nullable String value) {
			this.priStoreSize = value;
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
		 * size of completion
		 * <p>
		 * API name: {@code pri.completion.size}
		 */
		public final Builder priCompletionSize(@Nullable String value) {
			this.priCompletionSize = value;
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
		 * used fielddata cache
		 * <p>
		 * API name: {@code pri.fielddata.memory_size}
		 */
		public final Builder priFielddataMemorySize(@Nullable String value) {
			this.priFielddataMemorySize = value;
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
		 * fielddata evictions
		 * <p>
		 * API name: {@code pri.fielddata.evictions}
		 */
		public final Builder priFielddataEvictions(@Nullable String value) {
			this.priFielddataEvictions = value;
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
		 * used query cache
		 * <p>
		 * API name: {@code pri.query_cache.memory_size}
		 */
		public final Builder priQueryCacheMemorySize(@Nullable String value) {
			this.priQueryCacheMemorySize = value;
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
		 * query cache evictions
		 * <p>
		 * API name: {@code pri.query_cache.evictions}
		 */
		public final Builder priQueryCacheEvictions(@Nullable String value) {
			this.priQueryCacheEvictions = value;
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
		 * used request cache
		 * <p>
		 * API name: {@code pri.request_cache.memory_size}
		 */
		public final Builder priRequestCacheMemorySize(@Nullable String value) {
			this.priRequestCacheMemorySize = value;
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
		 * request cache evictions
		 * <p>
		 * API name: {@code pri.request_cache.evictions}
		 */
		public final Builder priRequestCacheEvictions(@Nullable String value) {
			this.priRequestCacheEvictions = value;
			return this;
		}

		/**
		 * request cache hit count
		 * <p>
		 * API name: {@code request_cache.hit_count}
		 */
		public final Builder requestCacheHitCount(@Nullable String value) {
			this.requestCacheHitCount = value;
			return this;
		}

		/**
		 * request cache hit count
		 * <p>
		 * API name: {@code pri.request_cache.hit_count}
		 */
		public final Builder priRequestCacheHitCount(@Nullable String value) {
			this.priRequestCacheHitCount = value;
			return this;
		}

		/**
		 * request cache miss count
		 * <p>
		 * API name: {@code request_cache.miss_count}
		 */
		public final Builder requestCacheMissCount(@Nullable String value) {
			this.requestCacheMissCount = value;
			return this;
		}

		/**
		 * request cache miss count
		 * <p>
		 * API name: {@code pri.request_cache.miss_count}
		 */
		public final Builder priRequestCacheMissCount(@Nullable String value) {
			this.priRequestCacheMissCount = value;
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
		 * number of flushes
		 * <p>
		 * API name: {@code pri.flush.total}
		 */
		public final Builder priFlushTotal(@Nullable String value) {
			this.priFlushTotal = value;
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
		 * time spent in flush
		 * <p>
		 * API name: {@code pri.flush.total_time}
		 */
		public final Builder priFlushTotalTime(@Nullable String value) {
			this.priFlushTotalTime = value;
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
		 * number of current get ops
		 * <p>
		 * API name: {@code pri.get.current}
		 */
		public final Builder priGetCurrent(@Nullable String value) {
			this.priGetCurrent = value;
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
		 * time spent in get
		 * <p>
		 * API name: {@code pri.get.time}
		 */
		public final Builder priGetTime(@Nullable String value) {
			this.priGetTime = value;
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
		 * number of get ops
		 * <p>
		 * API name: {@code pri.get.total}
		 */
		public final Builder priGetTotal(@Nullable String value) {
			this.priGetTotal = value;
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
		 * time spent in successful gets
		 * <p>
		 * API name: {@code pri.get.exists_time}
		 */
		public final Builder priGetExistsTime(@Nullable String value) {
			this.priGetExistsTime = value;
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
		 * number of successful gets
		 * <p>
		 * API name: {@code pri.get.exists_total}
		 */
		public final Builder priGetExistsTotal(@Nullable String value) {
			this.priGetExistsTotal = value;
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
		 * time spent in failed gets
		 * <p>
		 * API name: {@code pri.get.missing_time}
		 */
		public final Builder priGetMissingTime(@Nullable String value) {
			this.priGetMissingTime = value;
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
		 * number of failed gets
		 * <p>
		 * API name: {@code pri.get.missing_total}
		 */
		public final Builder priGetMissingTotal(@Nullable String value) {
			this.priGetMissingTotal = value;
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
		 * number of current deletions
		 * <p>
		 * API name: {@code pri.indexing.delete_current}
		 */
		public final Builder priIndexingDeleteCurrent(@Nullable String value) {
			this.priIndexingDeleteCurrent = value;
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
		 * time spent in deletions
		 * <p>
		 * API name: {@code pri.indexing.delete_time}
		 */
		public final Builder priIndexingDeleteTime(@Nullable String value) {
			this.priIndexingDeleteTime = value;
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
		 * number of delete ops
		 * <p>
		 * API name: {@code pri.indexing.delete_total}
		 */
		public final Builder priIndexingDeleteTotal(@Nullable String value) {
			this.priIndexingDeleteTotal = value;
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
		 * number of current indexing ops
		 * <p>
		 * API name: {@code pri.indexing.index_current}
		 */
		public final Builder priIndexingIndexCurrent(@Nullable String value) {
			this.priIndexingIndexCurrent = value;
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
		 * time spent in indexing
		 * <p>
		 * API name: {@code pri.indexing.index_time}
		 */
		public final Builder priIndexingIndexTime(@Nullable String value) {
			this.priIndexingIndexTime = value;
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
		 * number of indexing ops
		 * <p>
		 * API name: {@code pri.indexing.index_total}
		 */
		public final Builder priIndexingIndexTotal(@Nullable String value) {
			this.priIndexingIndexTotal = value;
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
		 * number of failed indexing ops
		 * <p>
		 * API name: {@code pri.indexing.index_failed}
		 */
		public final Builder priIndexingIndexFailed(@Nullable String value) {
			this.priIndexingIndexFailed = value;
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
		 * number of current merges
		 * <p>
		 * API name: {@code pri.merges.current}
		 */
		public final Builder priMergesCurrent(@Nullable String value) {
			this.priMergesCurrent = value;
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
		 * number of current merging docs
		 * <p>
		 * API name: {@code pri.merges.current_docs}
		 */
		public final Builder priMergesCurrentDocs(@Nullable String value) {
			this.priMergesCurrentDocs = value;
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
		 * size of current merges
		 * <p>
		 * API name: {@code pri.merges.current_size}
		 */
		public final Builder priMergesCurrentSize(@Nullable String value) {
			this.priMergesCurrentSize = value;
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
		 * number of completed merge ops
		 * <p>
		 * API name: {@code pri.merges.total}
		 */
		public final Builder priMergesTotal(@Nullable String value) {
			this.priMergesTotal = value;
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
		 * docs merged
		 * <p>
		 * API name: {@code pri.merges.total_docs}
		 */
		public final Builder priMergesTotalDocs(@Nullable String value) {
			this.priMergesTotalDocs = value;
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
		 * size merged
		 * <p>
		 * API name: {@code pri.merges.total_size}
		 */
		public final Builder priMergesTotalSize(@Nullable String value) {
			this.priMergesTotalSize = value;
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
		 * time spent in merges
		 * <p>
		 * API name: {@code pri.merges.total_time}
		 */
		public final Builder priMergesTotalTime(@Nullable String value) {
			this.priMergesTotalTime = value;
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
		 * total refreshes
		 * <p>
		 * API name: {@code pri.refresh.total}
		 */
		public final Builder priRefreshTotal(@Nullable String value) {
			this.priRefreshTotal = value;
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
		 * time spent in refreshes
		 * <p>
		 * API name: {@code pri.refresh.time}
		 */
		public final Builder priRefreshTime(@Nullable String value) {
			this.priRefreshTime = value;
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
		 * total external refreshes
		 * <p>
		 * API name: {@code pri.refresh.external_total}
		 */
		public final Builder priRefreshExternalTotal(@Nullable String value) {
			this.priRefreshExternalTotal = value;
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
		 * time spent in external refreshes
		 * <p>
		 * API name: {@code pri.refresh.external_time}
		 */
		public final Builder priRefreshExternalTime(@Nullable String value) {
			this.priRefreshExternalTime = value;
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
		 * number of pending refresh listeners
		 * <p>
		 * API name: {@code pri.refresh.listeners}
		 */
		public final Builder priRefreshListeners(@Nullable String value) {
			this.priRefreshListeners = value;
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
		 * current fetch phase ops
		 * <p>
		 * API name: {@code pri.search.fetch_current}
		 */
		public final Builder priSearchFetchCurrent(@Nullable String value) {
			this.priSearchFetchCurrent = value;
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
		 * time spent in fetch phase
		 * <p>
		 * API name: {@code pri.search.fetch_time}
		 */
		public final Builder priSearchFetchTime(@Nullable String value) {
			this.priSearchFetchTime = value;
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
		 * total fetch ops
		 * <p>
		 * API name: {@code pri.search.fetch_total}
		 */
		public final Builder priSearchFetchTotal(@Nullable String value) {
			this.priSearchFetchTotal = value;
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
		 * open search contexts
		 * <p>
		 * API name: {@code pri.search.open_contexts}
		 */
		public final Builder priSearchOpenContexts(@Nullable String value) {
			this.priSearchOpenContexts = value;
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
		 * current query phase ops
		 * <p>
		 * API name: {@code pri.search.query_current}
		 */
		public final Builder priSearchQueryCurrent(@Nullable String value) {
			this.priSearchQueryCurrent = value;
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
		 * time spent in query phase
		 * <p>
		 * API name: {@code pri.search.query_time}
		 */
		public final Builder priSearchQueryTime(@Nullable String value) {
			this.priSearchQueryTime = value;
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
		 * total query phase ops
		 * <p>
		 * API name: {@code pri.search.query_total}
		 */
		public final Builder priSearchQueryTotal(@Nullable String value) {
			this.priSearchQueryTotal = value;
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
		 * open scroll contexts
		 * <p>
		 * API name: {@code pri.search.scroll_current}
		 */
		public final Builder priSearchScrollCurrent(@Nullable String value) {
			this.priSearchScrollCurrent = value;
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
		 * time scroll contexts held open
		 * <p>
		 * API name: {@code pri.search.scroll_time}
		 */
		public final Builder priSearchScrollTime(@Nullable String value) {
			this.priSearchScrollTime = value;
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
		 * completed scroll contexts
		 * <p>
		 * API name: {@code pri.search.scroll_total}
		 */
		public final Builder priSearchScrollTotal(@Nullable String value) {
			this.priSearchScrollTotal = value;
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
		 * number of segments
		 * <p>
		 * API name: {@code pri.segments.count}
		 */
		public final Builder priSegmentsCount(@Nullable String value) {
			this.priSegmentsCount = value;
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
		 * memory used by segments
		 * <p>
		 * API name: {@code pri.segments.memory}
		 */
		public final Builder priSegmentsMemory(@Nullable String value) {
			this.priSegmentsMemory = value;
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
		 * memory used by index writer
		 * <p>
		 * API name: {@code pri.segments.index_writer_memory}
		 */
		public final Builder priSegmentsIndexWriterMemory(@Nullable String value) {
			this.priSegmentsIndexWriterMemory = value;
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
		 * memory used by version map
		 * <p>
		 * API name: {@code pri.segments.version_map_memory}
		 */
		public final Builder priSegmentsVersionMapMemory(@Nullable String value) {
			this.priSegmentsVersionMapMemory = value;
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
		 * memory used by fixed bit sets for nested object field types and export type
		 * filters for types referred in _parent fields
		 * <p>
		 * API name: {@code pri.segments.fixed_bitset_memory}
		 */
		public final Builder priSegmentsFixedBitsetMemory(@Nullable String value) {
			this.priSegmentsFixedBitsetMemory = value;
			return this;
		}

		/**
		 * current warmer ops
		 * <p>
		 * API name: {@code warmer.current}
		 */
		public final Builder warmerCurrent(@Nullable String value) {
			this.warmerCurrent = value;
			return this;
		}

		/**
		 * current warmer ops
		 * <p>
		 * API name: {@code pri.warmer.current}
		 */
		public final Builder priWarmerCurrent(@Nullable String value) {
			this.priWarmerCurrent = value;
			return this;
		}

		/**
		 * total warmer ops
		 * <p>
		 * API name: {@code warmer.total}
		 */
		public final Builder warmerTotal(@Nullable String value) {
			this.warmerTotal = value;
			return this;
		}

		/**
		 * total warmer ops
		 * <p>
		 * API name: {@code pri.warmer.total}
		 */
		public final Builder priWarmerTotal(@Nullable String value) {
			this.priWarmerTotal = value;
			return this;
		}

		/**
		 * time spent in warmers
		 * <p>
		 * API name: {@code warmer.total_time}
		 */
		public final Builder warmerTotalTime(@Nullable String value) {
			this.warmerTotalTime = value;
			return this;
		}

		/**
		 * time spent in warmers
		 * <p>
		 * API name: {@code pri.warmer.total_time}
		 */
		public final Builder priWarmerTotalTime(@Nullable String value) {
			this.priWarmerTotalTime = value;
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
		 * number of current suggest ops
		 * <p>
		 * API name: {@code pri.suggest.current}
		 */
		public final Builder priSuggestCurrent(@Nullable String value) {
			this.priSuggestCurrent = value;
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
		 * time spend in suggest
		 * <p>
		 * API name: {@code pri.suggest.time}
		 */
		public final Builder priSuggestTime(@Nullable String value) {
			this.priSuggestTime = value;
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
		 * number of suggest ops
		 * <p>
		 * API name: {@code pri.suggest.total}
		 */
		public final Builder priSuggestTotal(@Nullable String value) {
			this.priSuggestTotal = value;
			return this;
		}

		/**
		 * total used memory
		 * <p>
		 * API name: {@code memory.total}
		 */
		public final Builder memoryTotal(@Nullable String value) {
			this.memoryTotal = value;
			return this;
		}

		/**
		 * total user memory
		 * <p>
		 * API name: {@code pri.memory.total}
		 */
		public final Builder priMemoryTotal(@Nullable String value) {
			this.priMemoryTotal = value;
			return this;
		}

		/**
		 * indicates if the index is search throttled
		 * <p>
		 * API name: {@code search.throttled}
		 */
		public final Builder searchThrottled(@Nullable String value) {
			this.searchThrottled = value;
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
		 * number of bulk shard ops
		 * <p>
		 * API name: {@code pri.bulk.total_operations}
		 */
		public final Builder priBulkTotalOperations(@Nullable String value) {
			this.priBulkTotalOperations = value;
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
		 * time spend in shard bulk
		 * <p>
		 * API name: {@code pri.bulk.total_time}
		 */
		public final Builder priBulkTotalTime(@Nullable String value) {
			this.priBulkTotalTime = value;
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
		 * total size in bytes of shard bulk
		 * <p>
		 * API name: {@code pri.bulk.total_size_in_bytes}
		 */
		public final Builder priBulkTotalSizeInBytes(@Nullable String value) {
			this.priBulkTotalSizeInBytes = value;
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
		 * average time spend in shard bulk
		 * <p>
		 * API name: {@code pri.bulk.avg_time}
		 */
		public final Builder priBulkAvgTime(@Nullable String value) {
			this.priBulkAvgTime = value;
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
		 * average size in bytes of shard bulk
		 * <p>
		 * API name: {@code pri.bulk.avg_size_in_bytes}
		 */
		public final Builder priBulkAvgSizeInBytes(@Nullable String value) {
			this.priBulkAvgSizeInBytes = value;
			return this;
		}

		/**
		 * Builds a {@link IndicesRecord}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndicesRecord build() {
			_checkSingleUse();

			return new IndicesRecord(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndicesRecord}
	 */
	public static final JsonpDeserializer<IndicesRecord> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndicesRecord::setupIndicesRecordDeserializer);

	protected static void setupIndicesRecordDeserializer(ObjectDeserializer<IndicesRecord.Builder> op) {

		op.add(Builder::health, JsonpDeserializer.stringDeserializer(), "health", "h");
		op.add(Builder::status, JsonpDeserializer.stringDeserializer(), "status", "s");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index", "i", "idx");
		op.add(Builder::uuid, JsonpDeserializer.stringDeserializer(), "uuid", "id");
		op.add(Builder::pri, JsonpDeserializer.stringDeserializer(), "pri", "p", "shards.primary", "shardsPrimary");
		op.add(Builder::rep, JsonpDeserializer.stringDeserializer(), "rep", "r", "shards.replica", "shardsReplica");
		op.add(Builder::docsCount, JsonpDeserializer.stringDeserializer(), "docs.count", "dc", "docsCount");
		op.add(Builder::docsDeleted, JsonpDeserializer.stringDeserializer(), "docs.deleted", "dd", "docsDeleted");
		op.add(Builder::creationDate, JsonpDeserializer.stringDeserializer(), "creation.date", "cd");
		op.add(Builder::creationDateString, JsonpDeserializer.stringDeserializer(), "creation.date.string", "cds");
		op.add(Builder::storeSize, JsonpDeserializer.stringDeserializer(), "store.size", "ss", "storeSize");
		op.add(Builder::priStoreSize, JsonpDeserializer.stringDeserializer(), "pri.store.size");
		op.add(Builder::completionSize, JsonpDeserializer.stringDeserializer(), "completion.size", "cs",
				"completionSize");
		op.add(Builder::priCompletionSize, JsonpDeserializer.stringDeserializer(), "pri.completion.size");
		op.add(Builder::fielddataMemorySize, JsonpDeserializer.stringDeserializer(), "fielddata.memory_size", "fm",
				"fielddataMemory");
		op.add(Builder::priFielddataMemorySize, JsonpDeserializer.stringDeserializer(), "pri.fielddata.memory_size");
		op.add(Builder::fielddataEvictions, JsonpDeserializer.stringDeserializer(), "fielddata.evictions", "fe",
				"fielddataEvictions");
		op.add(Builder::priFielddataEvictions, JsonpDeserializer.stringDeserializer(), "pri.fielddata.evictions");
		op.add(Builder::queryCacheMemorySize, JsonpDeserializer.stringDeserializer(), "query_cache.memory_size", "qcm",
				"queryCacheMemory");
		op.add(Builder::priQueryCacheMemorySize, JsonpDeserializer.stringDeserializer(), "pri.query_cache.memory_size");
		op.add(Builder::queryCacheEvictions, JsonpDeserializer.stringDeserializer(), "query_cache.evictions", "qce",
				"queryCacheEvictions");
		op.add(Builder::priQueryCacheEvictions, JsonpDeserializer.stringDeserializer(), "pri.query_cache.evictions");
		op.add(Builder::requestCacheMemorySize, JsonpDeserializer.stringDeserializer(), "request_cache.memory_size",
				"rcm", "requestCacheMemory");
		op.add(Builder::priRequestCacheMemorySize, JsonpDeserializer.stringDeserializer(),
				"pri.request_cache.memory_size");
		op.add(Builder::requestCacheEvictions, JsonpDeserializer.stringDeserializer(), "request_cache.evictions", "rce",
				"requestCacheEvictions");
		op.add(Builder::priRequestCacheEvictions, JsonpDeserializer.stringDeserializer(),
				"pri.request_cache.evictions");
		op.add(Builder::requestCacheHitCount, JsonpDeserializer.stringDeserializer(), "request_cache.hit_count", "rchc",
				"requestCacheHitCount");
		op.add(Builder::priRequestCacheHitCount, JsonpDeserializer.stringDeserializer(), "pri.request_cache.hit_count");
		op.add(Builder::requestCacheMissCount, JsonpDeserializer.stringDeserializer(), "request_cache.miss_count",
				"rcmc", "requestCacheMissCount");
		op.add(Builder::priRequestCacheMissCount, JsonpDeserializer.stringDeserializer(),
				"pri.request_cache.miss_count");
		op.add(Builder::flushTotal, JsonpDeserializer.stringDeserializer(), "flush.total", "ft", "flushTotal");
		op.add(Builder::priFlushTotal, JsonpDeserializer.stringDeserializer(), "pri.flush.total");
		op.add(Builder::flushTotalTime, JsonpDeserializer.stringDeserializer(), "flush.total_time", "ftt",
				"flushTotalTime");
		op.add(Builder::priFlushTotalTime, JsonpDeserializer.stringDeserializer(), "pri.flush.total_time");
		op.add(Builder::getCurrent, JsonpDeserializer.stringDeserializer(), "get.current", "gc", "getCurrent");
		op.add(Builder::priGetCurrent, JsonpDeserializer.stringDeserializer(), "pri.get.current");
		op.add(Builder::getTime, JsonpDeserializer.stringDeserializer(), "get.time", "gti", "getTime");
		op.add(Builder::priGetTime, JsonpDeserializer.stringDeserializer(), "pri.get.time");
		op.add(Builder::getTotal, JsonpDeserializer.stringDeserializer(), "get.total", "gto", "getTotal");
		op.add(Builder::priGetTotal, JsonpDeserializer.stringDeserializer(), "pri.get.total");
		op.add(Builder::getExistsTime, JsonpDeserializer.stringDeserializer(), "get.exists_time", "geti",
				"getExistsTime");
		op.add(Builder::priGetExistsTime, JsonpDeserializer.stringDeserializer(), "pri.get.exists_time");
		op.add(Builder::getExistsTotal, JsonpDeserializer.stringDeserializer(), "get.exists_total", "geto",
				"getExistsTotal");
		op.add(Builder::priGetExistsTotal, JsonpDeserializer.stringDeserializer(), "pri.get.exists_total");
		op.add(Builder::getMissingTime, JsonpDeserializer.stringDeserializer(), "get.missing_time", "gmti",
				"getMissingTime");
		op.add(Builder::priGetMissingTime, JsonpDeserializer.stringDeserializer(), "pri.get.missing_time");
		op.add(Builder::getMissingTotal, JsonpDeserializer.stringDeserializer(), "get.missing_total", "gmto",
				"getMissingTotal");
		op.add(Builder::priGetMissingTotal, JsonpDeserializer.stringDeserializer(), "pri.get.missing_total");
		op.add(Builder::indexingDeleteCurrent, JsonpDeserializer.stringDeserializer(), "indexing.delete_current", "idc",
				"indexingDeleteCurrent");
		op.add(Builder::priIndexingDeleteCurrent, JsonpDeserializer.stringDeserializer(),
				"pri.indexing.delete_current");
		op.add(Builder::indexingDeleteTime, JsonpDeserializer.stringDeserializer(), "indexing.delete_time", "idti",
				"indexingDeleteTime");
		op.add(Builder::priIndexingDeleteTime, JsonpDeserializer.stringDeserializer(), "pri.indexing.delete_time");
		op.add(Builder::indexingDeleteTotal, JsonpDeserializer.stringDeserializer(), "indexing.delete_total", "idto",
				"indexingDeleteTotal");
		op.add(Builder::priIndexingDeleteTotal, JsonpDeserializer.stringDeserializer(), "pri.indexing.delete_total");
		op.add(Builder::indexingIndexCurrent, JsonpDeserializer.stringDeserializer(), "indexing.index_current", "iic",
				"indexingIndexCurrent");
		op.add(Builder::priIndexingIndexCurrent, JsonpDeserializer.stringDeserializer(), "pri.indexing.index_current");
		op.add(Builder::indexingIndexTime, JsonpDeserializer.stringDeserializer(), "indexing.index_time", "iiti",
				"indexingIndexTime");
		op.add(Builder::priIndexingIndexTime, JsonpDeserializer.stringDeserializer(), "pri.indexing.index_time");
		op.add(Builder::indexingIndexTotal, JsonpDeserializer.stringDeserializer(), "indexing.index_total", "iito",
				"indexingIndexTotal");
		op.add(Builder::priIndexingIndexTotal, JsonpDeserializer.stringDeserializer(), "pri.indexing.index_total");
		op.add(Builder::indexingIndexFailed, JsonpDeserializer.stringDeserializer(), "indexing.index_failed", "iif",
				"indexingIndexFailed");
		op.add(Builder::priIndexingIndexFailed, JsonpDeserializer.stringDeserializer(), "pri.indexing.index_failed");
		op.add(Builder::mergesCurrent, JsonpDeserializer.stringDeserializer(), "merges.current", "mc", "mergesCurrent");
		op.add(Builder::priMergesCurrent, JsonpDeserializer.stringDeserializer(), "pri.merges.current");
		op.add(Builder::mergesCurrentDocs, JsonpDeserializer.stringDeserializer(), "merges.current_docs", "mcd",
				"mergesCurrentDocs");
		op.add(Builder::priMergesCurrentDocs, JsonpDeserializer.stringDeserializer(), "pri.merges.current_docs");
		op.add(Builder::mergesCurrentSize, JsonpDeserializer.stringDeserializer(), "merges.current_size", "mcs",
				"mergesCurrentSize");
		op.add(Builder::priMergesCurrentSize, JsonpDeserializer.stringDeserializer(), "pri.merges.current_size");
		op.add(Builder::mergesTotal, JsonpDeserializer.stringDeserializer(), "merges.total", "mt", "mergesTotal");
		op.add(Builder::priMergesTotal, JsonpDeserializer.stringDeserializer(), "pri.merges.total");
		op.add(Builder::mergesTotalDocs, JsonpDeserializer.stringDeserializer(), "merges.total_docs", "mtd",
				"mergesTotalDocs");
		op.add(Builder::priMergesTotalDocs, JsonpDeserializer.stringDeserializer(), "pri.merges.total_docs");
		op.add(Builder::mergesTotalSize, JsonpDeserializer.stringDeserializer(), "merges.total_size", "mts",
				"mergesTotalSize");
		op.add(Builder::priMergesTotalSize, JsonpDeserializer.stringDeserializer(), "pri.merges.total_size");
		op.add(Builder::mergesTotalTime, JsonpDeserializer.stringDeserializer(), "merges.total_time", "mtt",
				"mergesTotalTime");
		op.add(Builder::priMergesTotalTime, JsonpDeserializer.stringDeserializer(), "pri.merges.total_time");
		op.add(Builder::refreshTotal, JsonpDeserializer.stringDeserializer(), "refresh.total", "rto", "refreshTotal");
		op.add(Builder::priRefreshTotal, JsonpDeserializer.stringDeserializer(), "pri.refresh.total");
		op.add(Builder::refreshTime, JsonpDeserializer.stringDeserializer(), "refresh.time", "rti", "refreshTime");
		op.add(Builder::priRefreshTime, JsonpDeserializer.stringDeserializer(), "pri.refresh.time");
		op.add(Builder::refreshExternalTotal, JsonpDeserializer.stringDeserializer(), "refresh.external_total", "reto");
		op.add(Builder::priRefreshExternalTotal, JsonpDeserializer.stringDeserializer(), "pri.refresh.external_total");
		op.add(Builder::refreshExternalTime, JsonpDeserializer.stringDeserializer(), "refresh.external_time", "reti");
		op.add(Builder::priRefreshExternalTime, JsonpDeserializer.stringDeserializer(), "pri.refresh.external_time");
		op.add(Builder::refreshListeners, JsonpDeserializer.stringDeserializer(), "refresh.listeners", "rli",
				"refreshListeners");
		op.add(Builder::priRefreshListeners, JsonpDeserializer.stringDeserializer(), "pri.refresh.listeners");
		op.add(Builder::searchFetchCurrent, JsonpDeserializer.stringDeserializer(), "search.fetch_current", "sfc",
				"searchFetchCurrent");
		op.add(Builder::priSearchFetchCurrent, JsonpDeserializer.stringDeserializer(), "pri.search.fetch_current");
		op.add(Builder::searchFetchTime, JsonpDeserializer.stringDeserializer(), "search.fetch_time", "sfti",
				"searchFetchTime");
		op.add(Builder::priSearchFetchTime, JsonpDeserializer.stringDeserializer(), "pri.search.fetch_time");
		op.add(Builder::searchFetchTotal, JsonpDeserializer.stringDeserializer(), "search.fetch_total", "sfto",
				"searchFetchTotal");
		op.add(Builder::priSearchFetchTotal, JsonpDeserializer.stringDeserializer(), "pri.search.fetch_total");
		op.add(Builder::searchOpenContexts, JsonpDeserializer.stringDeserializer(), "search.open_contexts", "so",
				"searchOpenContexts");
		op.add(Builder::priSearchOpenContexts, JsonpDeserializer.stringDeserializer(), "pri.search.open_contexts");
		op.add(Builder::searchQueryCurrent, JsonpDeserializer.stringDeserializer(), "search.query_current", "sqc",
				"searchQueryCurrent");
		op.add(Builder::priSearchQueryCurrent, JsonpDeserializer.stringDeserializer(), "pri.search.query_current");
		op.add(Builder::searchQueryTime, JsonpDeserializer.stringDeserializer(), "search.query_time", "sqti",
				"searchQueryTime");
		op.add(Builder::priSearchQueryTime, JsonpDeserializer.stringDeserializer(), "pri.search.query_time");
		op.add(Builder::searchQueryTotal, JsonpDeserializer.stringDeserializer(), "search.query_total", "sqto",
				"searchQueryTotal");
		op.add(Builder::priSearchQueryTotal, JsonpDeserializer.stringDeserializer(), "pri.search.query_total");
		op.add(Builder::searchScrollCurrent, JsonpDeserializer.stringDeserializer(), "search.scroll_current", "scc",
				"searchScrollCurrent");
		op.add(Builder::priSearchScrollCurrent, JsonpDeserializer.stringDeserializer(), "pri.search.scroll_current");
		op.add(Builder::searchScrollTime, JsonpDeserializer.stringDeserializer(), "search.scroll_time", "scti",
				"searchScrollTime");
		op.add(Builder::priSearchScrollTime, JsonpDeserializer.stringDeserializer(), "pri.search.scroll_time");
		op.add(Builder::searchScrollTotal, JsonpDeserializer.stringDeserializer(), "search.scroll_total", "scto",
				"searchScrollTotal");
		op.add(Builder::priSearchScrollTotal, JsonpDeserializer.stringDeserializer(), "pri.search.scroll_total");
		op.add(Builder::segmentsCount, JsonpDeserializer.stringDeserializer(), "segments.count", "sc", "segmentsCount");
		op.add(Builder::priSegmentsCount, JsonpDeserializer.stringDeserializer(), "pri.segments.count");
		op.add(Builder::segmentsMemory, JsonpDeserializer.stringDeserializer(), "segments.memory", "sm",
				"segmentsMemory");
		op.add(Builder::priSegmentsMemory, JsonpDeserializer.stringDeserializer(), "pri.segments.memory");
		op.add(Builder::segmentsIndexWriterMemory, JsonpDeserializer.stringDeserializer(),
				"segments.index_writer_memory", "siwm", "segmentsIndexWriterMemory");
		op.add(Builder::priSegmentsIndexWriterMemory, JsonpDeserializer.stringDeserializer(),
				"pri.segments.index_writer_memory");
		op.add(Builder::segmentsVersionMapMemory, JsonpDeserializer.stringDeserializer(), "segments.version_map_memory",
				"svmm", "segmentsVersionMapMemory");
		op.add(Builder::priSegmentsVersionMapMemory, JsonpDeserializer.stringDeserializer(),
				"pri.segments.version_map_memory");
		op.add(Builder::segmentsFixedBitsetMemory, JsonpDeserializer.stringDeserializer(),
				"segments.fixed_bitset_memory", "sfbm", "fixedBitsetMemory");
		op.add(Builder::priSegmentsFixedBitsetMemory, JsonpDeserializer.stringDeserializer(),
				"pri.segments.fixed_bitset_memory");
		op.add(Builder::warmerCurrent, JsonpDeserializer.stringDeserializer(), "warmer.current", "wc", "warmerCurrent");
		op.add(Builder::priWarmerCurrent, JsonpDeserializer.stringDeserializer(), "pri.warmer.current");
		op.add(Builder::warmerTotal, JsonpDeserializer.stringDeserializer(), "warmer.total", "wto", "warmerTotal");
		op.add(Builder::priWarmerTotal, JsonpDeserializer.stringDeserializer(), "pri.warmer.total");
		op.add(Builder::warmerTotalTime, JsonpDeserializer.stringDeserializer(), "warmer.total_time", "wtt",
				"warmerTotalTime");
		op.add(Builder::priWarmerTotalTime, JsonpDeserializer.stringDeserializer(), "pri.warmer.total_time");
		op.add(Builder::suggestCurrent, JsonpDeserializer.stringDeserializer(), "suggest.current", "suc",
				"suggestCurrent");
		op.add(Builder::priSuggestCurrent, JsonpDeserializer.stringDeserializer(), "pri.suggest.current");
		op.add(Builder::suggestTime, JsonpDeserializer.stringDeserializer(), "suggest.time", "suti", "suggestTime");
		op.add(Builder::priSuggestTime, JsonpDeserializer.stringDeserializer(), "pri.suggest.time");
		op.add(Builder::suggestTotal, JsonpDeserializer.stringDeserializer(), "suggest.total", "suto", "suggestTotal");
		op.add(Builder::priSuggestTotal, JsonpDeserializer.stringDeserializer(), "pri.suggest.total");
		op.add(Builder::memoryTotal, JsonpDeserializer.stringDeserializer(), "memory.total", "tm", "memoryTotal");
		op.add(Builder::priMemoryTotal, JsonpDeserializer.stringDeserializer(), "pri.memory.total");
		op.add(Builder::searchThrottled, JsonpDeserializer.stringDeserializer(), "search.throttled", "sth");
		op.add(Builder::bulkTotalOperations, JsonpDeserializer.stringDeserializer(), "bulk.total_operations", "bto",
				"bulkTotalOperation");
		op.add(Builder::priBulkTotalOperations, JsonpDeserializer.stringDeserializer(), "pri.bulk.total_operations");
		op.add(Builder::bulkTotalTime, JsonpDeserializer.stringDeserializer(), "bulk.total_time", "btti",
				"bulkTotalTime");
		op.add(Builder::priBulkTotalTime, JsonpDeserializer.stringDeserializer(), "pri.bulk.total_time");
		op.add(Builder::bulkTotalSizeInBytes, JsonpDeserializer.stringDeserializer(), "bulk.total_size_in_bytes",
				"btsi", "bulkTotalSizeInBytes");
		op.add(Builder::priBulkTotalSizeInBytes, JsonpDeserializer.stringDeserializer(),
				"pri.bulk.total_size_in_bytes");
		op.add(Builder::bulkAvgTime, JsonpDeserializer.stringDeserializer(), "bulk.avg_time", "bati", "bulkAvgTime");
		op.add(Builder::priBulkAvgTime, JsonpDeserializer.stringDeserializer(), "pri.bulk.avg_time");
		op.add(Builder::bulkAvgSizeInBytes, JsonpDeserializer.stringDeserializer(), "bulk.avg_size_in_bytes", "basi",
				"bulkAvgSizeInBytes");
		op.add(Builder::priBulkAvgSizeInBytes, JsonpDeserializer.stringDeserializer(), "pri.bulk.avg_size_in_bytes");

	}

}
