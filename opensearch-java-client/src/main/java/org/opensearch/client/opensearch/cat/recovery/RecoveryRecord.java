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

package org.opensearch.client.opensearch.cat.recovery;

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

// typedef: cat.recovery.RecoveryRecord


@JsonpDeserializable
public class RecoveryRecord implements JsonpSerializable {
	@Nullable
	private final String index;

	@Nullable
	private final String shard;

	@Nullable
	private final String startTime;

	@Nullable
	private final String startTimeMillis;

	@Nullable
	private final String stopTime;

	@Nullable
	private final String stopTimeMillis;

	@Nullable
	private final String time;

	@Nullable
	private final String type;

	@Nullable
	private final String stage;

	@Nullable
	private final String sourceHost;

	@Nullable
	private final String sourceNode;

	@Nullable
	private final String targetHost;

	@Nullable
	private final String targetNode;

	@Nullable
	private final String repository;

	@Nullable
	private final String snapshot;

	@Nullable
	private final String files;

	@Nullable
	private final String filesRecovered;

	@Nullable
	private final String filesPercent;

	@Nullable
	private final String filesTotal;

	@Nullable
	private final String bytes;

	@Nullable
	private final String bytesRecovered;

	@Nullable
	private final String bytesPercent;

	@Nullable
	private final String bytesTotal;

	@Nullable
	private final String translogOps;

	@Nullable
	private final String translogOpsRecovered;

	@Nullable
	private final String translogOpsPercent;

	// ---------------------------------------------------------------------------------------------

	private RecoveryRecord(Builder builder) {

		this.index = builder.index;
		this.shard = builder.shard;
		this.startTime = builder.startTime;
		this.startTimeMillis = builder.startTimeMillis;
		this.stopTime = builder.stopTime;
		this.stopTimeMillis = builder.stopTimeMillis;
		this.time = builder.time;
		this.type = builder.type;
		this.stage = builder.stage;
		this.sourceHost = builder.sourceHost;
		this.sourceNode = builder.sourceNode;
		this.targetHost = builder.targetHost;
		this.targetNode = builder.targetNode;
		this.repository = builder.repository;
		this.snapshot = builder.snapshot;
		this.files = builder.files;
		this.filesRecovered = builder.filesRecovered;
		this.filesPercent = builder.filesPercent;
		this.filesTotal = builder.filesTotal;
		this.bytes = builder.bytes;
		this.bytesRecovered = builder.bytesRecovered;
		this.bytesPercent = builder.bytesPercent;
		this.bytesTotal = builder.bytesTotal;
		this.translogOps = builder.translogOps;
		this.translogOpsRecovered = builder.translogOpsRecovered;
		this.translogOpsPercent = builder.translogOpsPercent;

	}

