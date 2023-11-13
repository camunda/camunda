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

import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.json.JsonData;
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
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: snapshot._types.SnapshotInfo


@JsonpDeserializable
public class SnapshotInfo implements JsonpSerializable {
	private final List<String> dataStreams;

	@Nullable
	private final Time duration;

	@Nullable
	private final String durationInMillis;

	@Nullable
	private final Time endTime;

	@Nullable
	private final String endTimeInMillis;

	private final List<SnapshotShardFailure> failures;

	@Nullable
	private final Boolean includeGlobalState;

	private final List<String> indices;

	private final Map<String, IndexDetails> indexDetails;

	private final Map<String, JsonData> metadata;

	@Nullable
	private final String reason;

	@Nullable
	private final String repository;

	private final String snapshot;

	@Nullable
	private final ShardStatistics shards;

	@Nullable
	private final Time startTime;

	@Nullable
	private final String startTimeInMillis;

	@Nullable
	private final String state;

	private final String uuid;

	@Nullable
	private final String version;

	@Nullable
	private final Long versionId;

	private final List<InfoFeatureState> featureStates;

	// ---------------------------------------------------------------------------------------------

	private SnapshotInfo(Builder builder) {

		this.dataStreams = ApiTypeHelper.unmodifiableRequired(builder.dataStreams, this, "dataStreams");
		this.duration = builder.duration;
		this.durationInMillis = builder.durationInMillis;
		this.endTime = builder.endTime;
		this.endTimeInMillis = builder.endTimeInMillis;
		this.failures = ApiTypeHelper.unmodifiable(builder.failures);
		this.includeGlobalState = builder.includeGlobalState;
		this.indices = ApiTypeHelper.unmodifiableRequired(builder.indices, this, "indices");
		this.indexDetails = ApiTypeHelper.unmodifiable(builder.indexDetails);
		this.metadata = ApiTypeHelper.unmodifiable(builder.metadata);
		this.reason = builder.reason;
		this.repository = builder.repository;
		this.snapshot = ApiTypeHelper.requireNonNull(builder.snapshot, this, "snapshot");
		this.shards = builder.shards;
		this.startTime = builder.startTime;
		this.startTimeInMillis = builder.startTimeInMillis;
		this.state = builder.state;
		this.uuid = ApiTypeHelper.requireNonNull(builder.uuid, this, "uuid");
		this.version = builder.version;
		this.versionId = builder.versionId;
		this.featureStates = ApiTypeHelper.unmodifiable(builder.featureStates);

	}

	public static SnapshotInfo of(Function<Builder, ObjectBuilder<SnapshotInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code data_streams}
	 */
	public final List<String> dataStreams() {
		return this.dataStreams;
	}

	/**
	 * API name: {@code duration}
	 */
	@Nullable
	public final Time duration() {
		return this.duration;
	}

	/**
	 * API name: {@code duration_in_millis}
	 */
	@Nullable
	public final String durationInMillis() {
		return this.durationInMillis;
	}

	/**
	 * API name: {@code end_time}
	 */
	@Nullable
	public final Time endTime() {
		return this.endTime;
	}

	/**
	 * API name: {@code end_time_in_millis}
	 */
	@Nullable
	public final String endTimeInMillis() {
		return this.endTimeInMillis;
	}

	/**
	 * API name: {@code failures}
	 */
	public final List<SnapshotShardFailure> failures() {
		return this.failures;
	}

	/**
	 * API name: {@code include_global_state}
	 */
	@Nullable
	public final Boolean includeGlobalState() {
		return this.includeGlobalState;
	}

	/**
	 * Required - API name: {@code indices}
	 */
	public final List<String> indices() {
		return this.indices;
	}

	/**
	 * API name: {@code index_details}
	 */
	public final Map<String, IndexDetails> indexDetails() {
		return this.indexDetails;
	}

	/**
	 * API name: {@code metadata}
	 */
	public final Map<String, JsonData> metadata() {
		return this.metadata;
	}

	/**
	 * API name: {@code reason}
	 */
	@Nullable
	public final String reason() {
		return this.reason;
	}

	/**
	 * API name: {@code repository}
	 */
	@Nullable
	public final String repository() {
		return this.repository;
	}

	/**
	 * Required - API name: {@code snapshot}
	 */
	public final String snapshot() {
		return this.snapshot;
	}

	/**
	 * API name: {@code shards}
	 */
	@Nullable
	public final ShardStatistics shards() {
		return this.shards;
	}

