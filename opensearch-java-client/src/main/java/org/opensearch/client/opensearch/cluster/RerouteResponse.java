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

package org.opensearch.client.opensearch.cluster;

import org.opensearch.client.opensearch.cluster.reroute.RerouteExplanation;
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
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: cluster.reroute.Response

@JsonpDeserializable
public class RerouteResponse implements JsonpSerializable {
	private final List<RerouteExplanation> explanations;

	private final JsonData state;

	// ---------------------------------------------------------------------------------------------

	private RerouteResponse(Builder builder) {

		this.explanations = ApiTypeHelper.unmodifiable(builder.explanations);
		this.state = ApiTypeHelper.requireNonNull(builder.state, this, "state");

	}

	public static RerouteResponse of(Function<Builder, ObjectBuilder<RerouteResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code explanations}
	 */
	public final List<RerouteExplanation> explanations() {
		return this.explanations;
	}

	/**
	 * Required - There aren't any guarantees on the output/structure of the raw
	 * cluster state. Here you will find the internal representation of the cluster,
	 * which can differ from the external representation.
	 * <p>
	 * API name: {@code state}
	 */
	public final JsonData state() {
		return this.state;
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

		if (ApiTypeHelper.isDefined(this.explanations)) {
			generator.writeKey("explanations");
			generator.writeStartArray();
			for (RerouteExplanation item0 : this.explanations) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("state");
		this.state.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RerouteResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RerouteResponse> {
		@Nullable
		private List<RerouteExplanation> explanations;

		private JsonData state;

		/**
		 * API name: {@code explanations}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>explanations</code>.
		 */
		public final Builder explanations(List<RerouteExplanation> list) {
			this.explanations = _listAddAll(this.explanations, list);
			return this;
		}

		/**
		 * API name: {@code explanations}
		 * <p>
		 * Adds one or more values to <code>explanations</code>.
		 */
		public final Builder explanations(RerouteExplanation value, RerouteExplanation... values) {
			this.explanations = _listAdd(this.explanations, value, values);
			return this;
		}

		/**
		 * API name: {@code explanations}
		 * <p>
		 * Adds a value to <code>explanations</code> using a builder lambda.
		 */
		public final Builder explanations(Function<RerouteExplanation.Builder, ObjectBuilder<RerouteExplanation>> fn) {
			return explanations(fn.apply(new RerouteExplanation.Builder()).build());
		}

		/**
		 * Required - There aren't any guarantees on the output/structure of the raw
		 * cluster state. Here you will find the internal representation of the cluster,
		 * which can differ from the external representation.
		 * <p>
		 * API name: {@code state}
		 */
		public final Builder state(JsonData value) {
			this.state = value;
			return this;
		}

		/**
		 * Builds a {@link RerouteResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RerouteResponse build() {
			_checkSingleUse();

			return new RerouteResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link RerouteResponse}
	 */
	public static final JsonpDeserializer<RerouteResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			RerouteResponse::setupRerouteResponseDeserializer);

	protected static void setupRerouteResponseDeserializer(ObjectDeserializer<RerouteResponse.Builder> op) {

		op.add(Builder::explanations, JsonpDeserializer.arrayDeserializer(RerouteExplanation._DESERIALIZER),
				"explanations");
		op.add(Builder::state, JsonData._DESERIALIZER, "state");

	}

}
