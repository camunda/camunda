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

package org.opensearch.client.opensearch.core;

import org.opensearch.client.opensearch.core.rank_eval.RankEvalMetricDetail;
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

import java.util.Map;
import java.util.function.Function;

// typedef: _global.rank_eval.Response

@JsonpDeserializable
public class RankEvalResponse implements JsonpSerializable {
	private final double metricScore;

	private final Map<String, RankEvalMetricDetail> details;

	private final Map<String, JsonData> failures;

	// ---------------------------------------------------------------------------------------------

	private RankEvalResponse(Builder builder) {

		this.metricScore = ApiTypeHelper.requireNonNull(builder.metricScore, this, "metricScore");
		this.details = ApiTypeHelper.unmodifiableRequired(builder.details, this, "details");
		this.failures = ApiTypeHelper.unmodifiableRequired(builder.failures, this, "failures");

	}

	public static RankEvalResponse of(Function<Builder, ObjectBuilder<RankEvalResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - The overall evaluation quality calculated by the defined metric
	 * <p>
	 * API name: {@code metric_score}
	 */
	public final double metricScore() {
		return this.metricScore;
	}

	/**
	 * Required - The details section contains one entry for every query in the
	 * original requests section, keyed by the search request id
	 * <p>
	 * API name: {@code details}
	 */
	public final Map<String, RankEvalMetricDetail> details() {
		return this.details;
	}

	/**
	 * Required - API name: {@code failures}
	 */
	public final Map<String, JsonData> failures() {
		return this.failures;
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

		generator.writeKey("metric_score");
		generator.write(this.metricScore);

		if (ApiTypeHelper.isDefined(this.details)) {
			generator.writeKey("details");
			generator.writeStartObject();
			for (Map.Entry<String, RankEvalMetricDetail> item0 : this.details.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.failures)) {
			generator.writeKey("failures");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.failures.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RankEvalResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RankEvalResponse> {
		private Double metricScore;

		private Map<String, RankEvalMetricDetail> details;

		private Map<String, JsonData> failures;

		/**
		 * Required - The overall evaluation quality calculated by the defined metric
		 * <p>
		 * API name: {@code metric_score}
		 */
		public final Builder metricScore(double value) {
			this.metricScore = value;
			return this;
		}

		/**
		 * Required - The details section contains one entry for every query in the
		 * original requests section, keyed by the search request id
		 * <p>
		 * API name: {@code details}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>details</code>.
		 */
		public final Builder details(Map<String, RankEvalMetricDetail> map) {
			this.details = _mapPutAll(this.details, map);
			return this;
		}

		/**
		 * Required - The details section contains one entry for every query in the
		 * original requests section, keyed by the search request id
		 * <p>
		 * API name: {@code details}
		 * <p>
		 * Adds an entry to <code>details</code>.
		 */
		public final Builder details(String key, RankEvalMetricDetail value) {
			this.details = _mapPut(this.details, key, value);
			return this;
		}

		/**
		 * Required - The details section contains one entry for every query in the
		 * original requests section, keyed by the search request id
		 * <p>
		 * API name: {@code details}
		 * <p>
		 * Adds an entry to <code>details</code> using a builder lambda.
		 */
		public final Builder details(String key,
				Function<RankEvalMetricDetail.Builder, ObjectBuilder<RankEvalMetricDetail>> fn) {
			return details(key, fn.apply(new RankEvalMetricDetail.Builder()).build());
		}

		/**
		 * Required - API name: {@code failures}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>failures</code>.
		 */
		public final Builder failures(Map<String, JsonData> map) {
			this.failures = _mapPutAll(this.failures, map);
			return this;
		}

		/**
		 * Required - API name: {@code failures}
		 * <p>
		 * Adds an entry to <code>failures</code>.
		 */
		public final Builder failures(String key, JsonData value) {
			this.failures = _mapPut(this.failures, key, value);
			return this;
		}

		/**
		 * Builds a {@link RankEvalResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RankEvalResponse build() {
			_checkSingleUse();

			return new RankEvalResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RankEvalResponse}
	 */
	public static final JsonpDeserializer<RankEvalResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			RankEvalResponse::setupRankEvalResponseDeserializer);

	protected static void setupRankEvalResponseDeserializer(ObjectDeserializer<RankEvalResponse.Builder> op) {

		op.add(Builder::metricScore, JsonpDeserializer.doubleDeserializer(), "metric_score");
		op.add(Builder::details, JsonpDeserializer.stringMapDeserializer(RankEvalMetricDetail._DESERIALIZER),
				"details");
		op.add(Builder::failures, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "failures");

	}

}
