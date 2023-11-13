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

package org.opensearch.client.opensearch.indices.stats;

import org.opensearch.client.opensearch._types.BulkStats;
import org.opensearch.client.opensearch._types.CompletionStats;
import org.opensearch.client.opensearch._types.DocStats;
import org.opensearch.client.opensearch._types.FielddataStats;
import org.opensearch.client.opensearch._types.FlushStats;
import org.opensearch.client.opensearch._types.GetStats;
import org.opensearch.client.opensearch._types.IndexingStats;
import org.opensearch.client.opensearch._types.MergesStats;
import org.opensearch.client.opensearch._types.RecoveryStats;
import org.opensearch.client.opensearch._types.RefreshStats;
import org.opensearch.client.opensearch._types.RequestCacheStats;
import org.opensearch.client.opensearch._types.SearchStats;
import org.opensearch.client.opensearch._types.SegmentsStats;
import org.opensearch.client.opensearch._types.StoreStats;
import org.opensearch.client.opensearch._types.TranslogStats;
import org.opensearch.client.opensearch._types.WarmerStats;
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
import javax.annotation.Nullable;

// typedef: indices.stats.ShardStats


@JsonpDeserializable
public class ShardStats implements JsonpSerializable {
	private final ShardCommit commit;

	private final CompletionStats completion;

	private final DocStats docs;

	private final FielddataStats fielddata;

	private final FlushStats flush;

	private final GetStats get;

	private final IndexingStats indexing;

	private final MergesStats merges;

	private final ShardPath shardPath;

	private final ShardQueryCache queryCache;

	private final RecoveryStats recovery;

	private final RefreshStats refresh;

	private final RequestCacheStats requestCache;

	private final ShardRetentionLeases retentionLeases;

	private final ShardRouting routing;

	private final SearchStats search;

	private final SegmentsStats segments;

	private final ShardSequenceNumber seqNo;

	private final StoreStats store;

	private final TranslogStats translog;

	private final WarmerStats warmer;

	@Nullable
	private final BulkStats bulk;

	private final ShardsTotalStats shards;

	// ---------------------------------------------------------------------------------------------

	private ShardStats(Builder builder) {

		this.commit = ApiTypeHelper.requireNonNull(builder.commit, this, "commit");
		this.completion = ApiTypeHelper.requireNonNull(builder.completion, this, "completion");
		this.docs = ApiTypeHelper.requireNonNull(builder.docs, this, "docs");
		this.fielddata = ApiTypeHelper.requireNonNull(builder.fielddata, this, "fielddata");
		this.flush = ApiTypeHelper.requireNonNull(builder.flush, this, "flush");
		this.get = ApiTypeHelper.requireNonNull(builder.get, this, "get");
		this.indexing = ApiTypeHelper.requireNonNull(builder.indexing, this, "indexing");
		this.merges = ApiTypeHelper.requireNonNull(builder.merges, this, "merges");
		this.shardPath = ApiTypeHelper.requireNonNull(builder.shardPath, this, "shardPath");
		this.queryCache = ApiTypeHelper.requireNonNull(builder.queryCache, this, "queryCache");
		this.recovery = ApiTypeHelper.requireNonNull(builder.recovery, this, "recovery");
		this.refresh = ApiTypeHelper.requireNonNull(builder.refresh, this, "refresh");
		this.requestCache = ApiTypeHelper.requireNonNull(builder.requestCache, this, "requestCache");
		this.retentionLeases = ApiTypeHelper.requireNonNull(builder.retentionLeases, this, "retentionLeases");
		this.routing = ApiTypeHelper.requireNonNull(builder.routing, this, "routing");
		this.search = ApiTypeHelper.requireNonNull(builder.search, this, "search");
		this.segments = ApiTypeHelper.requireNonNull(builder.segments, this, "segments");
		this.seqNo = ApiTypeHelper.requireNonNull(builder.seqNo, this, "seqNo");
		this.store = ApiTypeHelper.requireNonNull(builder.store, this, "store");
		this.translog = ApiTypeHelper.requireNonNull(builder.translog, this, "translog");
		this.warmer = ApiTypeHelper.requireNonNull(builder.warmer, this, "warmer");
		this.bulk = builder.bulk;
		this.shards = ApiTypeHelper.requireNonNull(builder.shards, this, "shards");

	}

