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

package org.opensearch.client.opensearch.cluster.stats;

import org.opensearch.client.opensearch._types.CompletionStats;
import org.opensearch.client.opensearch._types.DocStats;
import org.opensearch.client.opensearch._types.FielddataStats;
import org.opensearch.client.opensearch._types.QueryCacheStats;
import org.opensearch.client.opensearch._types.SegmentsStats;
import org.opensearch.client.opensearch._types.StoreStats;
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
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: cluster.stats.ClusterIndices


@JsonpDeserializable
public class ClusterIndices implements JsonpSerializable {
	private final CompletionStats completion;

	private final long count;

	private final DocStats docs;

	private final FielddataStats fielddata;

	private final QueryCacheStats queryCache;

	private final SegmentsStats segments;

	private final ClusterIndicesShards shards;

	private final StoreStats store;

	private final FieldTypesMappings mappings;

	private final CharFilterTypes analysis;

	private final List<IndicesVersions> versions;

	// ---------------------------------------------------------------------------------------------

	private ClusterIndices(Builder builder) {

		this.completion = ApiTypeHelper.requireNonNull(builder.completion, this, "completion");
		this.count = ApiTypeHelper.requireNonNull(builder.count, this, "count");
		this.docs = ApiTypeHelper.requireNonNull(builder.docs, this, "docs");
		this.fielddata = ApiTypeHelper.requireNonNull(builder.fielddata, this, "fielddata");
		this.queryCache = ApiTypeHelper.requireNonNull(builder.queryCache, this, "queryCache");
		this.segments = ApiTypeHelper.requireNonNull(builder.segments, this, "segments");
		this.shards = ApiTypeHelper.requireNonNull(builder.shards, this, "shards");
		this.store = ApiTypeHelper.requireNonNull(builder.store, this, "store");
		this.mappings = ApiTypeHelper.requireNonNull(builder.mappings, this, "mappings");
		this.analysis = ApiTypeHelper.requireNonNull(builder.analysis, this, "analysis");
		this.versions = ApiTypeHelper.unmodifiable(builder.versions);

	}

