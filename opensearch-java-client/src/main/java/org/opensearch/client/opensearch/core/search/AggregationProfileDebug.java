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

package org.opensearch.client.opensearch.core.search;

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

// typedef: _global.search._types.AggregationProfileDebug


@JsonpDeserializable
public class AggregationProfileDebug implements JsonpSerializable {
	@Nullable
	private final Integer segmentsWithMultiValuedOrds;

	@Nullable
	private final String collectionStrategy;

	@Nullable
	private final Integer segmentsWithSingleValuedOrds;

	@Nullable
	private final Integer totalBuckets;

	@Nullable
	private final Integer builtBuckets;

	@Nullable
	private final String resultStrategy;

	@Nullable
	private final Boolean hasFilter;

	@Nullable
	private final String delegate;

	@Nullable
	private final AggregationProfileDelegateDebug delegateDebug;

	@Nullable
	private final Integer charsFetched;

	@Nullable
	private final Integer extractCount;

	@Nullable
	private final Integer extractNs;

	@Nullable
	private final Integer valuesFetched;

	@Nullable
	private final Integer collectAnalyzedNs;

	@Nullable
	private final Integer collectAnalyzedCount;

	@Nullable
	private final Integer survivingBuckets;

	@Nullable
	private final Integer ordinalsCollectorsUsed;

	@Nullable
	private final Integer ordinalsCollectorsOverheadTooHigh;

	@Nullable
	private final Integer stringHashingCollectorsUsed;

	@Nullable
	private final Integer numericCollectorsUsed;

	@Nullable
	private final Integer emptyCollectorsUsed;

	private final List<String> deferredAggregators;

	// ---------------------------------------------------------------------------------------------

	private AggregationProfileDebug(Builder builder) {

		this.segmentsWithMultiValuedOrds = builder.segmentsWithMultiValuedOrds;
		this.collectionStrategy = builder.collectionStrategy;
		this.segmentsWithSingleValuedOrds = builder.segmentsWithSingleValuedOrds;
		this.totalBuckets = builder.totalBuckets;
		this.builtBuckets = builder.builtBuckets;
		this.resultStrategy = builder.resultStrategy;
		this.hasFilter = builder.hasFilter;
		this.delegate = builder.delegate;
		this.delegateDebug = builder.delegateDebug;
		this.charsFetched = builder.charsFetched;
		this.extractCount = builder.extractCount;
		this.extractNs = builder.extractNs;
		this.valuesFetched = builder.valuesFetched;
		this.collectAnalyzedNs = builder.collectAnalyzedNs;
		this.collectAnalyzedCount = builder.collectAnalyzedCount;
		this.survivingBuckets = builder.survivingBuckets;
		this.ordinalsCollectorsUsed = builder.ordinalsCollectorsUsed;
		this.ordinalsCollectorsOverheadTooHigh = builder.ordinalsCollectorsOverheadTooHigh;
		this.stringHashingCollectorsUsed = builder.stringHashingCollectorsUsed;
		this.numericCollectorsUsed = builder.numericCollectorsUsed;
		this.emptyCollectorsUsed = builder.emptyCollectorsUsed;
		this.deferredAggregators = ApiTypeHelper.unmodifiable(builder.deferredAggregators);

	}

	public static AggregationProfileDebug of(Function<Builder, ObjectBuilder<AggregationProfileDebug>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code segments_with_multi_valued_ords}
	 */
	@Nullable
	public final Integer segmentsWithMultiValuedOrds() {
		return this.segmentsWithMultiValuedOrds;
	}

	/**
	 * API name: {@code collection_strategy}
	 */
	@Nullable
	public final String collectionStrategy() {
		return this.collectionStrategy;
	}

	/**
	 * API name: {@code segments_with_single_valued_ords}
	 */
	@Nullable
	public final Integer segmentsWithSingleValuedOrds() {
		return this.segmentsWithSingleValuedOrds;
	}

	/**
	 * API name: {@code total_buckets}
	 */
	@Nullable
	public final Integer totalBuckets() {
		return this.totalBuckets;
	}

