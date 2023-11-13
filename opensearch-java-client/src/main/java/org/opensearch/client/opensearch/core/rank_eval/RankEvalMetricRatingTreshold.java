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

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectDeserializer;
import jakarta.json.stream.JsonGenerator;
import javax.annotation.Nullable;

// typedef: _global.rank_eval.RankEvalMetricRatingTreshold



public abstract class RankEvalMetricRatingTreshold extends RankEvalMetricBase {
	@Nullable
	private final Integer relevantRatingThreshold;

	// ---------------------------------------------------------------------------------------------

	protected RankEvalMetricRatingTreshold(AbstractBuilder<?> builder) {
		super(builder);

		this.relevantRatingThreshold = builder.relevantRatingThreshold;

	}

	/**
	 * Sets the rating threshold above which documents are considered to be
	 * &quot;relevant&quot;.
	 * <p>
	 * API name: {@code relevant_rating_threshold}
	 */
	@Nullable
	public final Integer relevantRatingThreshold() {
		return this.relevantRatingThreshold;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.relevantRatingThreshold != null) {
			generator.writeKey("relevant_rating_threshold");
			generator.write(this.relevantRatingThreshold);

		}

	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				RankEvalMetricBase.AbstractBuilder<BuilderT> {
		@Nullable
		private Integer relevantRatingThreshold;

		/**
		 * Sets the rating threshold above which documents are considered to be
		 * &quot;relevant&quot;.
		 * <p>
		 * API name: {@code relevant_rating_threshold}
		 */
		public final BuilderT relevantRatingThreshold(@Nullable Integer value) {
			this.relevantRatingThreshold = value;
			return self();
		}

	}

	// ---------------------------------------------------------------------------------------------
	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupRankEvalMetricRatingTresholdDeserializer(
			ObjectDeserializer<BuilderT> op) {
		setupRankEvalMetricBaseDeserializer(op);
		op.add(AbstractBuilder::relevantRatingThreshold, JsonpDeserializer.integerDeserializer(),
				"relevant_rating_threshold");

	}

}
