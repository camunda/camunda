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

package org.opensearch.client.opensearch.ingest.geo_ip_stats;

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

// typedef: ingest.geo_ip_stats.GeoIpDownloadStatistics


@JsonpDeserializable
public class GeoIpDownloadStatistics implements JsonpSerializable {
	private final int successfulDownloads;

	private final int failedDownloads;

	private final int totalDownloadTime;

	private final int databaseCount;

	private final int skippedUpdates;

	// ---------------------------------------------------------------------------------------------

	private GeoIpDownloadStatistics(Builder builder) {

		this.successfulDownloads = ApiTypeHelper.requireNonNull(builder.successfulDownloads, this,
				"successfulDownloads");
		this.failedDownloads = ApiTypeHelper.requireNonNull(builder.failedDownloads, this, "failedDownloads");
		this.totalDownloadTime = ApiTypeHelper.requireNonNull(builder.totalDownloadTime, this, "totalDownloadTime");
		this.databaseCount = ApiTypeHelper.requireNonNull(builder.databaseCount, this, "databaseCount");
		this.skippedUpdates = ApiTypeHelper.requireNonNull(builder.skippedUpdates, this, "skippedUpdates");

	}

	public static GeoIpDownloadStatistics of(Function<Builder, ObjectBuilder<GeoIpDownloadStatistics>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - Total number of successful database downloads.
	 * <p>
	 * API name: {@code successful_downloads}
	 */
	public final int successfulDownloads() {
		return this.successfulDownloads;
	}

	/**
	 * Required - Total number of failed database downloads.
	 * <p>
	 * API name: {@code failed_downloads}
	 */
	public final int failedDownloads() {
		return this.failedDownloads;
	}

	/**
	 * Required - Total milliseconds spent downloading databases.
	 * <p>
	 * API name: {@code total_download_time}
	 */
	public final int totalDownloadTime() {
		return this.totalDownloadTime;
	}

	/**
	 * Required - Current number of databases available for use.
	 * <p>
	 * API name: {@code database_count}
	 */
	public final int databaseCount() {
		return this.databaseCount;
	}

	/**
	 * Required - Total number of database updates skipped.
	 * <p>
	 * API name: {@code skipped_updates}
	 */
	public final int skippedUpdates() {
		return this.skippedUpdates;
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

		generator.writeKey("successful_downloads");
		generator.write(this.successfulDownloads);

		generator.writeKey("failed_downloads");
		generator.write(this.failedDownloads);

		generator.writeKey("total_download_time");
		generator.write(this.totalDownloadTime);

		generator.writeKey("database_count");
		generator.write(this.databaseCount);

		generator.writeKey("skipped_updates");
		generator.write(this.skippedUpdates);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GeoIpDownloadStatistics}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GeoIpDownloadStatistics> {
		private Integer successfulDownloads;

		private Integer failedDownloads;

		private Integer totalDownloadTime;

		private Integer databaseCount;

		private Integer skippedUpdates;

		/**
		 * Required - Total number of successful database downloads.
		 * <p>
		 * API name: {@code successful_downloads}
		 */
		public final Builder successfulDownloads(int value) {
			this.successfulDownloads = value;
			return this;
		}

		/**
		 * Required - Total number of failed database downloads.
		 * <p>
		 * API name: {@code failed_downloads}
		 */
		public final Builder failedDownloads(int value) {
			this.failedDownloads = value;
			return this;
		}

		/**
		 * Required - Total milliseconds spent downloading databases.
		 * <p>
		 * API name: {@code total_download_time}
		 */
		public final Builder totalDownloadTime(int value) {
			this.totalDownloadTime = value;
			return this;
		}

		/**
		 * Required - Current number of databases available for use.
		 * <p>
		 * API name: {@code database_count}
		 */
		public final Builder databaseCount(int value) {
			this.databaseCount = value;
			return this;
		}

		/**
		 * Required - Total number of database updates skipped.
		 * <p>
		 * API name: {@code skipped_updates}
		 */
		public final Builder skippedUpdates(int value) {
			this.skippedUpdates = value;
			return this;
		}

		/**
		 * Builds a {@link GeoIpDownloadStatistics}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GeoIpDownloadStatistics build() {
			_checkSingleUse();

			return new GeoIpDownloadStatistics(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GeoIpDownloadStatistics}
	 */
	public static final JsonpDeserializer<GeoIpDownloadStatistics> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, GeoIpDownloadStatistics::setupGeoIpDownloadStatisticsDeserializer);

	protected static void setupGeoIpDownloadStatisticsDeserializer(
			ObjectDeserializer<GeoIpDownloadStatistics.Builder> op) {

		op.add(Builder::successfulDownloads, JsonpDeserializer.integerDeserializer(), "successful_downloads");
		op.add(Builder::failedDownloads, JsonpDeserializer.integerDeserializer(), "failed_downloads");
		op.add(Builder::totalDownloadTime, JsonpDeserializer.integerDeserializer(), "total_download_time");
		op.add(Builder::databaseCount, JsonpDeserializer.integerDeserializer(), "database_count");
		op.add(Builder::skippedUpdates, JsonpDeserializer.integerDeserializer(), "skipped_updates");

	}

}
