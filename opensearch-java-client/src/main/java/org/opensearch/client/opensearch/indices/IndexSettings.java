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

package org.opensearch.client.opensearch.indices;

import org.opensearch.client.opensearch._types.Time;
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

// typedef: indices._types.IndexSettings

@JsonpDeserializable
public class IndexSettings implements JsonpSerializable {
	@Nullable
	private final IndexSettings index;

	@Nullable
	private final String mode;

	private final List<String> routingPath;

	@Nullable
	private final SoftDeletes softDeletes;

	@Nullable
	private final IndexSegmentSort sort;

	@Nullable
	private final String numberOfShards;

	@Nullable
	private final String numberOfReplicas;

	@Nullable
	private final Integer numberOfRoutingShards;

	@Nullable
	private final IndexCheckOnStartup checkOnStartup;

	@Nullable
	private final String codec;

	@Nullable
	private final Integer routingPartitionSize;

	@Nullable
	private final Time softDeletesRetentionLeasePeriod;

	@Nullable
	private final Boolean loadFixedBitsetFiltersEagerly;

	@Nullable
	private final Boolean hidden;

	@Nullable
	private final String autoExpandReplicas;

	@Nullable
	private final Integer mergeSchedulerMaxThreadCount;

	@Nullable
	private final Time searchIdleAfter;

	@Nullable
	private final Time refreshInterval;

	@Nullable
	private final Integer maxResultWindow;

	@Nullable
	private final Integer maxInnerResultWindow;

	@Nullable
	private final Integer maxRescoreWindow;

	@Nullable
	private final Integer maxDocvalueFieldsSearch;

	@Nullable
	private final Integer maxScriptFields;

	@Nullable
	private final Integer maxNgramDiff;

	@Nullable
	private final Integer maxShingleDiff;

	@Nullable
	private final IndexSettingBlocks blocks;

	@Nullable
	private final Boolean blocksReadOnly;

	@Nullable
	private final Boolean blocksReadOnlyAllowDelete;

	@Nullable
	private final Boolean blocksRead;

	@Nullable
	private final Boolean blocksWrite;

	@Nullable
	private final Boolean blocksMetadata;

	@Nullable
	private final Integer maxRefreshListeners;

	@Nullable
	private final Integer analyzeMaxTokenCount;

	@Nullable
	private final Integer highlightMaxAnalyzedOffset;

	@Nullable
	private final Integer maxTermsCount;

	@Nullable
	private final Integer maxRegexLength;

	@Nullable
	private final IndexRouting routing;

	@Nullable
	private final Time gcDeletes;

	@Nullable
	private final String defaultPipeline;

	@Nullable
	private final String finalPipeline;

	@Nullable
	private final String lifecycleName;

	@Nullable
	private final String providedName;

	@Nullable
	private final String creationDate;

	@Nullable
	private final String uuid;

	@Nullable
	private final IndexVersioning version;

	@Nullable
	private final Boolean verifiedBeforeClose;

	@Nullable
	private final String format;

	@Nullable
	private final Integer maxSlicesPerScroll;

	@Nullable
	private final String translogDurability;

	@Nullable
	private final String translogFlushThresholdSize;

	@Nullable
	private final Boolean queryStringLenient;

	@Nullable
	private final String priority;

	@Nullable
	private final Integer topMetricsMaxSize;

	@Nullable
	private final IndexSettingsAnalysis analysis;

	@Nullable
	private final IndexSettings settings;

	@Nullable
	private final IndexSettingsMapping mapping;

	// ---------------------------------------------------------------------------------------------

	private IndexSettings(Builder builder) {

		this.index = builder.index;
		this.mode = builder.mode;
		this.routingPath = ApiTypeHelper.unmodifiable(builder.routingPath);
		this.softDeletes = builder.softDeletes;
		this.sort = builder.sort;
		this.numberOfShards = builder.numberOfShards;
		this.numberOfReplicas = builder.numberOfReplicas;
		this.numberOfRoutingShards = builder.numberOfRoutingShards;
		this.checkOnStartup = builder.checkOnStartup;
		this.codec = builder.codec;
		this.routingPartitionSize = builder.routingPartitionSize;
		this.softDeletesRetentionLeasePeriod = builder.softDeletesRetentionLeasePeriod;
		this.loadFixedBitsetFiltersEagerly = builder.loadFixedBitsetFiltersEagerly;
		this.hidden = builder.hidden;
		this.autoExpandReplicas = builder.autoExpandReplicas;
		this.mergeSchedulerMaxThreadCount = builder.mergeSchedulerMaxThreadCount;
		this.searchIdleAfter = builder.searchIdleAfter;
		this.refreshInterval = builder.refreshInterval;
		this.maxResultWindow = builder.maxResultWindow;
		this.maxInnerResultWindow = builder.maxInnerResultWindow;
		this.maxRescoreWindow = builder.maxRescoreWindow;
		this.maxDocvalueFieldsSearch = builder.maxDocvalueFieldsSearch;
		this.maxScriptFields = builder.maxScriptFields;
		this.maxNgramDiff = builder.maxNgramDiff;
		this.maxShingleDiff = builder.maxShingleDiff;
		this.blocks = builder.blocks;
		this.blocksReadOnly = builder.blocksReadOnly;
		this.blocksReadOnlyAllowDelete = builder.blocksReadOnlyAllowDelete;
		this.blocksRead = builder.blocksRead;
		this.blocksWrite = builder.blocksWrite;
		this.blocksMetadata = builder.blocksMetadata;
		this.maxRefreshListeners = builder.maxRefreshListeners;
		this.analyzeMaxTokenCount = builder.analyzeMaxTokenCount;
		this.highlightMaxAnalyzedOffset = builder.highlightMaxAnalyzedOffset;
		this.maxTermsCount = builder.maxTermsCount;
		this.maxRegexLength = builder.maxRegexLength;
		this.routing = builder.routing;
		this.gcDeletes = builder.gcDeletes;
		this.defaultPipeline = builder.defaultPipeline;
		this.finalPipeline = builder.finalPipeline;
		this.lifecycleName = builder.lifecycleName;
		this.providedName = builder.providedName;
		this.creationDate = builder.creationDate;
		this.uuid = builder.uuid;
		this.version = builder.version;
		this.verifiedBeforeClose = builder.verifiedBeforeClose;
		this.format = builder.format;
		this.maxSlicesPerScroll = builder.maxSlicesPerScroll;
		this.translogDurability = builder.translogDurability;
		this.translogFlushThresholdSize = builder.translogFlushThresholdSize;
		this.queryStringLenient = builder.queryStringLenient;
		this.priority = builder.priority;
		this.topMetricsMaxSize = builder.topMetricsMaxSize;
		this.analysis = builder.analysis;
		this.settings = builder.settings;
		this.mapping = builder.mapping;

	}