	public static ShardStats of(Function<Builder, ObjectBuilder<ShardStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code commit}
	 */
	public final ShardCommit commit() {
		return this.commit;
	}

	/**
	 * Required - API name: {@code completion}
	 */
	public final CompletionStats completion() {
		return this.completion;
	}

	/**
	 * Required - API name: {@code docs}
	 */
	public final DocStats docs() {
		return this.docs;
	}

	/**
	 * Required - API name: {@code fielddata}
	 */
	public final FielddataStats fielddata() {
		return this.fielddata;
	}

	/**
	 * Required - API name: {@code flush}
	 */
	public final FlushStats flush() {
		return this.flush;
	}

	/**
	 * Required - API name: {@code get}
	 */
	public final GetStats get() {
		return this.get;
	}

	/**
	 * Required - API name: {@code indexing}
	 */
	public final IndexingStats indexing() {
		return this.indexing;
	}

	/**
	 * Required - API name: {@code merges}
	 */
	public final MergesStats merges() {
		return this.merges;
	}

	/**
	 * Required - API name: {@code shard_path}
	 */
	public final ShardPath shardPath() {
		return this.shardPath;
	}

	/**
	 * Required - API name: {@code query_cache}
	 */
	public final ShardQueryCache queryCache() {
		return this.queryCache;
	}

	/**
	 * Required - API name: {@code recovery}
	 */
	public final RecoveryStats recovery() {
		return this.recovery;
	}

	/**
	 * Required - API name: {@code refresh}
	 */
	public final RefreshStats refresh() {
		return this.refresh;
	}

	/**
	 * Required - API name: {@code request_cache}
	 */
	public final RequestCacheStats requestCache() {
		return this.requestCache;
	}

	/**
	 * Required - API name: {@code retention_leases}
	 */
	public final ShardRetentionLeases retentionLeases() {
		return this.retentionLeases;
	}

	/**
	 * Required - API name: {@code routing}
	 */
	public final ShardRouting routing() {
		return this.routing;
	}

	/**
	 * Required - API name: {@code search}
	 */
	public final SearchStats search() {
		return this.search;
	}

	/**
	 * Required - API name: {@code segments}
	 */
	public final SegmentsStats segments() {
		return this.segments;
	}

	/**
	 * Required - API name: {@code seq_no}
	 */
	public final ShardSequenceNumber seqNo() {
		return this.seqNo;
	}

	/**
	 * Required - API name: {@code store}
	 */
	public final StoreStats store() {
		return this.store;
	}

	/**
	 * Required - API name: {@code translog}
	 */
	public final TranslogStats translog() {
		return this.translog;
	}

	/**
	 * Required - API name: {@code warmer}
	 */
	public final WarmerStats warmer() {
		return this.warmer;
	}

	/**
	 * API name: {@code bulk}
	 */
	@Nullable
	public final BulkStats bulk() {
		return this.bulk;
	}

