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

package org.opensearch.client.opensearch.core.rank_eval;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: _global.rank_eval.RankEvalMetricExpectedReciprocalRank

/**
 * Expected Reciprocal Rank (ERR)
 *
 */
@JsonpDeserializable
public class RankEvalMetricExpectedReciprocalRank extends RankEvalMetricBase {
	private final int maximumRelevance;

	// ---------------------------------------------------------------------------------------------

	private RankEvalMetricExpectedReciprocalRank(Builder builder) {
		super(builder);

		this.maximumRelevance = ApiTypeHelper.requireNonNull(builder.maximumRelevance, this, "maximumRelevance");

	}

	public static RankEvalMetricExpectedReciprocalRank of(
			Function<Builder, ObjectBuilder<RankEvalMetricExpectedReciprocalRank>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - The highest relevance grade used in the user-supplied relevance
	 * judgments.
	 * <p>
	 * API name: {@code maximum_relevance}
	 */
	public final int maximumRelevance() {
		return this.maximumRelevance;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("maximum_relevance");
		generator.write(this.maximumRelevance);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RankEvalMetricExpectedReciprocalRank}.
	 */

	public static class Builder extends RankEvalMetricBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<RankEvalMetricExpectedReciprocalRank> {
		private Integer maximumRelevance;

		/**
		 * Required - The highest relevance grade used in the user-supplied relevance
		 * judgments.
		 * <p>
		 * API name: {@code maximum_relevance}
		 */
		public final Builder maximumRelevance(int value) {
			this.maximumRelevance = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link RankEvalMetricExpectedReciprocalRank}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RankEvalMetricExpectedReciprocalRank build() {
			_checkSingleUse();

			return new RankEvalMetricExpectedReciprocalRank(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RankEvalMetricExpectedReciprocalRank}
	 */
	public static final JsonpDeserializer<RankEvalMetricExpectedReciprocalRank> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new,
					RankEvalMetricExpectedReciprocalRank::setupRankEvalMetricExpectedReciprocalRankDeserializer);

	protected static void setupRankEvalMetricExpectedReciprocalRankDeserializer(
			ObjectDeserializer<RankEvalMetricExpectedReciprocalRank.Builder> op) {
		setupRankEvalMetricBaseDeserializer(op);
		op.add(Builder::maximumRelevance, JsonpDeserializer.integerDeserializer(), "maximum_relevance");

	}

}