	public static IndexSettings of(Function<Builder, ObjectBuilder<IndexSettings>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code index}
	 */
	@Nullable
	public final IndexSettings index() {
		return this.index;
	}

	/**
	 * API name: {@code mode}
	 */
	@Nullable
	public final String mode() {
		return this.mode;
	}

	/**
	 * API name: {@code routing_path}
	 */
	public final List<String> routingPath() {
		return this.routingPath;
	}

	/**
	 * API name: {@code soft_deletes}
	 */
	@Nullable
	public final SoftDeletes softDeletes() {
		return this.softDeletes;
	}

	/**
	 * API name: {@code sort}
	 */
	@Nullable
	public final IndexSegmentSort sort() {
		return this.sort;
	}

	/**
	 * API name: {@code number_of_shards}
	 */
	@Nullable
	public final String numberOfShards() {
		return this.numberOfShards;
	}

	/**
	 * API name: {@code number_of_replicas}
	 */
	@Nullable
	public final String numberOfReplicas() {
		return this.numberOfReplicas;
	}

	/**
	 * API name: {@code number_of_routing_shards}
	 */
	@Nullable
	public final Integer numberOfRoutingShards() {
		return this.numberOfRoutingShards;
	}

	/**
	 * API name: {@code check_on_startup}
	 */
	@Nullable
	public final IndexCheckOnStartup checkOnStartup() {
		return this.checkOnStartup;
	}

	/**
	 * API name: {@code codec}
	 */
	@Nullable
	public final String codec() {
		return this.codec;
	}

	/**
	 * API name: {@code routing_partition_size}
	 */
	@Nullable
	public final Integer routingPartitionSize() {
		return this.routingPartitionSize;
	}

	/**
	 * API name: {@code soft_deletes.retention_lease.period}
	 */
	@Nullable
	public final Time softDeletesRetentionLeasePeriod() {
		return this.softDeletesRetentionLeasePeriod;
	}

	/**
	 * API name: {@code load_fixed_bitset_filters_eagerly}
	 */
	@Nullable
	public final Boolean loadFixedBitsetFiltersEagerly() {
		return this.loadFixedBitsetFiltersEagerly;
	}

	/**
	 * API name: {@code hidden}
	 */
	@Nullable
	public final Boolean hidden() {
		return this.hidden;
	}

	/**
	 * API name: {@code auto_expand_replicas}
	 */
	@Nullable
	public final String autoExpandReplicas() {
		return this.autoExpandReplicas;
	}

	/**
	 * API name: {@code merge.scheduler.max_thread_count}
	 */
	@Nullable
	public final Integer mergeSchedulerMaxThreadCount() {
		return this.mergeSchedulerMaxThreadCount;
	}

	/**
	 * API name: {@code search.idle.after}
	 */
	@Nullable
	public final Time searchIdleAfter() {
		return this.searchIdleAfter;
	}

	/**
	 * API name: {@code refresh_interval}
	 */
	@Nullable
	public final Time refreshInterval() {
		return this.refreshInterval;
	}

	/**
	 * API name: {@code max_result_window}
	 */
	@Nullable
	public final Integer maxResultWindow() {
		return this.maxResultWindow;
	}

	/**
	 * API name: {@code max_inner_result_window}
	 */
	@Nullable
	public final Integer maxInnerResultWindow() {
		return this.maxInnerResultWindow;
	}

	/**
	 * API name: {@code max_rescore_window}
	 */
	@Nullable
	public final Integer maxRescoreWindow() {
		return this.maxRescoreWindow;
	}

	/**
	 * API name: {@code max_docvalue_fields_search}
	 */
	@Nullable
	public final Integer maxDocvalueFieldsSearch() {
		return this.maxDocvalueFieldsSearch;
	}

	/**
	 * API name: {@code max_script_fields}
	 */
	@Nullable
	public final Integer maxScriptFields() {
		return this.maxScriptFields;
	}

	/**
	 * API name: {@code max_ngram_diff}
	 */
	@Nullable
	public final Integer maxNgramDiff() {
		return this.maxNgramDiff;
	}

	/**
	 * API name: {@code max_shingle_diff}
	 */
	@Nullable
	public final Integer maxShingleDiff() {
		return this.maxShingleDiff;
	}

	/**
	 * API name: {@code blocks}
	 */
	@Nullable
	public final IndexSettingBlocks blocks() {
		return this.blocks;
	}

	/**
	 * API name: {@code blocks.read_only}
	 */
	@Nullable
	public final Boolean blocksReadOnly() {
		return this.blocksReadOnly;
	}

	/**
	 * API name: {@code blocks.read_only_allow_delete}
	 */
	@Nullable
	public final Boolean blocksReadOnlyAllowDelete() {
		return this.blocksReadOnlyAllowDelete;
	}

	/**
	 * API name: {@code blocks.read}
	 */
	@Nullable
	public final Boolean blocksRead() {
		return this.blocksRead;
	}

	/**
	 * API name: {@code blocks.write}
	 */
	@Nullable
	public final Boolean blocksWrite() {
		return this.blocksWrite;
	}

	/**
	 * API name: {@code blocks.metadata}
	 */
	@Nullable
	public final Boolean blocksMetadata() {
		return this.blocksMetadata;
	}