	public static RecoveryRecord of(Function<Builder, ObjectBuilder<RecoveryRecord>> fn) {
		return fn.apply(new Builder()).build();
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
	 * shard name
	 * <p>
	 * API name: {@code shard}
	 */
	@Nullable
	public final String shard() {
		return this.shard;
	}

	/**
	 * recovery start time
	 * <p>
	 * API name: {@code start_time}
	 */
	@Nullable
	public final String startTime() {
		return this.startTime;
	}

	/**
	 * recovery start time in epoch milliseconds
	 * <p>
	 * API name: {@code start_time_millis}
	 */
	@Nullable
	public final String startTimeMillis() {
		return this.startTimeMillis;
	}

	/**
	 * recovery stop time
	 * <p>
	 * API name: {@code stop_time}
	 */
	@Nullable
	public final String stopTime() {
		return this.stopTime;
	}

	/**
	 * recovery stop time in epoch milliseconds
	 * <p>
	 * API name: {@code stop_time_millis}
	 */
	@Nullable
	public final String stopTimeMillis() {
		return this.stopTimeMillis;
	}

	/**
	 * recovery time
	 * <p>
	 * API name: {@code time}
	 */
	@Nullable
	public final String time() {
		return this.time;
	}

	/**
	 * recovery type
	 * <p>
	 * API name: {@code type}
	 */
	@Nullable
	public final String type() {
		return this.type;
	}

	/**
	 * recovery stage
	 * <p>
	 * API name: {@code stage}
	 */
	@Nullable
	public final String stage() {
		return this.stage;
	}

	/**
	 * source host
	 * <p>
	 * API name: {@code source_host}
	 */
	@Nullable
	public final String sourceHost() {
		return this.sourceHost;
	}

	/**
	 * source node name
	 * <p>
	 * API name: {@code source_node}
	 */
	@Nullable
	public final String sourceNode() {
		return this.sourceNode;
	}

	/**
	 * target host
	 * <p>
	 * API name: {@code target_host}
	 */
	@Nullable
	public final String targetHost() {
		return this.targetHost;
	}

	/**
	 * target node name
	 * <p>
	 * API name: {@code target_node}
	 */
	@Nullable
	public final String targetNode() {
		return this.targetNode;
	}

	/**
	 * repository
	 * <p>
	 * API name: {@code repository}
	 */
	@Nullable
	public final String repository() {
		return this.repository;
	}

	/**
	 * snapshot
	 * <p>
	 * API name: {@code snapshot}
	 */
	@Nullable
	public final String snapshot() {
		return this.snapshot;
	}

	/**
	 * number of files to recover
	 * <p>
	 * API name: {@code files}
	 */
	@Nullable
	public final String files() {
		return this.files;
	}

	/**
	 * files recovered
	 * <p>
	 * API name: {@code files_recovered}
	 */
	@Nullable
	public final String filesRecovered() {
		return this.filesRecovered;
	}

	/**
	 * percent of files recovered
	 * <p>
	 * API name: {@code files_percent}
	 */
	@Nullable
	public final String filesPercent() {
		return this.filesPercent;
	}

	/**
	 * total number of files
	 * <p>
	 * API name: {@code files_total}
	 */
	@Nullable
	public final String filesTotal() {
		return this.filesTotal;
	}

	/**
	 * number of bytes to recover
	 * <p>
	 * API name: {@code bytes}
	 */
	@Nullable
	public final String bytes() {
		return this.bytes;
	}

	/**
	 * bytes recovered
	 * <p>
	 * API name: {@code bytes_recovered}
	 */
	@Nullable
	public final String bytesRecovered() {
		return this.bytesRecovered;
	}

	/**
	 * percent of bytes recovered
	 * <p>
	 * API name: {@code bytes_percent}
	 */
	@Nullable
	public final String bytesPercent() {
		return this.bytesPercent;
	}

	/**
	 * total number of bytes
	 * <p>
	 * API name: {@code bytes_total}
	 */
	@Nullable
	public final String bytesTotal() {
		return this.bytesTotal;
	}

	/**
	 * number of translog ops to recover
	 * <p>
	 * API name: {@code translog_ops}
	 */
	@Nullable
	public final String translogOps() {
		return this.translogOps;
	}

	/**
	 * translog ops recovered
	 * <p>
	 * API name: {@code translog_ops_recovered}
	 */
	@Nullable
	public final String translogOpsRecovered() {
		return this.translogOpsRecovered;
	}

	/**
	 * percent of translog ops recovered
	 * <p>
	 * API name: {@code translog_ops_percent}
	 */
	@Nullable
	public final String translogOpsPercent() {
		return this.translogOpsPercent;
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
			generator.write(this.index);

		}
		if (this.shard != null) {
			generator.writeKey("shard");
			generator.write(this.shard);

		}
		if (this.startTime != null) {
			generator.writeKey("start_time");
			generator.write(this.startTime);

		}
		if (this.startTimeMillis != null) {
			generator.writeKey("start_time_millis");
			generator.write(this.startTimeMillis);

		}
		if (this.stopTime != null) {
			generator.writeKey("stop_time");
			generator.write(this.stopTime);

		}
		if (this.stopTimeMillis != null) {
			generator.writeKey("stop_time_millis");
			generator.write(this.stopTimeMillis);

		}
		if (this.time != null) {
			generator.writeKey("time");
			generator.write(this.time);

		}
		if (this.type != null) {
			generator.writeKey("type");
			generator.write(this.type);

		}
		if (this.stage != null) {
			generator.writeKey("stage");
			generator.write(this.stage);

		}
		if (this.sourceHost != null) {
			generator.writeKey("source_host");
			generator.write(this.sourceHost);

		}
		if (this.sourceNode != null) {
			generator.writeKey("source_node");
			generator.write(this.sourceNode);

		}
		if (this.targetHost != null) {
			generator.writeKey("target_host");
			generator.write(this.targetHost);

		}
		if (this.targetNode != null) {
			generator.writeKey("target_node");
			generator.write(this.targetNode);

		}
		if (this.repository != null) {
			generator.writeKey("repository");
			generator.write(this.repository);

		}
		if (this.snapshot != null) {
			generator.writeKey("snapshot");
			generator.write(this.snapshot);

		}
		if (this.files != null) {
			generator.writeKey("files");
			generator.write(this.files);

		}
		if (this.filesRecovered != null) {
			generator.writeKey("files_recovered");
			generator.write(this.filesRecovered);

		}
		if (this.filesPercent != null) {
			generator.writeKey("files_percent");
			generator.write(this.filesPercent);

		}
		if (this.filesTotal != null) {
			generator.writeKey("files_total");
			generator.write(this.filesTotal);

		}
		if (this.bytes != null) {
			generator.writeKey("bytes");
			generator.write(this.bytes);

		}
		if (this.bytesRecovered != null) {
			generator.writeKey("bytes_recovered");
			generator.write(this.bytesRecovered);

		}
		if (this.bytesPercent != null) {
			generator.writeKey("bytes_percent");
			generator.write(this.bytesPercent);

		}
		if (this.bytesTotal != null) {
			generator.writeKey("bytes_total");
			generator.write(this.bytesTotal);

		}
		if (this.translogOps != null) {
			generator.writeKey("translog_ops");
			generator.write(this.translogOps);

		}
		if (this.translogOpsRecovered != null) {
			generator.writeKey("translog_ops_recovered");
			generator.write(this.translogOpsRecovered);

		}
		if (this.translogOpsPercent != null) {
			generator.writeKey("translog_ops_percent");
			generator.write(this.translogOpsPercent);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RecoveryRecord}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RecoveryRecord> {
		@Nullable
		private String index;

		@Nullable
		private String shard;

		@Nullable
		private String startTime;

		@Nullable
		private String startTimeMillis;

		@Nullable
		private String stopTime;

		@Nullable
		private String stopTimeMillis;

		@Nullable
		private String time;

		@Nullable
		private String type;

		@Nullable
		private String stage;

		@Nullable
		private String sourceHost;

		@Nullable
		private String sourceNode;

		@Nullable
		private String targetHost;

		@Nullable
		private String targetNode;

		@Nullable
		private String repository;

		@Nullable
		private String snapshot;

		@Nullable
		private String files;

		@Nullable
		private String filesRecovered;

		@Nullable
		private String filesPercent;

		@Nullable
		private String filesTotal;

		@Nullable
		private String bytes;

		@Nullable
		private String bytesRecovered;

		@Nullable
		private String bytesPercent;

		@Nullable
		private String bytesTotal;

		@Nullable
		private String translogOps;

		@Nullable
		private String translogOpsRecovered;

		@Nullable
		private String translogOpsPercent;

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
		 * shard name
		 * <p>
		 * API name: {@code shard}
		 */
		public final Builder shard(@Nullable String value) {
			this.shard = value;
			return this;
		}

		/**
		 * recovery start time
		 * <p>
		 * API name: {@code start_time}
		 */
		public final Builder startTime(@Nullable String value) {
			this.startTime = value;
			return this;
		}

		/**
		 * recovery start time in epoch milliseconds
		 * <p>
		 * API name: {@code start_time_millis}
		 */
		public final Builder startTimeMillis(@Nullable String value) {
			this.startTimeMillis = value;
			return this;
		}

		/**
		 * recovery stop time
		 * <p>
		 * API name: {@code stop_time}
		 */
		public final Builder stopTime(@Nullable String value) {
			this.stopTime = value;
			return this;
		}

		/**
		 * recovery stop time in epoch milliseconds
		 * <p>
		 * API name: {@code stop_time_millis}
		 */
		public final Builder stopTimeMillis(@Nullable String value) {
			this.stopTimeMillis = value;
			return this;
		}

		/**
		 * recovery time
		 * <p>
		 * API name: {@code time}
		 */
		public final Builder time(@Nullable String value) {
			this.time = value;
			return this;
		}

		/**
		 * recovery type
		 * <p>
		 * API name: {@code type}
		 */
		public final Builder type(@Nullable String value) {
			this.type = value;
			return this;
		}

		/**
		 * recovery stage
		 * <p>
		 * API name: {@code stage}
		 */
		public final Builder stage(@Nullable String value) {
			this.stage = value;
			return this;
		}

		/**
		 * source host
		 * <p>
		 * API name: {@code source_host}
		 */
		public final Builder sourceHost(@Nullable String value) {
			this.sourceHost = value;
			return this;
		}

		/**
		 * source node name
		 * <p>
		 * API name: {@code source_node}
		 */
		public final Builder sourceNode(@Nullable String value) {
			this.sourceNode = value;
			return this;
		}

		/**
		 * target host
		 * <p>
		 * API name: {@code target_host}
		 */
		public final Builder targetHost(@Nullable String value) {
			this.targetHost = value;
			return this;
		}

		/**
		 * target node name
		 * <p>
		 * API name: {@code target_node}
		 */
		public final Builder targetNode(@Nullable String value) {
			this.targetNode = value;
			return this;
		}

		/**
		 * repository
		 * <p>
		 * API name: {@code repository}
		 */
		public final Builder repository(@Nullable String value) {
			this.repository = value;
			return this;
		}

		/**
		 * snapshot
		 * <p>
		 * API name: {@code snapshot}
		 */
		public final Builder snapshot(@Nullable String value) {
			this.snapshot = value;
			return this;
		}

		/**
		 * number of files to recover
		 * <p>
		 * API name: {@code files}
		 */
		public final Builder files(@Nullable String value) {
			this.files = value;
			return this;
		}

		/**
		 * files recovered
		 * <p>
		 * API name: {@code files_recovered}
		 */
		public final Builder filesRecovered(@Nullable String value) {
			this.filesRecovered = value;
			return this;
		}

		/**
		 * percent of files recovered
		 * <p>
		 * API name: {@code files_percent}
		 */
		public final Builder filesPercent(@Nullable String value) {
			this.filesPercent = value;
			return this;
		}

		/**
		 * total number of files
		 * <p>
		 * API name: {@code files_total}
		 */
		public final Builder filesTotal(@Nullable String value) {
			this.filesTotal = value;
			return this;
		}

		/**
		 * number of bytes to recover
		 * <p>
		 * API name: {@code bytes}
		 */
		public final Builder bytes(@Nullable String value) {
			this.bytes = value;
			return this;
		}

		/**
		 * bytes recovered
		 * <p>
		 * API name: {@code bytes_recovered}
		 */
		public final Builder bytesRecovered(@Nullable String value) {
			this.bytesRecovered = value;
			return this;
		}

		/**
		 * percent of bytes recovered
		 * <p>
		 * API name: {@code bytes_percent}
		 */
		public final Builder bytesPercent(@Nullable String value) {
			this.bytesPercent = value;
			return this;
		}

		/**
		 * total number of bytes
		 * <p>
		 * API name: {@code bytes_total}
		 */
		public final Builder bytesTotal(@Nullable String value) {
			this.bytesTotal = value;
			return this;
		}

		/**
		 * number of translog ops to recover
		 * <p>
		 * API name: {@code translog_ops}
		 */
		public final Builder translogOps(@Nullable String value) {
			this.translogOps = value;
			return this;
		}

		/**
		 * translog ops recovered
		 * <p>
		 * API name: {@code translog_ops_recovered}
		 */
		public final Builder translogOpsRecovered(@Nullable String value) {
			this.translogOpsRecovered = value;
			return this;
		}

		/**
		 * percent of translog ops recovered
		 * <p>
		 * API name: {@code translog_ops_percent}
		 */
		public final Builder translogOpsPercent(@Nullable String value) {
			this.translogOpsPercent = value;
			return this;
		}

		/**
		 * Builds a {@link RecoveryRecord}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RecoveryRecord build() {
			_checkSingleUse();

			return new RecoveryRecord(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RecoveryRecord}
	 */
	public static final JsonpDeserializer<RecoveryRecord> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			RecoveryRecord::setupRecoveryRecordDeserializer);

	protected static void setupRecoveryRecordDeserializer(ObjectDeserializer<RecoveryRecord.Builder> op) {

		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index", "i", "idx");
		op.add(Builder::shard, JsonpDeserializer.stringDeserializer(), "shard", "s", "sh");
		op.add(Builder::startTime, JsonpDeserializer.stringDeserializer(), "start_time", "start");
		op.add(Builder::startTimeMillis, JsonpDeserializer.stringDeserializer(), "start_time_millis", "start_millis");
		op.add(Builder::stopTime, JsonpDeserializer.stringDeserializer(), "stop_time", "stop");
		op.add(Builder::stopTimeMillis, JsonpDeserializer.stringDeserializer(), "stop_time_millis", "stop_millis");
		op.add(Builder::time, JsonpDeserializer.stringDeserializer(), "time", "t", "ti");
		op.add(Builder::type, JsonpDeserializer.stringDeserializer(), "type", "ty");
		op.add(Builder::stage, JsonpDeserializer.stringDeserializer(), "stage", "st");
		op.add(Builder::sourceHost, JsonpDeserializer.stringDeserializer(), "source_host", "shost");
		op.add(Builder::sourceNode, JsonpDeserializer.stringDeserializer(), "source_node", "snode");
		op.add(Builder::targetHost, JsonpDeserializer.stringDeserializer(), "target_host", "thost");
		op.add(Builder::targetNode, JsonpDeserializer.stringDeserializer(), "target_node", "tnode");
		op.add(Builder::repository, JsonpDeserializer.stringDeserializer(), "repository", "rep");
		op.add(Builder::snapshot, JsonpDeserializer.stringDeserializer(), "snapshot", "snap");
		op.add(Builder::files, JsonpDeserializer.stringDeserializer(), "files", "f");
		op.add(Builder::filesRecovered, JsonpDeserializer.stringDeserializer(), "files_recovered", "fr");
		op.add(Builder::filesPercent, JsonpDeserializer.stringDeserializer(), "files_percent", "fp");
		op.add(Builder::filesTotal, JsonpDeserializer.stringDeserializer(), "files_total", "tf");
		op.add(Builder::bytes, JsonpDeserializer.stringDeserializer(), "bytes", "b");
		op.add(Builder::bytesRecovered, JsonpDeserializer.stringDeserializer(), "bytes_recovered", "br");
		op.add(Builder::bytesPercent, JsonpDeserializer.stringDeserializer(), "bytes_percent", "bp");
		op.add(Builder::bytesTotal, JsonpDeserializer.stringDeserializer(), "bytes_total", "tb");
		op.add(Builder::translogOps, JsonpDeserializer.stringDeserializer(), "translog_ops", "to");
		op.add(Builder::translogOpsRecovered, JsonpDeserializer.stringDeserializer(), "translog_ops_recovered", "tor");
		op.add(Builder::translogOpsPercent, JsonpDeserializer.stringDeserializer(), "translog_ops_percent", "top");

	}

}