	public static ClusterIndices of(Function<Builder, ObjectBuilder<ClusterIndices>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - Contains statistics about memory used for completion in selected
	 * nodes.
	 * <p>
	 * API name: {@code completion}
	 */
	public final CompletionStats completion() {
		return this.completion;
	}

	/**
	 * Required - Total number of indices with shards assigned to selected nodes.
	 * <p>
	 * API name: {@code count}
	 */
	public final long count() {
		return this.count;
	}

	/**
	 * Required - Contains counts for documents in selected nodes.
	 * <p>
	 * API name: {@code docs}
	 */
	public final DocStats docs() {
		return this.docs;
	}

	/**
	 * Required - Contains statistics about the field data cache of selected nodes.
	 * <p>
	 * API name: {@code fielddata}
	 */
	public final FielddataStats fielddata() {
		return this.fielddata;
	}

	/**
	 * Required - Contains statistics about the query cache of selected nodes.
	 * <p>
	 * API name: {@code query_cache}
	 */
	public final QueryCacheStats queryCache() {
		return this.queryCache;
	}

	/**
	 * Required - Contains statistics about segments in selected nodes.
	 * <p>
	 * API name: {@code segments}
	 */
	public final SegmentsStats segments() {
		return this.segments;
	}

	/**
	 * Required - Contains statistics about indices with shards assigned to selected
	 * nodes.
	 * <p>
	 * API name: {@code shards}
	 */
	public final ClusterIndicesShards shards() {
		return this.shards;
	}

	/**
	 * Required - Contains statistics about the size of shards assigned to selected
	 * nodes.
	 * <p>
	 * API name: {@code store}
	 */
	public final StoreStats store() {
		return this.store;
	}

	/**
	 * Required - Contains statistics about field mappings in selected nodes.
	 * <p>
	 * API name: {@code mappings}
	 */
	public final FieldTypesMappings mappings() {
		return this.mappings;
	}

	/**
	 * Required - Contains statistics about analyzers and analyzer components used
	 * in selected nodes.
	 * <p>
	 * API name: {@code analysis}
	 */
	public final CharFilterTypes analysis() {
		return this.analysis;
	}

	/**
	 * API name: {@code versions}
	 */
	public final List<IndicesVersions> versions() {
		return this.versions;
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

		generator.writeKey("completion");
		this.completion.serialize(generator, mapper);

		generator.writeKey("count");
		generator.write(this.count);

		generator.writeKey("docs");
		this.docs.serialize(generator, mapper);

		generator.writeKey("fielddata");
		this.fielddata.serialize(generator, mapper);

		generator.writeKey("query_cache");
		this.queryCache.serialize(generator, mapper);

		generator.writeKey("segments");
		this.segments.serialize(generator, mapper);

		generator.writeKey("shards");
		this.shards.serialize(generator, mapper);

		generator.writeKey("store");
		this.store.serialize(generator, mapper);

		generator.writeKey("mappings");
		this.mappings.serialize(generator, mapper);

		generator.writeKey("analysis");
		this.analysis.serialize(generator, mapper);

		if (ApiTypeHelper.isDefined(this.versions)) {
			generator.writeKey("versions");
			generator.writeStartArray();
			for (IndicesVersions item0 : this.versions) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ClusterIndices}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ClusterIndices> {
		private CompletionStats completion;

		private Long count;

		private DocStats docs;

		private FielddataStats fielddata;

		private QueryCacheStats queryCache;

		private SegmentsStats segments;

		private ClusterIndicesShards shards;

		private StoreStats store;

		private FieldTypesMappings mappings;

		private CharFilterTypes analysis;

		@Nullable
		private List<IndicesVersions> versions;

		/**
		 * Required - Contains statistics about memory used for completion in selected
		 * nodes.
		 * <p>
		 * API name: {@code completion}
		 */
		public final Builder completion(CompletionStats value) {
			this.completion = value;
			return this;
		}

		/**
		 * Required - Contains statistics about memory used for completion in selected
		 * nodes.
		 * <p>
		 * API name: {@code completion}
		 */
		public final Builder completion(Function<CompletionStats.Builder, ObjectBuilder<CompletionStats>> fn) {
			return this.completion(fn.apply(new CompletionStats.Builder()).build());
		}

		/**
		 * Required - Total number of indices with shards assigned to selected nodes.
		 * <p>
		 * API name: {@code count}
		 */
		public final Builder count(long value) {
			this.count = value;
			return this;
		}

		/**
		 * Required - Contains counts for documents in selected nodes.
		 * <p>
		 * API name: {@code docs}
		 */
		public final Builder docs(DocStats value) {
			this.docs = value;
			return this;
		}

		/**
		 * Required - Contains counts for documents in selected nodes.
		 * <p>
		 * API name: {@code docs}
		 */
		public final Builder docs(Function<DocStats.Builder, ObjectBuilder<DocStats>> fn) {
			return this.docs(fn.apply(new DocStats.Builder()).build());
		}

		/**
		 * Required - Contains statistics about the field data cache of selected nodes.
		 * <p>
		 * API name: {@code fielddata}
		 */
		public final Builder fielddata(FielddataStats value) {
			this.fielddata = value;
			return this;
		}

		/**
		 * Required - Contains statistics about the field data cache of selected nodes.
		 * <p>
		 * API name: {@code fielddata}
		 */
		public final Builder fielddata(Function<FielddataStats.Builder, ObjectBuilder<FielddataStats>> fn) {
			return this.fielddata(fn.apply(new FielddataStats.Builder()).build());
		}

		/**
		 * Required - Contains statistics about the query cache of selected nodes.
		 * <p>
		 * API name: {@code query_cache}
		 */
		public final Builder queryCache(QueryCacheStats value) {
			this.queryCache = value;
			return this;
		}

		/**
		 * Required - Contains statistics about the query cache of selected nodes.
		 * <p>
		 * API name: {@code query_cache}
		 */
		public final Builder queryCache(Function<QueryCacheStats.Builder, ObjectBuilder<QueryCacheStats>> fn) {
			return this.queryCache(fn.apply(new QueryCacheStats.Builder()).build());
		}

		/**
		 * Required - Contains statistics about segments in selected nodes.
		 * <p>
		 * API name: {@code segments}
		 */
		public final Builder segments(SegmentsStats value) {
			this.segments = value;
			return this;
		}

		/**
		 * Required - Contains statistics about segments in selected nodes.
		 * <p>
		 * API name: {@code segments}
		 */
		public final Builder segments(Function<SegmentsStats.Builder, ObjectBuilder<SegmentsStats>> fn) {
			return this.segments(fn.apply(new SegmentsStats.Builder()).build());
		}

		/**
		 * Required - Contains statistics about indices with shards assigned to selected
		 * nodes.
		 * <p>
		 * API name: {@code shards}
		 */
		public final Builder shards(ClusterIndicesShards value) {
			this.shards = value;
			return this;
		}

		/**
		 * Required - Contains statistics about indices with shards assigned to selected
		 * nodes.
		 * <p>
		 * API name: {@code shards}
		 */
		public final Builder shards(Function<ClusterIndicesShards.Builder, ObjectBuilder<ClusterIndicesShards>> fn) {
			return this.shards(fn.apply(new ClusterIndicesShards.Builder()).build());
		}

		/**
		 * Required - Contains statistics about the size of shards assigned to selected
		 * nodes.
		 * <p>
		 * API name: {@code store}
		 */
		public final Builder store(StoreStats value) {
			this.store = value;
			return this;
		}

		/**
		 * Required - Contains statistics about the size of shards assigned to selected
		 * nodes.
		 * <p>
		 * API name: {@code store}
		 */
		public final Builder store(Function<StoreStats.Builder, ObjectBuilder<StoreStats>> fn) {
			return this.store(fn.apply(new StoreStats.Builder()).build());
		}

		/**
		 * Required - Contains statistics about field mappings in selected nodes.
		 * <p>
		 * API name: {@code mappings}
		 */
		public final Builder mappings(FieldTypesMappings value) {
			this.mappings = value;
			return this;
		}

		/**
		 * Required - Contains statistics about field mappings in selected nodes.
		 * <p>
		 * API name: {@code mappings}
		 */
		public final Builder mappings(Function<FieldTypesMappings.Builder, ObjectBuilder<FieldTypesMappings>> fn) {
			return this.mappings(fn.apply(new FieldTypesMappings.Builder()).build());
		}

		/**
		 * Required - Contains statistics about analyzers and analyzer components used
		 * in selected nodes.
		 * <p>
		 * API name: {@code analysis}
		 */
		public final Builder analysis(CharFilterTypes value) {
			this.analysis = value;
			return this;
		}

		/**
		 * Required - Contains statistics about analyzers and analyzer components used
		 * in selected nodes.
		 * <p>
		 * API name: {@code analysis}
		 */
		public final Builder analysis(Function<CharFilterTypes.Builder, ObjectBuilder<CharFilterTypes>> fn) {
			return this.analysis(fn.apply(new CharFilterTypes.Builder()).build());
		}

		/**
		 * API name: {@code versions}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>versions</code>.
		 */
		public final Builder versions(List<IndicesVersions> list) {
			this.versions = _listAddAll(this.versions, list);
			return this;
		}

		/**
		 * API name: {@code versions}
		 * <p>
		 * Adds one or more values to <code>versions</code>.
		 */
		public final Builder versions(IndicesVersions value, IndicesVersions... values) {
			this.versions = _listAdd(this.versions, value, values);
			return this;
		}

		/**
		 * API name: {@code versions}
		 * <p>
		 * Adds a value to <code>versions</code> using a builder lambda.
		 */
		public final Builder versions(Function<IndicesVersions.Builder, ObjectBuilder<IndicesVersions>> fn) {
			return versions(fn.apply(new IndicesVersions.Builder()).build());
		}

		/**
		 * Builds a {@link ClusterIndices}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ClusterIndices build() {
			_checkSingleUse();

			return new ClusterIndices(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ClusterIndices}
	 */
	public static final JsonpDeserializer<ClusterIndices> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ClusterIndices::setupClusterIndicesDeserializer);

	protected static void setupClusterIndicesDeserializer(ObjectDeserializer<ClusterIndices.Builder> op) {

		op.add(Builder::completion, CompletionStats._DESERIALIZER, "completion");
		op.add(Builder::count, JsonpDeserializer.longDeserializer(), "count");
		op.add(Builder::docs, DocStats._DESERIALIZER, "docs");
		op.add(Builder::fielddata, FielddataStats._DESERIALIZER, "fielddata");
		op.add(Builder::queryCache, QueryCacheStats._DESERIALIZER, "query_cache");
		op.add(Builder::segments, SegmentsStats._DESERIALIZER, "segments");
		op.add(Builder::shards, ClusterIndicesShards._DESERIALIZER, "shards");
		op.add(Builder::store, StoreStats._DESERIALIZER, "store");
		op.add(Builder::mappings, FieldTypesMappings._DESERIALIZER, "mappings");
		op.add(Builder::analysis, CharFilterTypes._DESERIALIZER, "analysis");
		op.add(Builder::versions, JsonpDeserializer.arrayDeserializer(IndicesVersions._DESERIALIZER), "versions");

	}

}