	/**
	 * API name: {@code max_refresh_listeners}
	 */
	@Nullable
	public final Integer maxRefreshListeners() {
		return this.maxRefreshListeners;
	}

	/**
	 * API name: {@code analyze.max_token_count}
	 */
	@Nullable
	public final Integer analyzeMaxTokenCount() {
		return this.analyzeMaxTokenCount;
	}

	/**
	 * API name: {@code highlight.max_analyzed_offset}
	 */
	@Nullable
	public final Integer highlightMaxAnalyzedOffset() {
		return this.highlightMaxAnalyzedOffset;
	}

	/**
	 * API name: {@code max_terms_count}
	 */
	@Nullable
	public final Integer maxTermsCount() {
		return this.maxTermsCount;
	}

	/**
	 * API name: {@code max_regex_length}
	 */
	@Nullable
	public final Integer maxRegexLength() {
		return this.maxRegexLength;
	}

	/**
	 * API name: {@code routing}
	 */
	@Nullable
	public final IndexRouting routing() {
		return this.routing;
	}

	/**
	 * API name: {@code gc_deletes}
	 */
	@Nullable
	public final Time gcDeletes() {
		return this.gcDeletes;
	}

	/**
	 * API name: {@code default_pipeline}
	 */
	@Nullable
	public final String defaultPipeline() {
		return this.defaultPipeline;
	}

	/**
	 * API name: {@code final_pipeline}
	 */
	@Nullable
	public final String finalPipeline() {
		return this.finalPipeline;
	}

	/**
	 * API name: {@code lifecycle.name}
	 */
	@Nullable
	public final String lifecycleName() {
		return this.lifecycleName;
	}

	/**
	 * API name: {@code provided_name}
	 */
	@Nullable
	public final String providedName() {
		return this.providedName;
	}

	/**
	 * API name: {@code creation_date}
	 */
	@Nullable
	public final String creationDate() {
		return this.creationDate;
	}

	/**
	 * API name: {@code uuid}
	 */
	@Nullable
	public final String uuid() {
		return this.uuid;
	}

	/**
	 * API name: {@code version}
	 */
	@Nullable
	public final IndexVersioning version() {
		return this.version;
	}

	/**
	 * API name: {@code verified_before_close}
	 */
	@Nullable
	public final Boolean verifiedBeforeClose() {
		return this.verifiedBeforeClose;
	}

	/**
	 * API name: {@code format}
	 */
	@Nullable
	public final String format() {
		return this.format;
	}

	/**
	 * API name: {@code max_slices_per_scroll}
	 */
	@Nullable
	public final Integer maxSlicesPerScroll() {
		return this.maxSlicesPerScroll;
	}

	/**
	 * API name: {@code translog.durability}
	 */
	@Nullable
	public final String translogDurability() {
		return this.translogDurability;
	}

	/**
	 * API name: {@code translog.flush_threshold_size}
	 */
	@Nullable
	public final String translogFlushThresholdSize() {
		return this.translogFlushThresholdSize;
	}

	/**
	 * API name: {@code query_string.lenient}
	 */
	@Nullable
	public final Boolean queryStringLenient() {
		return this.queryStringLenient;
	}

	/**
	 * API name: {@code priority}
	 */
	@Nullable
	public final String priority() {
		return this.priority;
	}

	/**
	 * API name: {@code top_metrics_max_size}
	 */
	@Nullable
	public final Integer topMetricsMaxSize() {
		return this.topMetricsMaxSize;
	}

	/**
	 * API name: {@code analysis}
	 */
	@Nullable
	public final IndexSettingsAnalysis analysis() {
		return this.analysis;
	}

	/**
	 * API name: {@code settings}
	 */
	@Nullable
	public final IndexSettings settings() {
		return this.settings;
	}

