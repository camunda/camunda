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

package org.opensearch.client.opensearch._types.aggregations;

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

// typedef: _types.aggregations.MutualInformationHeuristic

@JsonpDeserializable
public class MutualInformationHeuristic implements JsonpSerializable {
	@Nullable
	private final Boolean backgroundIsSuperset;

	@Nullable
	private final Boolean includeNegatives;

	// ---------------------------------------------------------------------------------------------

	private MutualInformationHeuristic(Builder builder) {

		this.backgroundIsSuperset = builder.backgroundIsSuperset;
		this.includeNegatives = builder.includeNegatives;

	}

	public static MutualInformationHeuristic of(Function<Builder, ObjectBuilder<MutualInformationHeuristic>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code background_is_superset}
	 */
	@Nullable
	public final Boolean backgroundIsSuperset() {
		return this.backgroundIsSuperset;
	}

	/**
	 * API name: {@code include_negatives}
	 */
	@Nullable
	public final Boolean includeNegatives() {
		return this.includeNegatives;
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

		if (this.backgroundIsSuperset != null) {
			generator.writeKey("background_is_superset");
			generator.write(this.backgroundIsSuperset);

		}
		if (this.includeNegatives != null) {
			generator.writeKey("include_negatives");
			generator.write(this.includeNegatives);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link MutualInformationHeuristic}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<MutualInformationHeuristic> {
		@Nullable
		private Boolean backgroundIsSuperset;

		@Nullable
		private Boolean includeNegatives;

		/**
		 * API name: {@code background_is_superset}
		 */
		public final Builder backgroundIsSuperset(@Nullable Boolean value) {
			this.backgroundIsSuperset = value;
			return this;
		}

		/**
		 * API name: {@code include_negatives}
		 */
		public final Builder includeNegatives(@Nullable Boolean value) {
			this.includeNegatives = value;
			return this;
		}

		/**
		 * Builds a {@link MutualInformationHeuristic}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public MutualInformationHeuristic build() {
			_checkSingleUse();

			return new MutualInformationHeuristic(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link MutualInformationHeuristic}
	 */
	public static final JsonpDeserializer<MutualInformationHeuristic> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, MutualInformationHeuristic::setupMutualInformationHeuristicDeserializer);

	protected static void setupMutualInformationHeuristicDeserializer(
			ObjectDeserializer<MutualInformationHeuristic.Builder> op) {

		op.add(Builder::backgroundIsSuperset, JsonpDeserializer.booleanDeserializer(), "background_is_superset");
		op.add(Builder::includeNegatives, JsonpDeserializer.booleanDeserializer(), "include_negatives");

	}

}
