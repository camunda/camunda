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

package org.opensearch.client.opensearch.cluster.stats;

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

// typedef: cluster.stats.ClusterNetworkTypes

@JsonpDeserializable
public class ClusterNetworkTypes implements JsonpSerializable {
	private final Map<String, Integer> httpTypes;

	private final Map<String, Integer> transportTypes;

	// ---------------------------------------------------------------------------------------------

	private ClusterNetworkTypes(Builder builder) {

		this.httpTypes = ApiTypeHelper.unmodifiableRequired(builder.httpTypes, this, "httpTypes");
		this.transportTypes = ApiTypeHelper.unmodifiableRequired(builder.transportTypes, this, "transportTypes");

	}

	public static ClusterNetworkTypes of(Function<Builder, ObjectBuilder<ClusterNetworkTypes>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code http_types}
	 */
	public final Map<String, Integer> httpTypes() {
		return this.httpTypes;
	}

	/**
	 * Required - API name: {@code transport_types}
	 */
	public final Map<String, Integer> transportTypes() {
		return this.transportTypes;
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

		if (ApiTypeHelper.isDefined(this.httpTypes)) {
			generator.writeKey("http_types");
			generator.writeStartObject();
			for (Map.Entry<String, Integer> item0 : this.httpTypes.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.transportTypes)) {
			generator.writeKey("transport_types");
			generator.writeStartObject();
			for (Map.Entry<String, Integer> item0 : this.transportTypes.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ClusterNetworkTypes}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ClusterNetworkTypes> {
		private Map<String, Integer> httpTypes;

		private Map<String, Integer> transportTypes;

		/**
		 * Required - API name: {@code http_types}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>httpTypes</code>.
		 */
		public final Builder httpTypes(Map<String, Integer> map) {
			this.httpTypes = _mapPutAll(this.httpTypes, map);
			return this;
		}

		/**
		 * Required - API name: {@code http_types}
		 * <p>
		 * Adds an entry to <code>httpTypes</code>.
		 */
		public final Builder httpTypes(String key, Integer value) {
			this.httpTypes = _mapPut(this.httpTypes, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code transport_types}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>transportTypes</code>.
		 */
		public final Builder transportTypes(Map<String, Integer> map) {
			this.transportTypes = _mapPutAll(this.transportTypes, map);
			return this;
		}

		/**
		 * Required - API name: {@code transport_types}
		 * <p>
		 * Adds an entry to <code>transportTypes</code>.
		 */
		public final Builder transportTypes(String key, Integer value) {
			this.transportTypes = _mapPut(this.transportTypes, key, value);
			return this;
		}

		/**
		 * Builds a {@link ClusterNetworkTypes}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ClusterNetworkTypes build() {
			_checkSingleUse();

			return new ClusterNetworkTypes(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ClusterNetworkTypes}
	 */
	public static final JsonpDeserializer<ClusterNetworkTypes> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ClusterNetworkTypes::setupClusterNetworkTypesDeserializer);

	protected static void setupClusterNetworkTypesDeserializer(ObjectDeserializer<ClusterNetworkTypes.Builder> op) {

		op.add(Builder::httpTypes, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.integerDeserializer()),
				"http_types");
		op.add(Builder::transportTypes,
				JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.integerDeserializer()), "transport_types");

	}

}