	/**
	 * API name: {@code built_buckets}
	 */
	@Nullable
	public final Integer builtBuckets() {
		return this.builtBuckets;
	}

	/**
	 * API name: {@code result_strategy}
	 */
	@Nullable
	public final String resultStrategy() {
		return this.resultStrategy;
	}

	/**
	 * API name: {@code has_filter}
	 */
	@Nullable
	public final Boolean hasFilter() {
		return this.hasFilter;
	}

	/**
	 * API name: {@code delegate}
	 */
	@Nullable
	public final String delegate() {
		return this.delegate;
	}

	/**
	 * API name: {@code delegate_debug}
	 */
	@Nullable
	public final AggregationProfileDelegateDebug delegateDebug() {
		return this.delegateDebug;
	}

	/**
	 * API name: {@code chars_fetched}
	 */
	@Nullable
	public final Integer charsFetched() {
		return this.charsFetched;
	}

	/**
	 * API name: {@code extract_count}
	 */
	@Nullable
	public final Integer extractCount() {
		return this.extractCount;
	}

	/**
	 * API name: {@code extract_ns}
	 */
	@Nullable
	public final Integer extractNs() {
		return this.extractNs;
	}

	/**
	 * API name: {@code values_fetched}
	 */
	@Nullable
	public final Integer valuesFetched() {
		return this.valuesFetched;
	}

	/**
	 * API name: {@code collect_analyzed_ns}
	 */
	@Nullable
	public final Integer collectAnalyzedNs() {
		return this.collectAnalyzedNs;
	}

	/**
	 * API name: {@code collect_analyzed_count}
	 */
	@Nullable
	public final Integer collectAnalyzedCount() {
		return this.collectAnalyzedCount;
	}

	/**
	 * API name: {@code surviving_buckets}
	 */
	@Nullable
	public final Integer survivingBuckets() {
		return this.survivingBuckets;
	}

	/**
	 * API name: {@code ordinals_collectors_used}
	 */
	@Nullable
	public final Integer ordinalsCollectorsUsed() {
		return this.ordinalsCollectorsUsed;
	}

	/**
	 * API name: {@code ordinals_collectors_overhead_too_high}
	 */
	@Nullable
	public final Integer ordinalsCollectorsOverheadTooHigh() {
		return this.ordinalsCollectorsOverheadTooHigh;
	}

	/**
	 * API name: {@code string_hashing_collectors_used}
	 */
	@Nullable
	public final Integer stringHashingCollectorsUsed() {
		return this.stringHashingCollectorsUsed;
	}

	/**
	 * API name: {@code numeric_collectors_used}
	 */
	@Nullable
	public final Integer numericCollectorsUsed() {
		return this.numericCollectorsUsed;
	}

	/**
	 * API name: {@code empty_collectors_used}
	 */
	@Nullable
	public final Integer emptyCollectorsUsed() {
		return this.emptyCollectorsUsed;
	}

