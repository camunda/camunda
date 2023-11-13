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
import org.opensearch.client.opensearch._types.QueryCacheStats;
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
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: indices.stats.IndexStats


@JsonpDeserializable
public class IndexStats implements JsonpSerializable {
	@Nullable
	private final CompletionStats completion;

	@Nullable
	private final DocStats docs;

	@Nullable
	private final FielddataStats fielddata;

	@Nullable
	private final FlushStats flush;

	@Nullable
	private final GetStats get;

	@Nullable
	private final IndexingStats indexing;

	@Nullable
	private final MergesStats merges;

	@Nullable
	private final QueryCacheStats queryCache;

	@Nullable
	private final RecoveryStats recovery;

	@Nullable
	private final RefreshStats refresh;

	@Nullable
	private final RequestCacheStats requestCache;

	@Nullable
	private final SearchStats search;

	@Nullable
	private final SegmentsStats segments;

	@Nullable
	private final StoreStats store;

	@Nullable
	private final TranslogStats translog;

	@Nullable
	private final WarmerStats warmer;

	@Nullable
	private final BulkStats bulk;

	@Nullable
	private final ShardsTotalStats shards;

	// ---------------------------------------------------------------------------------------------

	private IndexStats(Builder builder) {

		this.completion = builder.completion;
		this.docs = builder.docs;
		this.fielddata = builder.fielddata;
		this.flush = builder.flush;
		this.get = builder.get;
		this.indexing = builder.indexing;
		this.merges = builder.merges;
		this.queryCache = builder.queryCache;
		this.recovery = builder.recovery;
		this.refresh = builder.refresh;
		this.requestCache = builder.requestCache;
		this.search = builder.search;
		this.segments = builder.segments;
		this.store = builder.store;
		this.translog = builder.translog;
		this.warmer = builder.warmer;
		this.bulk = builder.bulk;
		this.shards = builder.shards;

	}