	/**
	 * API name: {@code mapping}
	 */
	@Nullable
	public final IndexSettingsMapping mapping() {
		return this.mapping;
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

		if (this.index != null) {
			generator.writeKey("index");
			this.index.serialize(generator, mapper);

		}
		if (this.mode != null) {
			generator.writeKey("mode");
			generator.write(this.mode);

		}
		if (ApiTypeHelper.isDefined(this.routingPath)) {
			generator.writeKey("routing_path");
			generator.writeStartArray();
			for (String item0 : this.routingPath) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.softDeletes != null) {
			generator.writeKey("soft_deletes");
			this.softDeletes.serialize(generator, mapper);

		}
		if (this.sort != null) {
			generator.writeKey("sort");
			this.sort.serialize(generator, mapper);

		}
		if (this.numberOfShards != null) {
			generator.writeKey("number_of_shards");
			generator.write(this.numberOfShards);

		}
		if (this.numberOfReplicas != null) {
			generator.writeKey("number_of_replicas");
			generator.write(this.numberOfReplicas);

		}
		if (this.numberOfRoutingShards != null) {
			generator.writeKey("number_of_routing_shards");
			generator.write(this.numberOfRoutingShards);

		}
		if (this.checkOnStartup != null) {
			generator.writeKey("check_on_startup");
			this.checkOnStartup.serialize(generator, mapper);
		}
		if (this.codec != null) {
			generator.writeKey("codec");
			generator.write(this.codec);

		}
		if (this.routingPartitionSize != null) {
			generator.writeKey("routing_partition_size");
			generator.write(this.routingPartitionSize);

		}
		if (this.softDeletesRetentionLeasePeriod != null) {
			generator.writeKey("soft_deletes.retention_lease.period");
			this.softDeletesRetentionLeasePeriod.serialize(generator, mapper);

		}
		if (this.loadFixedBitsetFiltersEagerly != null) {
			generator.writeKey("load_fixed_bitset_filters_eagerly");
			generator.write(this.loadFixedBitsetFiltersEagerly);

		}
		if (this.hidden != null) {
			generator.writeKey("hidden");
			generator.write(this.hidden);

		}
		if (this.autoExpandReplicas != null) {
			generator.writeKey("auto_expand_replicas");
			generator.write(this.autoExpandReplicas);

		}
		if (this.mergeSchedulerMaxThreadCount != null) {
			generator.writeKey("merge.scheduler.max_thread_count");
			generator.write(this.mergeSchedulerMaxThreadCount);

		}
		if (this.searchIdleAfter != null) {
			generator.writeKey("search.idle.after");
			this.searchIdleAfter.serialize(generator, mapper);

		}
		if (this.refreshInterval != null) {
			generator.writeKey("refresh_interval");
			this.refreshInterval.serialize(generator, mapper);

		}
		if (this.maxResultWindow != null) {
			generator.writeKey("max_result_window");
			generator.write(this.maxResultWindow);

		}
		if (this.maxInnerResultWindow != null) {
			generator.writeKey("max_inner_result_window");
			generator.write(this.maxInnerResultWindow);

		}
		if (this.maxRescoreWindow != null) {
			generator.writeKey("max_rescore_window");
			generator.write(this.maxRescoreWindow);

		}
		if (this.maxDocvalueFieldsSearch != null) {
			generator.writeKey("max_docvalue_fields_search");
			generator.write(this.maxDocvalueFieldsSearch);

		}
		if (this.maxScriptFields != null) {
			generator.writeKey("max_script_fields");
			generator.write(this.maxScriptFields);

		}
		if (this.maxNgramDiff != null) {
			generator.writeKey("max_ngram_diff");
			generator.write(this.maxNgramDiff);

		}
		if (this.maxShingleDiff != null) {
			generator.writeKey("max_shingle_diff");
			generator.write(this.maxShingleDiff);

		}
		if (this.blocks != null) {
			generator.writeKey("blocks");
			this.blocks.serialize(generator, mapper);

		}
		if (this.blocksReadOnly != null) {
			generator.writeKey("blocks.read_only");
			generator.write(this.blocksReadOnly);

		}
		if (this.blocksReadOnlyAllowDelete != null) {
			generator.writeKey("blocks.read_only_allow_delete");
			generator.write(this.blocksReadOnlyAllowDelete);

		}
		if (this.blocksRead != null) {
			generator.writeKey("blocks.read");
			generator.write(this.blocksRead);

		}
		if (this.blocksWrite != null) {
			generator.writeKey("blocks.write");
			generator.write(this.blocksWrite);

		}
		if (this.blocksMetadata != null) {
			generator.writeKey("blocks.metadata");
			generator.write(this.blocksMetadata);

		}
		if (this.maxRefreshListeners != null) {
			generator.writeKey("max_refresh_listeners");
			generator.write(this.maxRefreshListeners);

		}
		if (this.analyzeMaxTokenCount != null) {
			generator.writeKey("analyze.max_token_count");
			generator.write(this.analyzeMaxTokenCount);

		}
		if (this.highlightMaxAnalyzedOffset != null) {
			generator.writeKey("highlight.max_analyzed_offset");
			generator.write(this.highlightMaxAnalyzedOffset);

		}
		if (this.maxTermsCount != null) {
			generator.writeKey("max_terms_count");
			generator.write(this.maxTermsCount);

		}
		if (this.maxRegexLength != null) {
			generator.writeKey("max_regex_length");
			generator.write(this.maxRegexLength);

		}
		if (this.routing != null) {
			generator.writeKey("routing");
			this.routing.serialize(generator, mapper);

		}
		if (this.gcDeletes != null) {
			generator.writeKey("gc_deletes");
			this.gcDeletes.serialize(generator, mapper);

		}
		if (this.defaultPipeline != null) {
			generator.writeKey("default_pipeline");
			generator.write(this.defaultPipeline);

		}
		if (this.finalPipeline != null) {
			generator.writeKey("final_pipeline");
			generator.write(this.finalPipeline);

		}

		if (this.lifecycleName != null) {
			generator.writeKey("lifecycle.name");
			generator.write(this.lifecycleName);

		}
		if (this.providedName != null) {
			generator.writeKey("provided_name");
			generator.write(this.providedName);

		}
		if (this.creationDate != null) {
			generator.writeKey("creation_date");
			generator.write(this.creationDate);

		}
		if (this.uuid != null) {
			generator.writeKey("uuid");
			generator.write(this.uuid);

		}
		if (this.version != null) {
			generator.writeKey("version");
			this.version.serialize(generator, mapper);

		}
		if (this.verifiedBeforeClose != null) {
			generator.writeKey("verified_before_close");
			generator.write(this.verifiedBeforeClose);

		}
		if (this.format != null) {
			generator.writeKey("format");
			generator.write(this.format);

		}
		if (this.maxSlicesPerScroll != null) {
			generator.writeKey("max_slices_per_scroll");
			generator.write(this.maxSlicesPerScroll);

		}
		if (this.translogDurability != null) {
			generator.writeKey("translog.durability");
			generator.write(this.translogDurability);

		}
		if (this.translogFlushThresholdSize != null) {
			generator.writeKey("translog.flush_threshold_size");
			generator.write(this.translogFlushThresholdSize);

		}
		if (this.queryStringLenient != null) {
			generator.writeKey("query_string.lenient");
			generator.write(this.queryStringLenient);

		}
		if (this.priority != null) {
			generator.writeKey("priority");
			generator.write(this.priority);

		}
		if (this.topMetricsMaxSize != null) {
			generator.writeKey("top_metrics_max_size");
			generator.write(this.topMetricsMaxSize);

		}
		if (this.analysis != null) {
			generator.writeKey("analysis");
			this.analysis.serialize(generator, mapper);

		}
		if (this.settings != null) {
			generator.writeKey("settings");
			this.settings.serialize(generator, mapper);

		}
		if (this.mapping != null) {
			generator.writeKey("mapping");
			this.mapping.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndexSettings}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexSettings> {
		@Nullable
		private IndexSettings index;

		@Nullable
		private String mode;

		@Nullable
		private List<String> routingPath;

		@Nullable
		private SoftDeletes softDeletes;

		@Nullable
		private IndexSegmentSort sort;

		@Nullable
		private String numberOfShards;

		@Nullable
		private String numberOfReplicas;

		@Nullable
		private Integer numberOfRoutingShards;

		@Nullable
		private IndexCheckOnStartup checkOnStartup;

		@Nullable
		private String codec;

		@Nullable
		private Integer routingPartitionSize;

		@Nullable
		private Time softDeletesRetentionLeasePeriod;

		@Nullable
		private Boolean loadFixedBitsetFiltersEagerly;

		@Nullable
		private Boolean hidden;

		@Nullable
		private String autoExpandReplicas;

		@Nullable
		private Integer mergeSchedulerMaxThreadCount;

		@Nullable
		private Time searchIdleAfter;

		@Nullable
		private Time refreshInterval;

		@Nullable
		private Integer maxResultWindow;

		@Nullable
		private Integer maxInnerResultWindow;

		@Nullable
		private Integer maxRescoreWindow;

		@Nullable
		private Integer maxDocvalueFieldsSearch;

		@Nullable
		private Integer maxScriptFields;

		@Nullable
		private Integer maxNgramDiff;

		@Nullable
		private Integer maxShingleDiff;

		@Nullable
		private IndexSettingBlocks blocks;

		@Nullable
		private Boolean blocksReadOnly;

		@Nullable
		private Boolean blocksReadOnlyAllowDelete;

		@Nullable
		private Boolean blocksRead;

		@Nullable
		private Boolean blocksWrite;

		@Nullable
		private Boolean blocksMetadata;

		@Nullable
		private Integer maxRefreshListeners;

		@Nullable
		private Integer analyzeMaxTokenCount;

		@Nullable
		private Integer highlightMaxAnalyzedOffset;

		@Nullable
		private Integer maxTermsCount;

		@Nullable
		private Integer maxRegexLength;

		@Nullable
		private IndexRouting routing;

		@Nullable
		private Time gcDeletes;

		@Nullable
		private String defaultPipeline;

		@Nullable
		private String finalPipeline;

		@Nullable
		private String lifecycleName;

		@Nullable
		private String providedName;

		@Nullable
		private String creationDate;

		@Nullable
		private String uuid;

		@Nullable
		private IndexVersioning version;

		@Nullable
		private Boolean verifiedBeforeClose;

		@Nullable
		private String format;

		@Nullable
		private Integer maxSlicesPerScroll;

		@Nullable
		private String translogDurability;

		@Nullable
		private String translogFlushThresholdSize;

		@Nullable
		private Boolean queryStringLenient;

		@Nullable
		private String priority;

		@Nullable
		private Integer topMetricsMaxSize;

		@Nullable
		private IndexSettingsAnalysis analysis;

		@Nullable
		private IndexSettings settings;

		@Nullable
		private IndexSettingsMapping mapping;

		/**
		 * API name: {@code index}
		 */
		public final Builder index(@Nullable IndexSettings value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code index}
		 */
		public final Builder index(Function<IndexSettings.Builder, ObjectBuilder<IndexSettings>> fn) {
			return this.index(fn.apply(new IndexSettings.Builder()).build());
		}

		/**
		 * API name: {@code mode}
		 */
		public final Builder mode(@Nullable String value) {
			this.mode = value;
			return this;
		}

		/**
		 * API name: {@code routing_path}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>routingPath</code>.
		 */
		public final Builder routingPath(List<String> list) {
			this.routingPath = _listAddAll(this.routingPath, list);
			return this;
		}

		/**
		 * API name: {@code routing_path}
		 * <p>
		 * Adds one or more values to <code>routingPath</code>.
		 */
		public final Builder routingPath(String value, String... values) {
			this.routingPath = _listAdd(this.routingPath, value, values);
			return this;
		}

		/**
		 * API name: {@code soft_deletes}
		 */
		public final Builder softDeletes(@Nullable SoftDeletes value) {
			this.softDeletes = value;
			return this;
		}

		/**
		 * API name: {@code soft_deletes}
		 */
		public final Builder softDeletes(Function<SoftDeletes.Builder, ObjectBuilder<SoftDeletes>> fn) {
			return this.softDeletes(fn.apply(new SoftDeletes.Builder()).build());
		}

		/**
		 * API name: {@code sort}
		 */
		public final Builder sort(@Nullable IndexSegmentSort value) {
			this.sort = value;
			return this;
		}

		/**
		 * API name: {@code sort}
		 */
		public final Builder sort(Function<IndexSegmentSort.Builder, ObjectBuilder<IndexSegmentSort>> fn) {
			return this.sort(fn.apply(new IndexSegmentSort.Builder()).build());
		}

		/**
		 * API name: {@code number_of_shards}
		 */
		public final Builder numberOfShards(@Nullable String value) {
			this.numberOfShards = value;
			return this;
		}

		/**
		 * API name: {@code number_of_replicas}
		 */
		public final Builder numberOfReplicas(@Nullable String value) {
			this.numberOfReplicas = value;
			return this;
		}

		/**
		 * API name: {@code number_of_routing_shards}
		 */
		public final Builder numberOfRoutingShards(@Nullable Integer value) {
			this.numberOfRoutingShards = value;
			return this;
		}

		/**
		 * API name: {@code check_on_startup}
		 */
		public final Builder checkOnStartup(@Nullable IndexCheckOnStartup value) {
			this.checkOnStartup = value;
			return this;
		}

		/**
		 * API name: {@code codec}
		 */
		public final Builder codec(@Nullable String value) {
			this.codec = value;
			return this;
		}

		/**
		 * API name: {@code routing_partition_size}
		 */
		public final Builder routingPartitionSize(@Nullable Integer value) {
			this.routingPartitionSize = value;
			return this;
		}

		/**
		 * API name: {@code soft_deletes.retention_lease.period}
		 */
		public final Builder softDeletesRetentionLeasePeriod(@Nullable Time value) {
			this.softDeletesRetentionLeasePeriod = value;
			return this;
		}

		/**
		 * API name: {@code soft_deletes.retention_lease.period}
		 */
		public final Builder softDeletesRetentionLeasePeriod(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.softDeletesRetentionLeasePeriod(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code load_fixed_bitset_filters_eagerly}
		 */
		public final Builder loadFixedBitsetFiltersEagerly(@Nullable Boolean value) {
			this.loadFixedBitsetFiltersEagerly = value;
			return this;
		}

		/**
		 * API name: {@code hidden}
		 */
		public final Builder hidden(@Nullable Boolean value) {
			this.hidden = value;
			return this;
		}

		/**
		 * API name: {@code auto_expand_replicas}
		 */
		public final Builder autoExpandReplicas(@Nullable String value) {
			this.autoExpandReplicas = value;
			return this;
		}

		/**
		 * API name: {@code merge.scheduler.max_thread_count}
		 */
		public final Builder mergeSchedulerMaxThreadCount(@Nullable Integer value) {
			this.mergeSchedulerMaxThreadCount = value;
			return this;
		}

		/**
		 * API name: {@code search.idle.after}
		 */
		public final Builder searchIdleAfter(@Nullable Time value) {
			this.searchIdleAfter = value;
			return this;
		}

		/**
		 * API name: {@code search.idle.after}
		 */
		public final Builder searchIdleAfter(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.searchIdleAfter(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code refresh_interval}
		 */
		public final Builder refreshInterval(@Nullable Time value) {
			this.refreshInterval = value;
			return this;
		}

		/**
		 * API name: {@code refresh_interval}
		 */
		public final Builder refreshInterval(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.refreshInterval(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code max_result_window}
		 */
		public final Builder maxResultWindow(@Nullable Integer value) {
			this.maxResultWindow = value;
			return this;
		}

		/**
		 * API name: {@code max_inner_result_window}
		 */
		public final Builder maxInnerResultWindow(@Nullable Integer value) {
			this.maxInnerResultWindow = value;
			return this;
		}

		/**
		 * API name: {@code max_rescore_window}
		 */
		public final Builder maxRescoreWindow(@Nullable Integer value) {
			this.maxRescoreWindow = value;
			return this;
		}

		/**
		 * API name: {@code max_docvalue_fields_search}
		 */
		public final Builder maxDocvalueFieldsSearch(@Nullable Integer value) {
			this.maxDocvalueFieldsSearch = value;
			return this;
		}

		/**
		 * API name: {@code max_script_fields}
		 */
		public final Builder maxScriptFields(@Nullable Integer value) {
			this.maxScriptFields = value;
			return this;
		}

		/**
		 * API name: {@code max_ngram_diff}
		 */
		public final Builder maxNgramDiff(@Nullable Integer value) {
			this.maxNgramDiff = value;
			return this;
		}

		/**
		 * API name: {@code max_shingle_diff}
		 */
		public final Builder maxShingleDiff(@Nullable Integer value) {
			this.maxShingleDiff = value;
			return this;
		}

		/**
		 * API name: {@code blocks}
		 */
		public final Builder blocks(@Nullable IndexSettingBlocks value) {
			this.blocks = value;
			return this;
		}

		/**
		 * API name: {@code blocks}
		 */
		public final Builder blocks(Function<IndexSettingBlocks.Builder, ObjectBuilder<IndexSettingBlocks>> fn) {
			return this.blocks(fn.apply(new IndexSettingBlocks.Builder()).build());
		}

		/**
		 * API name: {@code blocks.read_only}
		 */
		public final Builder blocksReadOnly(@Nullable Boolean value) {
			this.blocksReadOnly = value;
			return this;
		}

		/**
		 * API name: {@code blocks.read_only_allow_delete}
		 */
		public final Builder blocksReadOnlyAllowDelete(@Nullable Boolean value) {
			this.blocksReadOnlyAllowDelete = value;
			return this;
		}

		/**
		 * API name: {@code blocks.read}
		 */
		public final Builder blocksRead(@Nullable Boolean value) {
			this.blocksRead = value;
			return this;
		}

		/**
		 * API name: {@code blocks.write}
		 */
		public final Builder blocksWrite(@Nullable Boolean value) {
			this.blocksWrite = value;
			return this;
		}

		/**
		 * API name: {@code blocks.metadata}
		 */
		public final Builder blocksMetadata(@Nullable Boolean value) {
			this.blocksMetadata = value;
			return this;
		}

		/**
		 * API name: {@code max_refresh_listeners}
		 */
		public final Builder maxRefreshListeners(@Nullable Integer value) {
			this.maxRefreshListeners = value;
			return this;
		}

		/**
		 * API name: {@code analyze.max_token_count}
		 */
		public final Builder analyzeMaxTokenCount(@Nullable Integer value) {
			this.analyzeMaxTokenCount = value;
			return this;
		}

		/**
		 * API name: {@code highlight.max_analyzed_offset}
		 */
		public final Builder highlightMaxAnalyzedOffset(@Nullable Integer value) {
			this.highlightMaxAnalyzedOffset = value;
			return this;
		}

		/**
		 * API name: {@code max_terms_count}
		 */
		public final Builder maxTermsCount(@Nullable Integer value) {
			this.maxTermsCount = value;
			return this;
		}

		/**
		 * API name: {@code max_regex_length}
		 */
		public final Builder maxRegexLength(@Nullable Integer value) {
			this.maxRegexLength = value;
			return this;
		}

		/**
		 * API name: {@code routing}
		 */
		public final Builder routing(@Nullable IndexRouting value) {
			this.routing = value;
			return this;
		}

		/**
		 * API name: {@code routing}
		 */
		public final Builder routing(Function<IndexRouting.Builder, ObjectBuilder<IndexRouting>> fn) {
			return this.routing(fn.apply(new IndexRouting.Builder()).build());
		}

		/**
		 * API name: {@code gc_deletes}
		 */
		public final Builder gcDeletes(@Nullable Time value) {
			this.gcDeletes = value;
			return this;
		}

		/**
		 * API name: {@code gc_deletes}
		 */
		public final Builder gcDeletes(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.gcDeletes(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code default_pipeline}
		 */
		public final Builder defaultPipeline(@Nullable String value) {
			this.defaultPipeline = value;
			return this;
		}

		/**
		 * API name: {@code final_pipeline}
		 */
		public final Builder finalPipeline(@Nullable String value) {
			this.finalPipeline = value;
			return this;
		}

		/**
		 * API name: {@code lifecycle.name}
		 */
		public final Builder lifecycleName(@Nullable String value) {
			this.lifecycleName = value;
			return this;
		}

		/**
		 * API name: {@code provided_name}
		 */
		public final Builder providedName(@Nullable String value) {
			this.providedName = value;
			return this;
		}

		/**
		 * API name: {@code creation_date}
		 */
		public final Builder creationDate(@Nullable String value) {
			this.creationDate = value;
			return this;
		}

		/**
		 * API name: {@code uuid}
		 */
		public final Builder uuid(@Nullable String value) {
			this.uuid = value;
			return this;
		}

		/**
		 * API name: {@code version}
		 */
		public final Builder version(@Nullable IndexVersioning value) {
			this.version = value;
			return this;
		}

		/**
		 * API name: {@code version}
		 */
		public final Builder version(Function<IndexVersioning.Builder, ObjectBuilder<IndexVersioning>> fn) {
			return this.version(fn.apply(new IndexVersioning.Builder()).build());
		}

		/**
		 * API name: {@code verified_before_close}
		 */
		public final Builder verifiedBeforeClose(@Nullable Boolean value) {
			this.verifiedBeforeClose = value;
			return this;
		}

		/**
		 * API name: {@code format}
		 */
		public final Builder format(@Nullable String value) {
			this.format = value;
			return this;
		}

		/**
		 * API name: {@code max_slices_per_scroll}
		 */
		public final Builder maxSlicesPerScroll(@Nullable Integer value) {
			this.maxSlicesPerScroll = value;
			return this;
		}

		/**
		 * API name: {@code translog.durability}
		 */
		public final Builder translogDurability(@Nullable String value) {
			this.translogDurability = value;
			return this;
		}

		/**
		 * API name: {@code translog.flush_threshold_size}
		 */
		public final Builder translogFlushThresholdSize(@Nullable String value) {
			this.translogFlushThresholdSize = value;
			return this;
		}

		/**
		 * API name: {@code query_string.lenient}
		 */
		public final Builder queryStringLenient(@Nullable Boolean value) {
			this.queryStringLenient = value;
			return this;
		}

		/**
		 * API name: {@code priority}
		 */
		public final Builder priority(@Nullable String value) {
			this.priority = value;
			return this;
		}

		/**
		 * API name: {@code top_metrics_max_size}
		 */
		public final Builder topMetricsMaxSize(@Nullable Integer value) {
			this.topMetricsMaxSize = value;
			return this;
		}

		/**
		 * API name: {@code analysis}
		 */
		public final Builder analysis(@Nullable IndexSettingsAnalysis value) {
			this.analysis = value;
			return this;
		}

		/**
		 * API name: {@code analysis}
		 */
		public final Builder analysis(
				Function<IndexSettingsAnalysis.Builder, ObjectBuilder<IndexSettingsAnalysis>> fn) {
			return this.analysis(fn.apply(new IndexSettingsAnalysis.Builder()).build());
		}

		/**
		 * API name: {@code settings}
		 */
		public final Builder settings(@Nullable IndexSettings value) {
			this.settings = value;
			return this;
		}

		/**
		 * API name: {@code settings}
		 */
		public final Builder settings(Function<IndexSettings.Builder, ObjectBuilder<IndexSettings>> fn) {
			return this.settings(fn.apply(new IndexSettings.Builder()).build());
		}

		/**
		 * API name: {@code mapping}
		 */
		public final Builder mapping(@Nullable IndexSettingsMapping value) {
			this.mapping = value;
			return this;
		}

		/**
		 * API name: {@code mapping}
		 */
		public final Builder mapping(Function<IndexSettingsMapping.Builder, ObjectBuilder<IndexSettingsMapping>> fn) {
			return this.mapping(fn.apply(new IndexSettingsMapping.Builder()).build());
		}

		/**
		 * Builds a {@link IndexSettings}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndexSettings build() {
			_checkSingleUse();

			return new IndexSettings(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndexSettings}
	 */
	public static final JsonpDeserializer<IndexSettings> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndexSettings::setupIndexSettingsDeserializer);

	protected static void setupIndexSettingsDeserializer(ObjectDeserializer<IndexSettings.Builder> op) {

		op.add(Builder::index, IndexSettings._DESERIALIZER, "index");
		op.add(Builder::mode, JsonpDeserializer.stringDeserializer(), "mode", "index.mode");
		op.add(Builder::routingPath, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"routing_path", "index.routing_path");
		op.add(Builder::softDeletes, SoftDeletes._DESERIALIZER, "soft_deletes", "index.soft_deletes");
		op.add(Builder::sort, IndexSegmentSort._DESERIALIZER, "sort", "index.sort");
		op.add(Builder::numberOfShards, JsonpDeserializer.stringDeserializer(), "number_of_shards",
				"index.number_of_shards");
		op.add(Builder::numberOfReplicas, JsonpDeserializer.stringDeserializer(), "number_of_replicas",
				"index.number_of_replicas");
		op.add(Builder::numberOfRoutingShards, JsonpDeserializer.integerDeserializer(), "number_of_routing_shards",
				"index.number_of_routing_shards");
		op.add(Builder::checkOnStartup, IndexCheckOnStartup._DESERIALIZER, "check_on_startup",
				"index.check_on_startup");
		op.add(Builder::codec, JsonpDeserializer.stringDeserializer(), "codec", "index.codec");
		op.add(Builder::routingPartitionSize, JsonpDeserializer.integerDeserializer(), "routing_partition_size",
				"index.routing_partition_size");
		op.add(Builder::softDeletesRetentionLeasePeriod, Time._DESERIALIZER, "soft_deletes.retention_lease.period",
				"index.soft_deletes.retention_lease.period");
		op.add(Builder::loadFixedBitsetFiltersEagerly, JsonpDeserializer.booleanDeserializer(),
				"load_fixed_bitset_filters_eagerly", "index.load_fixed_bitset_filters_eagerly");
		op.add(Builder::hidden, JsonpDeserializer.booleanDeserializer(), "hidden", "index.hidden");
		op.add(Builder::autoExpandReplicas, JsonpDeserializer.stringDeserializer(), "auto_expand_replicas",
				"index.auto_expand_replicas");
		op.add(Builder::mergeSchedulerMaxThreadCount, JsonpDeserializer.integerDeserializer(),
				"merge.scheduler.max_thread_count", "index.merge.scheduler.max_thread_count");
		op.add(Builder::searchIdleAfter, Time._DESERIALIZER, "search.idle.after", "index.search.idle.after");
		op.add(Builder::refreshInterval, Time._DESERIALIZER, "refresh_interval", "index.refresh_interval");
		op.add(Builder::maxResultWindow, JsonpDeserializer.integerDeserializer(), "max_result_window",
				"index.max_result_window");
		op.add(Builder::maxInnerResultWindow, JsonpDeserializer.integerDeserializer(), "max_inner_result_window",
				"index.max_inner_result_window");
		op.add(Builder::maxRescoreWindow, JsonpDeserializer.integerDeserializer(), "max_rescore_window",
				"index.max_rescore_window");
		op.add(Builder::maxDocvalueFieldsSearch, JsonpDeserializer.integerDeserializer(), "max_docvalue_fields_search",
				"index.max_docvalue_fields_search");
		op.add(Builder::maxScriptFields, JsonpDeserializer.integerDeserializer(), "max_script_fields",
				"index.max_script_fields");
		op.add(Builder::maxNgramDiff, JsonpDeserializer.integerDeserializer(), "max_ngram_diff",
				"index.max_ngram_diff");
		op.add(Builder::maxShingleDiff, JsonpDeserializer.integerDeserializer(), "max_shingle_diff",
				"index.max_shingle_diff");
		op.add(Builder::blocks, IndexSettingBlocks._DESERIALIZER, "blocks", "index.blocks");
		op.add(Builder::blocksReadOnly, JsonpDeserializer.booleanDeserializer(), "blocks.read_only",
				"index.blocks.read_only");
		op.add(Builder::blocksReadOnlyAllowDelete, JsonpDeserializer.booleanDeserializer(),
				"blocks.read_only_allow_delete", "index.blocks.read_only_allow_delete");
		op.add(Builder::blocksRead, JsonpDeserializer.booleanDeserializer(), "blocks.read", "index.blocks.read");
		op.add(Builder::blocksWrite, JsonpDeserializer.booleanDeserializer(), "blocks.write", "index.blocks.write");
		op.add(Builder::blocksMetadata, JsonpDeserializer.booleanDeserializer(), "blocks.metadata",
				"index.blocks.metadata");
		op.add(Builder::maxRefreshListeners, JsonpDeserializer.integerDeserializer(), "max_refresh_listeners",
				"index.max_refresh_listeners");
		op.add(Builder::analyzeMaxTokenCount, JsonpDeserializer.integerDeserializer(), "analyze.max_token_count",
				"index.analyze.max_token_count");
		op.add(Builder::highlightMaxAnalyzedOffset, JsonpDeserializer.integerDeserializer(),
				"highlight.max_analyzed_offset", "index.highlight.max_analyzed_offset");
		op.add(Builder::maxTermsCount, JsonpDeserializer.integerDeserializer(), "max_terms_count",
				"index.max_terms_count");
		op.add(Builder::maxRegexLength, JsonpDeserializer.integerDeserializer(), "max_regex_length",
				"index.max_regex_length");
		op.add(Builder::routing, IndexRouting._DESERIALIZER, "routing", "index.routing");
		op.add(Builder::gcDeletes, Time._DESERIALIZER, "gc_deletes", "index.gc_deletes");
		op.add(Builder::defaultPipeline, JsonpDeserializer.stringDeserializer(), "default_pipeline",
				"index.default_pipeline");
		op.add(Builder::finalPipeline, JsonpDeserializer.stringDeserializer(), "final_pipeline",
				"index.final_pipeline");
		op.add(Builder::lifecycleName, JsonpDeserializer.stringDeserializer(), "lifecycle.name",
				"index.lifecycle.name");
		op.add(Builder::providedName, JsonpDeserializer.stringDeserializer(), "provided_name", "index.provided_name");
		op.add(Builder::creationDate, JsonpDeserializer.stringDeserializer(), "creation_date", "index.creation_date");
		op.add(Builder::uuid, JsonpDeserializer.stringDeserializer(), "uuid", "index.uuid");
		op.add(Builder::version, IndexVersioning._DESERIALIZER, "version", "index.version");
		op.add(Builder::verifiedBeforeClose, JsonpDeserializer.booleanDeserializer(), "verified_before_close",
				"index.verified_before_close");
		op.add(Builder::format, JsonpDeserializer.stringDeserializer(), "format", "index.format");
		op.add(Builder::maxSlicesPerScroll, JsonpDeserializer.integerDeserializer(), "max_slices_per_scroll",
				"index.max_slices_per_scroll");
		op.add(Builder::translogDurability, JsonpDeserializer.stringDeserializer(), "translog.durability",
				"index.translog.durability");
		op.add(Builder::translogFlushThresholdSize, JsonpDeserializer.stringDeserializer(),
				"translog.flush_threshold_size", "index.translog.flush_threshold_size");
		op.add(Builder::queryStringLenient, JsonpDeserializer.booleanDeserializer(), "query_string.lenient",
				"index.query_string.lenient");
		op.add(Builder::priority, JsonpDeserializer.stringDeserializer(), "priority", "index.priority");
		op.add(Builder::topMetricsMaxSize, JsonpDeserializer.integerDeserializer(), "top_metrics_max_size");
		op.add(Builder::analysis, IndexSettingsAnalysis._DESERIALIZER, "analysis", "index.analysis");
		op.add(Builder::settings, IndexSettings._DESERIALIZER, "settings");
		op.add(Builder::mapping, IndexSettingsMapping._DESERIALIZER, "mapping");

	}

}