	/**
	 * API name: {@code start_time}
	 */
	@Nullable
	public final Time startTime() {
		return this.startTime;
	}

	/**
	 * API name: {@code start_time_in_millis}
	 */
	@Nullable
	public final String startTimeInMillis() {
		return this.startTimeInMillis;
	}

	/**
	 * API name: {@code state}
	 */
	@Nullable
	public final String state() {
		return this.state;
	}

	/**
	 * Required - API name: {@code uuid}
	 */
	public final String uuid() {
		return this.uuid;
	}

	/**
	 * API name: {@code version}
	 */
	@Nullable
	public final String version() {
		return this.version;
	}

	/**
	 * API name: {@code version_id}
	 */
	@Nullable
	public final Long versionId() {
		return this.versionId;
	}

	/**
	 * API name: {@code feature_states}
	 */
	public final List<InfoFeatureState> featureStates() {
		return this.featureStates;
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

		if (ApiTypeHelper.isDefined(this.dataStreams)) {
			generator.writeKey("data_streams");
			generator.writeStartArray();
			for (String item0 : this.dataStreams) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.duration != null) {
			generator.writeKey("duration");
			this.duration.serialize(generator, mapper);

		}
		if (this.durationInMillis != null) {
			generator.writeKey("duration_in_millis");
			generator.write(this.durationInMillis);

		}
		if (this.endTime != null) {
			generator.writeKey("end_time");
			this.endTime.serialize(generator, mapper);

		}
		if (this.endTimeInMillis != null) {
			generator.writeKey("end_time_in_millis");
			generator.write(this.endTimeInMillis);

		}
		if (ApiTypeHelper.isDefined(this.failures)) {
			generator.writeKey("failures");
			generator.writeStartArray();
			for (SnapshotShardFailure item0 : this.failures) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

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
		if (ApiTypeHelper.isDefined(this.indexDetails)) {
			generator.writeKey("index_details");
			generator.writeStartObject();
			for (Map.Entry<String, IndexDetails> item0 : this.indexDetails.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

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
		if (this.reason != null) {
			generator.writeKey("reason");
			generator.write(this.reason);

		}
		if (this.repository != null) {
			generator.writeKey("repository");
			generator.write(this.repository);

		}
		generator.writeKey("snapshot");
		generator.write(this.snapshot);

		if (this.shards != null) {
			generator.writeKey("shards");
			this.shards.serialize(generator, mapper);

		}
		if (this.startTime != null) {
			generator.writeKey("start_time");
			this.startTime.serialize(generator, mapper);

		}
		if (this.startTimeInMillis != null) {
			generator.writeKey("start_time_in_millis");
			generator.write(this.startTimeInMillis);

		}
		if (this.state != null) {
			generator.writeKey("state");
			generator.write(this.state);

		}
		generator.writeKey("uuid");
		generator.write(this.uuid);

		if (this.version != null) {
			generator.writeKey("version");
			generator.write(this.version);

		}
		if (this.versionId != null) {
			generator.writeKey("version_id");
			generator.write(this.versionId);

		}
		if (ApiTypeHelper.isDefined(this.featureStates)) {
			generator.writeKey("feature_states");
			generator.writeStartArray();
			for (InfoFeatureState item0 : this.featureStates) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SnapshotInfo}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<SnapshotInfo> {
		private List<String> dataStreams;

		@Nullable
		private Time duration;

		@Nullable
		private String durationInMillis;

		@Nullable
		private Time endTime;

		@Nullable
		private String endTimeInMillis;

		@Nullable
		private List<SnapshotShardFailure> failures;

		@Nullable
		private Boolean includeGlobalState;

		private List<String> indices;

		@Nullable
		private Map<String, IndexDetails> indexDetails;

		@Nullable
		private Map<String, JsonData> metadata;

		@Nullable
		private String reason;

		@Nullable
		private String repository;

		private String snapshot;

		@Nullable
		private ShardStatistics shards;

		@Nullable
		private Time startTime;

		@Nullable
		private String startTimeInMillis;

		@Nullable
		private String state;

		private String uuid;

		@Nullable
		private String version;

		@Nullable
		private Long versionId;

		@Nullable
		private List<InfoFeatureState> featureStates;

		/**
		 * Required - API name: {@code data_streams}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>dataStreams</code>.
		 */
		public final Builder dataStreams(List<String> list) {
			this.dataStreams = _listAddAll(this.dataStreams, list);
			return this;
		}

		/**
		 * Required - API name: {@code data_streams}
		 * <p>
		 * Adds one or more values to <code>dataStreams</code>.
		 */
		public final Builder dataStreams(String value, String... values) {
			this.dataStreams = _listAdd(this.dataStreams, value, values);
			return this;
		}

		/**
		 * API name: {@code duration}
		 */
		public final Builder duration(@Nullable Time value) {
			this.duration = value;
			return this;
		}

		/**
		 * API name: {@code duration}
		 */
		public final Builder duration(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.duration(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code duration_in_millis}
		 */
		public final Builder durationInMillis(@Nullable String value) {
			this.durationInMillis = value;
			return this;
		}

		/**
		 * API name: {@code end_time}
		 */
		public final Builder endTime(@Nullable Time value) {
			this.endTime = value;
			return this;
		}

		/**
		 * API name: {@code end_time}
		 */
		public final Builder endTime(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.endTime(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code end_time_in_millis}
		 */
		public final Builder endTimeInMillis(@Nullable String value) {
			this.endTimeInMillis = value;
			return this;
		}

		/**
		 * API name: {@code failures}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>failures</code>.
		 */
		public final Builder failures(List<SnapshotShardFailure> list) {
			this.failures = _listAddAll(this.failures, list);
			return this;
		}

		/**
		 * API name: {@code failures}
		 * <p>
		 * Adds one or more values to <code>failures</code>.
		 */
		public final Builder failures(SnapshotShardFailure value, SnapshotShardFailure... values) {
			this.failures = _listAdd(this.failures, value, values);
			return this;
		}

		/**
		 * API name: {@code failures}
		 * <p>
		 * Adds a value to <code>failures</code> using a builder lambda.
		 */
		public final Builder failures(Function<SnapshotShardFailure.Builder, ObjectBuilder<SnapshotShardFailure>> fn) {
			return failures(fn.apply(new SnapshotShardFailure.Builder()).build());
		}

		/**
		 * API name: {@code include_global_state}
		 */
		public final Builder includeGlobalState(@Nullable Boolean value) {
			this.includeGlobalState = value;
			return this;
		}

		/**
		 * Required - API name: {@code indices}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>indices</code>.
		 */
		public final Builder indices(List<String> list) {
			this.indices = _listAddAll(this.indices, list);
			return this;
		}

		/**
		 * Required - API name: {@code indices}
		 * <p>
		 * Adds one or more values to <code>indices</code>.
		 */
		public final Builder indices(String value, String... values) {
			this.indices = _listAdd(this.indices, value, values);
			return this;
		}

		/**
		 * API name: {@code index_details}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>indexDetails</code>.
		 */
		public final Builder indexDetails(Map<String, IndexDetails> map) {
			this.indexDetails = _mapPutAll(this.indexDetails, map);
			return this;
		}

		/**
		 * API name: {@code index_details}
		 * <p>
		 * Adds an entry to <code>indexDetails</code>.
		 */
		public final Builder indexDetails(String key, IndexDetails value) {
			this.indexDetails = _mapPut(this.indexDetails, key, value);
			return this;
		}

		/**
		 * API name: {@code index_details}
		 * <p>
		 * Adds an entry to <code>indexDetails</code> using a builder lambda.
		 */
		public final Builder indexDetails(String key, Function<IndexDetails.Builder, ObjectBuilder<IndexDetails>> fn) {
			return indexDetails(key, fn.apply(new IndexDetails.Builder()).build());
		}

		/**
		 * API name: {@code metadata}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>metadata</code>.
		 */
		public final Builder metadata(Map<String, JsonData> map) {
			this.metadata = _mapPutAll(this.metadata, map);
			return this;
		}

		/**
		 * API name: {@code metadata}
		 * <p>
		 * Adds an entry to <code>metadata</code>.
		 */
		public final Builder metadata(String key, JsonData value) {
			this.metadata = _mapPut(this.metadata, key, value);
			return this;
		}

		/**
		 * API name: {@code reason}
		 */
		public final Builder reason(@Nullable String value) {
			this.reason = value;
			return this;
		}

		/**
		 * API name: {@code repository}
		 */
		public final Builder repository(@Nullable String value) {
			this.repository = value;
			return this;
		}

		/**
		 * Required - API name: {@code snapshot}
		 */
		public final Builder snapshot(String value) {
			this.snapshot = value;
			return this;
		}

		/**
		 * API name: {@code shards}
		 */
		public final Builder shards(@Nullable ShardStatistics value) {
			this.shards = value;
			return this;
		}

		/**
		 * API name: {@code shards}
		 */
		public final Builder shards(Function<ShardStatistics.Builder, ObjectBuilder<ShardStatistics>> fn) {
			return this.shards(fn.apply(new ShardStatistics.Builder()).build());
		}

		/**
		 * API name: {@code start_time}
		 */
		public final Builder startTime(@Nullable Time value) {
			this.startTime = value;
			return this;
		}

		/**
		 * API name: {@code start_time}
		 */
		public final Builder startTime(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.startTime(fn.apply(new Time.Builder()).build());
		}

		/**
		 * API name: {@code start_time_in_millis}
		 */
		public final Builder startTimeInMillis(@Nullable String value) {
			this.startTimeInMillis = value;
			return this;
		}

		/**
		 * API name: {@code state}
		 */
		public final Builder state(@Nullable String value) {
			this.state = value;
			return this;
		}

		/**
		 * Required - API name: {@code uuid}
		 */
		public final Builder uuid(String value) {
			this.uuid = value;
			return this;
		}

		/**
		 * API name: {@code version}
		 */
		public final Builder version(@Nullable String value) {
			this.version = value;
			return this;
		}

		/**
		 * API name: {@code version_id}
		 */
		public final Builder versionId(@Nullable Long value) {
			this.versionId = value;
			return this;
		}

		/**
		 * API name: {@code feature_states}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>featureStates</code>.
		 */
		public final Builder featureStates(List<InfoFeatureState> list) {
			this.featureStates = _listAddAll(this.featureStates, list);
			return this;
		}

		/**
		 * API name: {@code feature_states}
		 * <p>
		 * Adds one or more values to <code>featureStates</code>.
		 */
		public final Builder featureStates(InfoFeatureState value, InfoFeatureState... values) {
			this.featureStates = _listAdd(this.featureStates, value, values);
			return this;
		}

		/**
		 * API name: {@code feature_states}
		 * <p>
		 * Adds a value to <code>featureStates</code> using a builder lambda.
		 */
		public final Builder featureStates(Function<InfoFeatureState.Builder, ObjectBuilder<InfoFeatureState>> fn) {
			return featureStates(fn.apply(new InfoFeatureState.Builder()).build());
		}

		/**
		 * Builds a {@link SnapshotInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public SnapshotInfo build() {
			_checkSingleUse();

			return new SnapshotInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link SnapshotInfo}
	 */
	public static final JsonpDeserializer<SnapshotInfo> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			SnapshotInfo::setupSnapshotInfoDeserializer);

	protected static void setupSnapshotInfoDeserializer(ObjectDeserializer<SnapshotInfo.Builder> op) {

		op.add(Builder::dataStreams, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"data_streams");
		op.add(Builder::duration, Time._DESERIALIZER, "duration");
		op.add(Builder::durationInMillis, JsonpDeserializer.stringDeserializer(), "duration_in_millis");
		op.add(Builder::endTime, Time._DESERIALIZER, "end_time");
		op.add(Builder::endTimeInMillis, JsonpDeserializer.stringDeserializer(), "end_time_in_millis");
		op.add(Builder::failures, JsonpDeserializer.arrayDeserializer(SnapshotShardFailure._DESERIALIZER), "failures");
		op.add(Builder::includeGlobalState, JsonpDeserializer.booleanDeserializer(), "include_global_state");
		op.add(Builder::indices, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"indices");
		op.add(Builder::indexDetails, JsonpDeserializer.stringMapDeserializer(IndexDetails._DESERIALIZER),
				"index_details");
		op.add(Builder::metadata, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "metadata");
		op.add(Builder::reason, JsonpDeserializer.stringDeserializer(), "reason");
		op.add(Builder::repository, JsonpDeserializer.stringDeserializer(), "repository");
		op.add(Builder::snapshot, JsonpDeserializer.stringDeserializer(), "snapshot");
		op.add(Builder::shards, ShardStatistics._DESERIALIZER, "shards");
		op.add(Builder::startTime, Time._DESERIALIZER, "start_time");
		op.add(Builder::startTimeInMillis, JsonpDeserializer.stringDeserializer(), "start_time_in_millis");
		op.add(Builder::state, JsonpDeserializer.stringDeserializer(), "state");
		op.add(Builder::uuid, JsonpDeserializer.stringDeserializer(), "uuid");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version");
		op.add(Builder::versionId, JsonpDeserializer.longDeserializer(), "version_id");
		op.add(Builder::featureStates, JsonpDeserializer.arrayDeserializer(InfoFeatureState._DESERIALIZER),
				"feature_states");

	}

}