	public static IndexStats of(Function<Builder, ObjectBuilder<IndexStats>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Contains statistics about completions across all shards assigned to the node.
	 * <p>
	 * API name: {@code completion}
	 */
	@Nullable
	public final CompletionStats completion() {
		return this.completion;
	}

	/**
	 * Contains statistics about documents across all primary shards assigned to the
	 * node.
	 * <p>
	 * API name: {@code docs}
	 */
	@Nullable
	public final DocStats docs() {
		return this.docs;
	}

	/**
	 * Contains statistics about the field data cache across all shards assigned to
	 * the node.
	 * <p>
	 * API name: {@code fielddata}
	 */
	@Nullable
	public final FielddataStats fielddata() {
		return this.fielddata;
	}

	/**
	 * Contains statistics about flush operations for the node.
	 * <p>
	 * API name: {@code flush}
	 */
	@Nullable
	public final FlushStats flush() {
		return this.flush;
	}

	/**
	 * Contains statistics about get operations for the node.
	 * <p>
	 * API name: {@code get}
	 */
	@Nullable
	public final GetStats get() {
		return this.get;
	}

	/**
	 * Contains statistics about indexing operations for the node.
	 * <p>
	 * API name: {@code indexing}
	 */
	@Nullable
	public final IndexingStats indexing() {
		return this.indexing;
	}

	/**
	 * Contains statistics about merge operations for the node.
	 * <p>
	 * API name: {@code merges}
	 */
	@Nullable
	public final MergesStats merges() {
		return this.merges;
	}

	/**
	 * Contains statistics about the query cache across all shards assigned to the
	 * node.
	 * <p>
	 * API name: {@code query_cache}
	 */
	@Nullable
	public final QueryCacheStats queryCache() {
		return this.queryCache;
	}

	/**
	 * Contains statistics about recovery operations for the node.
	 * <p>
	 * API name: {@code recovery}
	 */
	@Nullable
	public final RecoveryStats recovery() {
		return this.recovery;
	}

	/**
	 * Contains statistics about refresh operations for the node.
	 * <p>
	 * API name: {@code refresh}
	 */
	@Nullable
	public final RefreshStats refresh() {
		return this.refresh;
	}

	/**
	 * Contains statistics about the request cache across all shards assigned to the
	 * node.
	 * <p>
	 * API name: {@code request_cache}
	 */
	@Nullable
	public final RequestCacheStats requestCache() {
		return this.requestCache;
	}

	/**
	 * Contains statistics about search operations for the node.
	 * <p>
	 * API name: {@code search}
	 */
	@Nullable
	public final SearchStats search() {
		return this.search;
	}

	/**
	 * Contains statistics about segments across all shards assigned to the node.
	 * <p>
	 * API name: {@code segments}
	 */
	@Nullable
	public final SegmentsStats segments() {
		return this.segments;
	}

	/**
	 * Contains statistics about the size of shards assigned to the node.
	 * <p>
	 * API name: {@code store}
	 */
	@Nullable
	public final StoreStats store() {
		return this.store;
	}

	/**
	 * Contains statistics about transaction log operations for the node.
	 * <p>
	 * API name: {@code translog}
	 */
	@Nullable
	public final TranslogStats translog() {
		return this.translog;
	}

	/**
	 * Contains statistics about index warming operations for the node.
	 * <p>
	 * API name: {@code warmer}
	 */
	@Nullable
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
	 * API name: {@code shards}
	 */
	@Nullable
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

		if (this.completion != null) {
			generator.writeKey("completion");
			this.completion.serialize(generator, mapper);

		}
		if (this.docs != null) {
			generator.writeKey("docs");
			this.docs.serialize(generator, mapper);

		}
		if (this.fielddata != null) {
			generator.writeKey("fielddata");
			this.fielddata.serialize(generator, mapper);

		}
		if (this.flush != null) {
			generator.writeKey("flush");
			this.flush.serialize(generator, mapper);

		}
		if (this.get != null) {
			generator.writeKey("get");
			this.get.serialize(generator, mapper);

		}
		if (this.indexing != null) {
			generator.writeKey("indexing");
			this.indexing.serialize(generator, mapper);

		}
		if (this.merges != null) {
			generator.writeKey("merges");
			this.merges.serialize(generator, mapper);

		}
		if (this.queryCache != null) {
			generator.writeKey("query_cache");
			this.queryCache.serialize(generator, mapper);

		}
		if (this.recovery != null) {
			generator.writeKey("recovery");
			this.recovery.serialize(generator, mapper);

		}
		if (this.refresh != null) {
			generator.writeKey("refresh");
			this.refresh.serialize(generator, mapper);

		}
		if (this.requestCache != null) {
			generator.writeKey("request_cache");
			this.requestCache.serialize(generator, mapper);

		}
		if (this.search != null) {
			generator.writeKey("search");
			this.search.serialize(generator, mapper);

		}
		if (this.segments != null) {
			generator.writeKey("segments");
			this.segments.serialize(generator, mapper);

		}
		if (this.store != null) {
			generator.writeKey("store");
			this.store.serialize(generator, mapper);

		}
		if (this.translog != null) {
			generator.writeKey("translog");
			this.translog.serialize(generator, mapper);

		}
		if (this.warmer != null) {
			generator.writeKey("warmer");
			this.warmer.serialize(generator, mapper);

		}
		if (this.bulk != null) {
			generator.writeKey("bulk");
			this.bulk.serialize(generator, mapper);

		}
		if (this.shards != null) {
			generator.writeKey("shards");
			this.shards.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndexStats}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexStats> {
		@Nullable
		private CompletionStats completion;

		@Nullable
		private DocStats docs;

		@Nullable
		private FielddataStats fielddata;

		@Nullable
		private FlushStats flush;

		@Nullable
		private GetStats get;

		@Nullable
		private IndexingStats indexing;

		@Nullable
		private MergesStats merges;

		@Nullable
		private QueryCacheStats queryCache;

		@Nullable
		private RecoveryStats recovery;

		@Nullable
		private RefreshStats refresh;

		@Nullable
		private RequestCacheStats requestCache;

		@Nullable
		private SearchStats search;

		@Nullable
		private SegmentsStats segments;

		@Nullable
		private StoreStats store;

		@Nullable
		private TranslogStats translog;

		@Nullable
		private WarmerStats warmer;

		@Nullable
		private BulkStats bulk;

		@Nullable
		private ShardsTotalStats shards;

		/**
		 * Contains statistics about completions across all shards assigned to the node.
		 * <p>
		 * API name: {@code completion}
		 */
		public final Builder completion(@Nullable CompletionStats value) {
			this.completion = value;
			return this;
		}

		/**
		 * Contains statistics about completions across all shards assigned to the node.
		 * <p>
		 * API name: {@code completion}
		 */
		public final Builder completion(Function<CompletionStats.Builder, ObjectBuilder<CompletionStats>> fn) {
			return this.completion(fn.apply(new CompletionStats.Builder()).build());
		}

		/**
		 * Contains statistics about documents across all primary shards assigned to the
		 * node.
		 * <p>
		 * API name: {@code docs}
		 */
		public final Builder docs(@Nullable DocStats value) {
			this.docs = value;
			return this;
		}

		/**
		 * Contains statistics about documents across all primary shards assigned to the
		 * node.
		 * <p>
		 * API name: {@code docs}
		 */
		public final Builder docs(Function<DocStats.Builder, ObjectBuilder<DocStats>> fn) {
			return this.docs(fn.apply(new DocStats.Builder()).build());
		}

		/**
		 * Contains statistics about the field data cache across all shards assigned to
		 * the node.
		 * <p>
		 * API name: {@code fielddata}
		 */
		public final Builder fielddata(@Nullable FielddataStats value) {
			this.fielddata = value;
			return this;
		}

		/**
		 * Contains statistics about the field data cache across all shards assigned to
		 * the node.
		 * <p>
		 * API name: {@code fielddata}
		 */
		public final Builder fielddata(Function<FielddataStats.Builder, ObjectBuilder<FielddataStats>> fn) {
			return this.fielddata(fn.apply(new FielddataStats.Builder()).build());
		}

		/**
		 * Contains statistics about flush operations for the node.
		 * <p>
		 * API name: {@code flush}
		 */
		public final Builder flush(@Nullable FlushStats value) {
			this.flush = value;
			return this;
		}

		/**
		 * Contains statistics about flush operations for the node.
		 * <p>
		 * API name: {@code flush}
		 */
		public final Builder flush(Function<FlushStats.Builder, ObjectBuilder<FlushStats>> fn) {
			return this.flush(fn.apply(new FlushStats.Builder()).build());
		}

		/**
		 * Contains statistics about get operations for the node.
		 * <p>
		 * API name: {@code get}
		 */
		public final Builder get(@Nullable GetStats value) {
			this.get = value;
			return this;
		}

		/**
		 * Contains statistics about get operations for the node.
		 * <p>
		 * API name: {@code get}
		 */
		public final Builder get(Function<GetStats.Builder, ObjectBuilder<GetStats>> fn) {
			return this.get(fn.apply(new GetStats.Builder()).build());
		}

		/**
		 * Contains statistics about indexing operations for the node.
		 * <p>
		 * API name: {@code indexing}
		 */
		public final Builder indexing(@Nullable IndexingStats value) {
			this.indexing = value;
			return this;
		}

		/**
		 * Contains statistics about indexing operations for the node.
		 * <p>
		 * API name: {@code indexing}
		 */
		public final Builder indexing(Function<IndexingStats.Builder, ObjectBuilder<IndexingStats>> fn) {
			return this.indexing(fn.apply(new IndexingStats.Builder()).build());
		}

		/**
		 * Contains statistics about merge operations for the node.
		 * <p>
		 * API name: {@code merges}
		 */
		public final Builder merges(@Nullable MergesStats value) {
			this.merges = value;
			return this;
		}

		/**
		 * Contains statistics about merge operations for the node.
		 * <p>
		 * API name: {@code merges}
		 */
		public final Builder merges(Function<MergesStats.Builder, ObjectBuilder<MergesStats>> fn) {
			return this.merges(fn.apply(new MergesStats.Builder()).build());
		}

		/**
		 * Contains statistics about the query cache across all shards assigned to the
		 * node.
		 * <p>
		 * API name: {@code query_cache}
		 */
		public final Builder queryCache(@Nullable QueryCacheStats value) {
			this.queryCache = value;
			return this;
		}

		/**
		 * Contains statistics about the query cache across all shards assigned to the
		 * node.
		 * <p>
		 * API name: {@code query_cache}
		 */
		public final Builder queryCache(Function<QueryCacheStats.Builder, ObjectBuilder<QueryCacheStats>> fn) {
			return this.queryCache(fn.apply(new QueryCacheStats.Builder()).build());
		}

		/**
		 * Contains statistics about recovery operations for the node.
		 * <p>
		 * API name: {@code recovery}
		 */
		public final Builder recovery(@Nullable RecoveryStats value) {
			this.recovery = value;
			return this;
		}

		/**
		 * Contains statistics about recovery operations for the node.
		 * <p>
		 * API name: {@code recovery}
		 */
		public final Builder recovery(Function<RecoveryStats.Builder, ObjectBuilder<RecoveryStats>> fn) {
			return this.recovery(fn.apply(new RecoveryStats.Builder()).build());
		}

		/**
		 * Contains statistics about refresh operations for the node.
		 * <p>
		 * API name: {@code refresh}
		 */
		public final Builder refresh(@Nullable RefreshStats value) {
			this.refresh = value;
			return this;
		}

		/**
		 * Contains statistics about refresh operations for the node.
		 * <p>
		 * API name: {@code refresh}
		 */
		public final Builder refresh(Function<RefreshStats.Builder, ObjectBuilder<RefreshStats>> fn) {
			return this.refresh(fn.apply(new RefreshStats.Builder()).build());
		}

		/**
		 * Contains statistics about the request cache across all shards assigned to the
		 * node.
		 * <p>
		 * API name: {@code request_cache}
		 */
		public final Builder requestCache(@Nullable RequestCacheStats value) {
			this.requestCache = value;
			return this;
		}

		/**
		 * Contains statistics about the request cache across all shards assigned to the
		 * node.
		 * <p>
		 * API name: {@code request_cache}
		 */
		public final Builder requestCache(Function<RequestCacheStats.Builder, ObjectBuilder<RequestCacheStats>> fn) {
			return this.requestCache(fn.apply(new RequestCacheStats.Builder()).build());
		}

		/**
		 * Contains statistics about search operations for the node.
		 * <p>
		 * API name: {@code search}
		 */
		public final Builder search(@Nullable SearchStats value) {
			this.search = value;
			return this;
		}

		/**
		 * Contains statistics about search operations for the node.
		 * <p>
		 * API name: {@code search}
		 */
		public final Builder search(Function<SearchStats.Builder, ObjectBuilder<SearchStats>> fn) {
			return this.search(fn.apply(new SearchStats.Builder()).build());
		}

		/**
		 * Contains statistics about segments across all shards assigned to the node.
		 * <p>
		 * API name: {@code segments}
		 */
		public final Builder segments(@Nullable SegmentsStats value) {
			this.segments = value;
			return this;
		}

		/**
		 * Contains statistics about segments across all shards assigned to the node.
		 * <p>
		 * API name: {@code segments}
		 */
		public final Builder segments(Function<SegmentsStats.Builder, ObjectBuilder<SegmentsStats>> fn) {
			return this.segments(fn.apply(new SegmentsStats.Builder()).build());
		}

		/**
		 * Contains statistics about the size of shards assigned to the node.
		 * <p>
		 * API name: {@code store}
		 */
		public final Builder store(@Nullable StoreStats value) {
			this.store = value;
			return this;
		}

		/**
		 * Contains statistics about the size of shards assigned to the node.
		 * <p>
		 * API name: {@code store}
		 */
		public final Builder store(Function<StoreStats.Builder, ObjectBuilder<StoreStats>> fn) {
			return this.store(fn.apply(new StoreStats.Builder()).build());
		}

		/**
		 * Contains statistics about transaction log operations for the node.
		 * <p>
		 * API name: {@code translog}
		 */
		public final Builder translog(@Nullable TranslogStats value) {
			this.translog = value;
			return this;
		}

		/**
		 * Contains statistics about transaction log operations for the node.
		 * <p>
		 * API name: {@code translog}
		 */
		public final Builder translog(Function<TranslogStats.Builder, ObjectBuilder<TranslogStats>> fn) {
			return this.translog(fn.apply(new TranslogStats.Builder()).build());
		}

		/**
		 * Contains statistics about index warming operations for the node.
		 * <p>
		 * API name: {@code warmer}
		 */
		public final Builder warmer(@Nullable WarmerStats value) {
			this.warmer = value;
			return this;
		}

		/**
		 * Contains statistics about index warming operations for the node.
		 * <p>
		 * API name: {@code warmer}
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
		 * API name: {@code shards}
		 */
		public final Builder shards(@Nullable ShardsTotalStats value) {
			this.shards = value;
			return this;
		}

		/**
		 * API name: {@code shards}
		 */
		public final Builder shards(Function<ShardsTotalStats.Builder, ObjectBuilder<ShardsTotalStats>> fn) {
			return this.shards(fn.apply(new ShardsTotalStats.Builder()).build());
		}

		/**
		 * Builds a {@link IndexStats}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndexStats build() {
			_checkSingleUse();

			return new IndexStats(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndexStats}
	 */
	public static final JsonpDeserializer<IndexStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndexStats::setupIndexStatsDeserializer);

	protected static void setupIndexStatsDeserializer(ObjectDeserializer<IndexStats.Builder> op) {

		op.add(Builder::completion, CompletionStats._DESERIALIZER, "completion");
		op.add(Builder::docs, DocStats._DESERIALIZER, "docs");
		op.add(Builder::fielddata, FielddataStats._DESERIALIZER, "fielddata");
		op.add(Builder::flush, FlushStats._DESERIALIZER, "flush");
		op.add(Builder::get, GetStats._DESERIALIZER, "get");
		op.add(Builder::indexing, IndexingStats._DESERIALIZER, "indexing");
		op.add(Builder::merges, MergesStats._DESERIALIZER, "merges");
		op.add(Builder::queryCache, QueryCacheStats._DESERIALIZER, "query_cache");
		op.add(Builder::recovery, RecoveryStats._DESERIALIZER, "recovery");
		op.add(Builder::refresh, RefreshStats._DESERIALIZER, "refresh");
		op.add(Builder::requestCache, RequestCacheStats._DESERIALIZER, "request_cache");
		op.add(Builder::search, SearchStats._DESERIALIZER, "search");
		op.add(Builder::segments, SegmentsStats._DESERIALIZER, "segments");
		op.add(Builder::store, StoreStats._DESERIALIZER, "store");
		op.add(Builder::translog, TranslogStats._DESERIALIZER, "translog");
		op.add(Builder::warmer, WarmerStats._DESERIALIZER, "warmer");
		op.add(Builder::bulk, BulkStats._DESERIALIZER, "bulk");
		op.add(Builder::shards, ShardsTotalStats._DESERIALIZER, "shards");

	}

}