	/**
	 * Required - API name: {@code shards}
	 */
	public final ShardsTotalStats shards() {
		return this.shards;
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

		generator.writeKey("commit");
		this.commit.serialize(generator, mapper);

		generator.writeKey("completion");
		this.completion.serialize(generator, mapper);

		generator.writeKey("docs");
		this.docs.serialize(generator, mapper);

		generator.writeKey("fielddata");
		this.fielddata.serialize(generator, mapper);

		generator.writeKey("flush");
		this.flush.serialize(generator, mapper);

		generator.writeKey("get");
		this.get.serialize(generator, mapper);

		generator.writeKey("indexing");
		this.indexing.serialize(generator, mapper);

		generator.writeKey("merges");
		this.merges.serialize(generator, mapper);

		generator.writeKey("shard_path");
		this.shardPath.serialize(generator, mapper);

		generator.writeKey("query_cache");
		this.queryCache.serialize(generator, mapper);

		generator.writeKey("recovery");
		this.recovery.serialize(generator, mapper);

		generator.writeKey("refresh");
		this.refresh.serialize(generator, mapper);

		generator.writeKey("request_cache");
		this.requestCache.serialize(generator, mapper);

		generator.writeKey("retention_leases");
		this.retentionLeases.serialize(generator, mapper);

		generator.writeKey("routing");
		this.routing.serialize(generator, mapper);

		generator.writeKey("search");
		this.search.serialize(generator, mapper);

		generator.writeKey("segments");
		this.segments.serialize(generator, mapper);

		generator.writeKey("seq_no");
		this.seqNo.serialize(generator, mapper);

		generator.writeKey("store");
		this.store.serialize(generator, mapper);

		generator.writeKey("translog");
		this.translog.serialize(generator, mapper);

		generator.writeKey("warmer");
		this.warmer.serialize(generator, mapper);

		if (this.bulk != null) {
			generator.writeKey("bulk");
			this.bulk.serialize(generator, mapper);

		}
		generator.writeKey("shards");
		this.shards.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ShardStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ShardStats> {
		private ShardCommit commit;

		private CompletionStats completion;

		private DocStats docs;

		private FielddataStats fielddata;

		private FlushStats flush;

		private GetStats get;

		private IndexingStats indexing;

		private MergesStats merges;

		private ShardPath shardPath;

		private ShardQueryCache queryCache;

		private RecoveryStats recovery;

		private RefreshStats refresh;

		private RequestCacheStats requestCache;

		private ShardRetentionLeases retentionLeases;

		private ShardRouting routing;

		private SearchStats search;

		private SegmentsStats segments;

		private ShardSequenceNumber seqNo;

		private StoreStats store;

		private TranslogStats translog;

		private WarmerStats warmer;

		@Nullable
		private BulkStats bulk;

		private ShardsTotalStats shards;

		/**
		 * Required - API name: {@code commit}
		 */
		public final Builder commit(ShardCommit value) {
			this.commit = value;
			return this;
		}

		/**
		 * Required - API name: {@code commit}
		 */
		public final Builder commit(Function<ShardCommit.Builder, ObjectBuilder<ShardCommit>> fn) {
			return this.commit(fn.apply(new ShardCommit.Builder()).build());
		}

		/**
		 * Required - API name: {@code completion}
		 */
		public final Builder completion(CompletionStats value) {
			this.completion = value;
			return this;
		}

		/**
		 * Required - API name: {@code completion}
		 */
		public final Builder completion(Function<CompletionStats.Builder, ObjectBuilder<CompletionStats>> fn) {
			return this.completion(fn.apply(new CompletionStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code docs}
		 */
		public final Builder docs(DocStats value) {
			this.docs = value;
			return this;
		}

		/**
		 * Required - API name: {@code docs}
		 */
		public final Builder docs(Function<DocStats.Builder, ObjectBuilder<DocStats>> fn) {
			return this.docs(fn.apply(new DocStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code fielddata}
		 */
		public final Builder fielddata(FielddataStats value) {
			this.fielddata = value;
			return this;
		}

		/**
		 * Required - API name: {@code fielddata}
		 */
		public final Builder fielddata(Function<FielddataStats.Builder, ObjectBuilder<FielddataStats>> fn) {
			return this.fielddata(fn.apply(new FielddataStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code flush}
		 */
		public final Builder flush(FlushStats value) {
			this.flush = value;
			return this;
		}

		/**
		 * Required - API name: {@code flush}
		 */
		public final Builder flush(Function<FlushStats.Builder, ObjectBuilder<FlushStats>> fn) {
			return this.flush(fn.apply(new FlushStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code get}
		 */
		public final Builder get(GetStats value) {
			this.get = value;
			return this;
		}

		/**
		 * Required - API name: {@code get}
		 */
		public final Builder get(Function<GetStats.Builder, ObjectBuilder<GetStats>> fn) {
			return this.get(fn.apply(new GetStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code indexing}
		 */
		public final Builder indexing(IndexingStats value) {
			this.indexing = value;
			return this;
		}

		/**
		 * Required - API name: {@code indexing}
		 */
		public final Builder indexing(Function<IndexingStats.Builder, ObjectBuilder<IndexingStats>> fn) {
			return this.indexing(fn.apply(new IndexingStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code merges}
		 */
		public final Builder merges(MergesStats value) {
			this.merges = value;
			return this;
		}

		/**
		 * Required - API name: {@code merges}
		 */
		public final Builder merges(Function<MergesStats.Builder, ObjectBuilder<MergesStats>> fn) {
			return this.merges(fn.apply(new MergesStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code shard_path}
		 */
		public final Builder shardPath(ShardPath value) {
			this.shardPath = value;
			return this;
		}

		/**
		 * Required - API name: {@code shard_path}
		 */
		public final Builder shardPath(Function<ShardPath.Builder, ObjectBuilder<ShardPath>> fn) {
			return this.shardPath(fn.apply(new ShardPath.Builder()).build());
		}

		/**
		 * Required - API name: {@code query_cache}
		 */
		public final Builder queryCache(ShardQueryCache value) {
			this.queryCache = value;
			return this;
		}

		/**
		 * Required - API name: {@code query_cache}
		 */
		public final Builder queryCache(Function<ShardQueryCache.Builder, ObjectBuilder<ShardQueryCache>> fn) {
			return this.queryCache(fn.apply(new ShardQueryCache.Builder()).build());
		}

		/**
		 * Required - API name: {@code recovery}
		 */
		public final Builder recovery(RecoveryStats value) {
			this.recovery = value;
			return this;
		}

		/**
		 * Required - API name: {@code recovery}
		 */
		public final Builder recovery(Function<RecoveryStats.Builder, ObjectBuilder<RecoveryStats>> fn) {
			return this.recovery(fn.apply(new RecoveryStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code refresh}
		 */
		public final Builder refresh(RefreshStats value) {
			this.refresh = value;
			return this;
		}

		/**
		 * Required - API name: {@code refresh}
		 */
		public final Builder refresh(Function<RefreshStats.Builder, ObjectBuilder<RefreshStats>> fn) {
			return this.refresh(fn.apply(new RefreshStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code request_cache}
		 */
		public final Builder requestCache(RequestCacheStats value) {
			this.requestCache = value;
			return this;
		}

		/**
		 * Required - API name: {@code request_cache}
		 */
		public final Builder requestCache(Function<RequestCacheStats.Builder, ObjectBuilder<RequestCacheStats>> fn) {
			return this.requestCache(fn.apply(new RequestCacheStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code retention_leases}
		 */
		public final Builder retentionLeases(ShardRetentionLeases value) {
			this.retentionLeases = value;
			return this;
		}

		/**
		 * Required - API name: {@code retention_leases}
		 */
		public final Builder retentionLeases(
				Function<ShardRetentionLeases.Builder, ObjectBuilder<ShardRetentionLeases>> fn) {
			return this.retentionLeases(fn.apply(new ShardRetentionLeases.Builder()).build());
		}

		/**
		 * Required - API name: {@code routing}
		 */
		public final Builder routing(ShardRouting value) {
			this.routing = value;
			return this;
		}

		/**
		 * Required - API name: {@code routing}
		 */
		public final Builder routing(Function<ShardRouting.Builder, ObjectBuilder<ShardRouting>> fn) {
			return this.routing(fn.apply(new ShardRouting.Builder()).build());
		}

		/**
		 * Required - API name: {@code search}
		 */
		public final Builder search(SearchStats value) {
			this.search = value;
			return this;
		}

		/**
		 * Required - API name: {@code search}
		 */
		public final Builder search(Function<SearchStats.Builder, ObjectBuilder<SearchStats>> fn) {
			return this.search(fn.apply(new SearchStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code segments}
		 */
		public final Builder segments(SegmentsStats value) {
			this.segments = value;
			return this;
		}

		/**
		 * Required - API name: {@code segments}
		 */
		public final Builder segments(Function<SegmentsStats.Builder, ObjectBuilder<SegmentsStats>> fn) {
			return this.segments(fn.apply(new SegmentsStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code seq_no}
		 */
		public final Builder seqNo(ShardSequenceNumber value) {
			this.seqNo = value;
			return this;
		}

		/**
		 * Required - API name: {@code seq_no}
		 */
		public final Builder seqNo(Function<ShardSequenceNumber.Builder, ObjectBuilder<ShardSequenceNumber>> fn) {
			return this.seqNo(fn.apply(new ShardSequenceNumber.Builder()).build());
		}

		/**
		 * Required - API name: {@code store}
		 */
		public final Builder store(StoreStats value) {
			this.store = value;
			return this;
		}

		/**
		 * Required - API name: {@code store}
		 */
		public final Builder store(Function<StoreStats.Builder, ObjectBuilder<StoreStats>> fn) {
			return this.store(fn.apply(new StoreStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code translog}
		 */
		public final Builder translog(TranslogStats value) {
			this.translog = value;
			return this;
		}

		/**
		 * Required - API name: {@code translog}
		 */
		public final Builder translog(Function<TranslogStats.Builder, ObjectBuilder<TranslogStats>> fn) {
			return this.translog(fn.apply(new TranslogStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code warmer}
		 */
		public final Builder warmer(WarmerStats value) {
			this.warmer = value;
			return this;
		}

		/**
		 * Required - API name: {@code warmer}
		 */
		public final Builder warmer(Function<WarmerStats.Builder, ObjectBuilder<WarmerStats>> fn) {
			return this.warmer(fn.apply(new WarmerStats.Builder()).build());
		}

		/**
		 * API name: {@code bulk}
		 */
		public final Builder bulk(@Nullable BulkStats value) {
			this.bulk = value;
			return this;
		}

		/**
		 * API name: {@code bulk}
		 */
		public final Builder bulk(Function<BulkStats.Builder, ObjectBuilder<BulkStats>> fn) {
			return this.bulk(fn.apply(new BulkStats.Builder()).build());
		}

		/**
		 * Required - API name: {@code shards}
		 */
		public final Builder shards(ShardsTotalStats value) {
			this.shards = value;
			return this;
		}

		/**
		 * Required - API name: {@code shards}
		 */
		public final Builder shards(Function<ShardsTotalStats.Builder, ObjectBuilder<ShardsTotalStats>> fn) {
			return this.shards(fn.apply(new ShardsTotalStats.Builder()).build());
		}

		/**
		 * Builds a {@link ShardStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ShardStats build() {
			_checkSingleUse();

			return new ShardStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ShardStats}
	 */
	public static final JsonpDeserializer<ShardStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ShardStats::setupShardStatsDeserializer);

	protected static void setupShardStatsDeserializer(ObjectDeserializer<ShardStats.Builder> op) {

		op.add(Builder::commit, ShardCommit._DESERIALIZER, "commit");
		op.add(Builder::completion, CompletionStats._DESERIALIZER, "completion");
		op.add(Builder::docs, DocStats._DESERIALIZER, "docs");
		op.add(Builder::fielddata, FielddataStats._DESERIALIZER, "fielddata");
		op.add(Builder::flush, FlushStats._DESERIALIZER, "flush");
		op.add(Builder::get, GetStats._DESERIALIZER, "get");
		op.add(Builder::indexing, IndexingStats._DESERIALIZER, "indexing");
		op.add(Builder::merges, MergesStats._DESERIALIZER, "merges");
		op.add(Builder::shardPath, ShardPath._DESERIALIZER, "shard_path");
		op.add(Builder::queryCache, ShardQueryCache._DESERIALIZER, "query_cache");
		op.add(Builder::recovery, RecoveryStats._DESERIALIZER, "recovery");
		op.add(Builder::refresh, RefreshStats._DESERIALIZER, "refresh");
		op.add(Builder::requestCache, RequestCacheStats._DESERIALIZER, "request_cache");
		op.add(Builder::retentionLeases, ShardRetentionLeases._DESERIALIZER, "retention_leases");
		op.add(Builder::routing, ShardRouting._DESERIALIZER, "routing");
		op.add(Builder::search, SearchStats._DESERIALIZER, "search");
		op.add(Builder::segments, SegmentsStats._DESERIALIZER, "segments");
		op.add(Builder::seqNo, ShardSequenceNumber._DESERIALIZER, "seq_no");
		op.add(Builder::store, StoreStats._DESERIALIZER, "store");
		op.add(Builder::translog, TranslogStats._DESERIALIZER, "translog");
		op.add(Builder::warmer, WarmerStats._DESERIALIZER, "warmer");
		op.add(Builder::bulk, BulkStats._DESERIALIZER, "bulk");
		op.add(Builder::shards, ShardsTotalStats._DESERIALIZER, "shards");

	}

}