	/**
	 * API name: {@code deferred_aggregators}
	 */
	public final List<String> deferredAggregators() {
		return this.deferredAggregators;
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

		if (this.segmentsWithMultiValuedOrds != null) {
			generator.writeKey("segments_with_multi_valued_ords");
			generator.write(this.segmentsWithMultiValuedOrds);

		}
		if (this.collectionStrategy != null) {
			generator.writeKey("collection_strategy");
			generator.write(this.collectionStrategy);

		}
		if (this.segmentsWithSingleValuedOrds != null) {
			generator.writeKey("segments_with_single_valued_ords");
			generator.write(this.segmentsWithSingleValuedOrds);

		}
		if (this.totalBuckets != null) {
			generator.writeKey("total_buckets");
			generator.write(this.totalBuckets);

		}
		if (this.builtBuckets != null) {
			generator.writeKey("built_buckets");
			generator.write(this.builtBuckets);

		}
		if (this.resultStrategy != null) {
			generator.writeKey("result_strategy");
			generator.write(this.resultStrategy);

		}
		if (this.hasFilter != null) {
			generator.writeKey("has_filter");
			generator.write(this.hasFilter);

		}
		if (this.delegate != null) {
			generator.writeKey("delegate");
			generator.write(this.delegate);

		}
		if (this.delegateDebug != null) {
			generator.writeKey("delegate_debug");
			this.delegateDebug.serialize(generator, mapper);

		}
		if (this.charsFetched != null) {
			generator.writeKey("chars_fetched");
			generator.write(this.charsFetched);

		}
		if (this.extractCount != null) {
			generator.writeKey("extract_count");
			generator.write(this.extractCount);

		}
		if (this.extractNs != null) {
			generator.writeKey("extract_ns");
			generator.write(this.extractNs);

		}
		if (this.valuesFetched != null) {
			generator.writeKey("values_fetched");
			generator.write(this.valuesFetched);

		}
		if (this.collectAnalyzedNs != null) {
			generator.writeKey("collect_analyzed_ns");
			generator.write(this.collectAnalyzedNs);

		}
		if (this.collectAnalyzedCount != null) {
			generator.writeKey("collect_analyzed_count");
			generator.write(this.collectAnalyzedCount);

		}
		if (this.survivingBuckets != null) {
			generator.writeKey("surviving_buckets");
			generator.write(this.survivingBuckets);

		}
		if (this.ordinalsCollectorsUsed != null) {
			generator.writeKey("ordinals_collectors_used");
			generator.write(this.ordinalsCollectorsUsed);

		}
		if (this.ordinalsCollectorsOverheadTooHigh != null) {
			generator.writeKey("ordinals_collectors_overhead_too_high");
			generator.write(this.ordinalsCollectorsOverheadTooHigh);

		}
		if (this.stringHashingCollectorsUsed != null) {
			generator.writeKey("string_hashing_collectors_used");
			generator.write(this.stringHashingCollectorsUsed);

		}
		if (this.numericCollectorsUsed != null) {
			generator.writeKey("numeric_collectors_used");
			generator.write(this.numericCollectorsUsed);

		}
		if (this.emptyCollectorsUsed != null) {
			generator.writeKey("empty_collectors_used");
			generator.write(this.emptyCollectorsUsed);

		}
		if (ApiTypeHelper.isDefined(this.deferredAggregators)) {
			generator.writeKey("deferred_aggregators");
			generator.writeStartArray();
			for (String item0 : this.deferredAggregators) {
				generator.write(item0);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link AggregationProfileDebug}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<AggregationProfileDebug> {
		@Nullable
		private Integer segmentsWithMultiValuedOrds;

		@Nullable
		private String collectionStrategy;

		@Nullable
		private Integer segmentsWithSingleValuedOrds;

		@Nullable
		private Integer totalBuckets;

		@Nullable
		private Integer builtBuckets;

		@Nullable
		private String resultStrategy;

		@Nullable
		private Boolean hasFilter;

		@Nullable
		private String delegate;

		@Nullable
		private AggregationProfileDelegateDebug delegateDebug;

		@Nullable
		private Integer charsFetched;

		@Nullable
		private Integer extractCount;

		@Nullable
		private Integer extractNs;

		@Nullable
		private Integer valuesFetched;

		@Nullable
		private Integer collectAnalyzedNs;

		@Nullable
		private Integer collectAnalyzedCount;

		@Nullable
		private Integer survivingBuckets;

		@Nullable
		private Integer ordinalsCollectorsUsed;

		@Nullable
		private Integer ordinalsCollectorsOverheadTooHigh;

		@Nullable
		private Integer stringHashingCollectorsUsed;

		@Nullable
		private Integer numericCollectorsUsed;

		@Nullable
		private Integer emptyCollectorsUsed;

		@Nullable
		private List<String> deferredAggregators;

		/**
		 * API name: {@code segments_with_multi_valued_ords}
		 */
		public final Builder segmentsWithMultiValuedOrds(@Nullable Integer value) {
			this.segmentsWithMultiValuedOrds = value;
			return this;
		}

		/**
		 * API name: {@code collection_strategy}
		 */
		public final Builder collectionStrategy(@Nullable String value) {
			this.collectionStrategy = value;
			return this;
		}

		/**
		 * API name: {@code segments_with_single_valued_ords}
		 */
		public final Builder segmentsWithSingleValuedOrds(@Nullable Integer value) {
			this.segmentsWithSingleValuedOrds = value;
			return this;
		}

		/**
		 * API name: {@code total_buckets}
		 */
		public final Builder totalBuckets(@Nullable Integer value) {
			this.totalBuckets = value;
			return this;
		}

		/**
		 * API name: {@code built_buckets}
		 */
		public final Builder builtBuckets(@Nullable Integer value) {
			this.builtBuckets = value;
			return this;
		}

		/**
		 * API name: {@code result_strategy}
		 */
		public final Builder resultStrategy(@Nullable String value) {
			this.resultStrategy = value;
			return this;
		}

		/**
		 * API name: {@code has_filter}
		 */
		public final Builder hasFilter(@Nullable Boolean value) {
			this.hasFilter = value;
			return this;
		}

		/**
		 * API name: {@code delegate}
		 */
		public final Builder delegate(@Nullable String value) {
			this.delegate = value;
			return this;
		}

		/**
		 * API name: {@code delegate_debug}
		 */
		public final Builder delegateDebug(@Nullable AggregationProfileDelegateDebug value) {
			this.delegateDebug = value;
			return this;
		}

		/**
		 * API name: {@code delegate_debug}
		 */
		public final Builder delegateDebug(
				Function<AggregationProfileDelegateDebug.Builder, ObjectBuilder<AggregationProfileDelegateDebug>> fn) {
			return this.delegateDebug(fn.apply(new AggregationProfileDelegateDebug.Builder()).build());
		}

		/**
		 * API name: {@code chars_fetched}
		 */
		public final Builder charsFetched(@Nullable Integer value) {
			this.charsFetched = value;
			return this;
		}

		/**
		 * API name: {@code extract_count}
		 */
		public final Builder extractCount(@Nullable Integer value) {
			this.extractCount = value;
			return this;
		}

		/**
		 * API name: {@code extract_ns}
		 */
		public final Builder extractNs(@Nullable Integer value) {
			this.extractNs = value;
			return this;
		}

		/**
		 * API name: {@code values_fetched}
		 */
		public final Builder valuesFetched(@Nullable Integer value) {
			this.valuesFetched = value;
			return this;
		}

		/**
		 * API name: {@code collect_analyzed_ns}
		 */
		public final Builder collectAnalyzedNs(@Nullable Integer value) {
			this.collectAnalyzedNs = value;
			return this;
		}

		/**
		 * API name: {@code collect_analyzed_count}
		 */
		public final Builder collectAnalyzedCount(@Nullable Integer value) {
			this.collectAnalyzedCount = value;
			return this;
		}

		/**
		 * API name: {@code surviving_buckets}
		 */
		public final Builder survivingBuckets(@Nullable Integer value) {
			this.survivingBuckets = value;
			return this;
		}

		/**
		 * API name: {@code ordinals_collectors_used}
		 */
		public final Builder ordinalsCollectorsUsed(@Nullable Integer value) {
			this.ordinalsCollectorsUsed = value;
			return this;
		}

		/**
		 * API name: {@code ordinals_collectors_overhead_too_high}
		 */
		public final Builder ordinalsCollectorsOverheadTooHigh(@Nullable Integer value) {
			this.ordinalsCollectorsOverheadTooHigh = value;
			return this;
		}

		/**
		 * API name: {@code string_hashing_collectors_used}
		 */
		public final Builder stringHashingCollectorsUsed(@Nullable Integer value) {
			this.stringHashingCollectorsUsed = value;
			return this;
		}

		/**
		 * API name: {@code numeric_collectors_used}
		 */
		public final Builder numericCollectorsUsed(@Nullable Integer value) {
			this.numericCollectorsUsed = value;
			return this;
		}

		/**
		 * API name: {@code empty_collectors_used}
		 */
		public final Builder emptyCollectorsUsed(@Nullable Integer value) {
			this.emptyCollectorsUsed = value;
			return this;
		}

		/**
		 * API name: {@code deferred_aggregators}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>deferredAggregators</code>.
		 */
		public final Builder deferredAggregators(List<String> list) {
			this.deferredAggregators = _listAddAll(this.deferredAggregators, list);
			return this;
		}

		/**
		 * API name: {@code deferred_aggregators}
		 * <p>
		 * Adds one or more values to <code>deferredAggregators</code>.
		 */
		public final Builder deferredAggregators(String value, String... values) {
			this.deferredAggregators = _listAdd(this.deferredAggregators, value, values);
			return this;
		}

		/**
		 * Builds a {@link AggregationProfileDebug}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public AggregationProfileDebug build() {
			_checkSingleUse();

			return new AggregationProfileDebug(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link AggregationProfileDebug}
	 */
	public static final JsonpDeserializer<AggregationProfileDebug> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, AggregationProfileDebug::setupAggregationProfileDebugDeserializer);

	protected static void setupAggregationProfileDebugDeserializer(
			ObjectDeserializer<AggregationProfileDebug.Builder> op) {

		op.add(Builder::segmentsWithMultiValuedOrds, JsonpDeserializer.integerDeserializer(),
				"segments_with_multi_valued_ords");
		op.add(Builder::collectionStrategy, JsonpDeserializer.stringDeserializer(), "collection_strategy");
		op.add(Builder::segmentsWithSingleValuedOrds, JsonpDeserializer.integerDeserializer(),
				"segments_with_single_valued_ords");
		op.add(Builder::totalBuckets, JsonpDeserializer.integerDeserializer(), "total_buckets");
		op.add(Builder::builtBuckets, JsonpDeserializer.integerDeserializer(), "built_buckets");
		op.add(Builder::resultStrategy, JsonpDeserializer.stringDeserializer(), "result_strategy");
		op.add(Builder::hasFilter, JsonpDeserializer.booleanDeserializer(), "has_filter");
		op.add(Builder::delegate, JsonpDeserializer.stringDeserializer(), "delegate");
		op.add(Builder::delegateDebug, AggregationProfileDelegateDebug._DESERIALIZER, "delegate_debug");
		op.add(Builder::charsFetched, JsonpDeserializer.integerDeserializer(), "chars_fetched");
		op.add(Builder::extractCount, JsonpDeserializer.integerDeserializer(), "extract_count");
		op.add(Builder::extractNs, JsonpDeserializer.integerDeserializer(), "extract_ns");
		op.add(Builder::valuesFetched, JsonpDeserializer.integerDeserializer(), "values_fetched");
		op.add(Builder::collectAnalyzedNs, JsonpDeserializer.integerDeserializer(), "collect_analyzed_ns");
		op.add(Builder::collectAnalyzedCount, JsonpDeserializer.integerDeserializer(), "collect_analyzed_count");
		op.add(Builder::survivingBuckets, JsonpDeserializer.integerDeserializer(), "surviving_buckets");
		op.add(Builder::ordinalsCollectorsUsed, JsonpDeserializer.integerDeserializer(), "ordinals_collectors_used");
		op.add(Builder::ordinalsCollectorsOverheadTooHigh, JsonpDeserializer.integerDeserializer(),
				"ordinals_collectors_overhead_too_high");
		op.add(Builder::stringHashingCollectorsUsed, JsonpDeserializer.integerDeserializer(),
				"string_hashing_collectors_used");
		op.add(Builder::numericCollectorsUsed, JsonpDeserializer.integerDeserializer(), "numeric_collectors_used");
		op.add(Builder::emptyCollectorsUsed, JsonpDeserializer.integerDeserializer(), "empty_collectors_used");
		op.add(Builder::deferredAggregators,
				JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "deferred_aggregators");

	}

}
