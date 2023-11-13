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

package org.opensearch.client.opensearch.nodes.info;

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

import javax.annotation.Nullable;

// typedef: nodes.info.NodeInfoScript

@JsonpDeserializable
public class NodeInfoScript implements JsonpSerializable {
    @Nullable
	private final String allowedTypes;

	private final String disableMaxCompilationsRate;

	// ---------------------------------------------------------------------------------------------

	private NodeInfoScript(Builder builder) {

		this.allowedTypes = builder.allowedTypes;
		this.disableMaxCompilationsRate = ApiTypeHelper.requireNonNull(builder.disableMaxCompilationsRate, this,
				"disableMaxCompilationsRate");

	}

	public static NodeInfoScript of(Function<Builder, ObjectBuilder<NodeInfoScript>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code allowed_types}
	 */
	@Nullable
	public final String allowedTypes() {
		return this.allowedTypes;
	}

	/**
	 * Required - API name: {@code disable_max_compilations_rate}
	 */
	public final String disableMaxCompilationsRate() {
		return this.disableMaxCompilationsRate;
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
	    if (this.allowedTypes != null) {
    		generator.writeKey("allowed_types");
    		generator.write(this.allowedTypes);
		}

		generator.writeKey("disable_max_compilations_rate");
		generator.write(this.disableMaxCompilationsRate);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeInfoScript}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeInfoScript> {
	    @Nullable
	    private String allowedTypes;

		private String disableMaxCompilationsRate;

		/**
		 * API name: {@code allowed_types}
		 */
		public final Builder allowedTypes(String value) {
			this.allowedTypes = value;
			return this;
		}

		/**
		 * Required - API name: {@code disable_max_compilations_rate}
		 */
		public final Builder disableMaxCompilationsRate(String value) {
			this.disableMaxCompilationsRate = value;
			return this;
		}

		/**
		 * Builds a {@link NodeInfoScript}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeInfoScript build() {
			_checkSingleUse();

			return new NodeInfoScript(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeInfoScript}
	 */
	public static final JsonpDeserializer<NodeInfoScript> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodeInfoScript::setupNodeInfoScriptDeserializer);

	protected static void setupNodeInfoScriptDeserializer(ObjectDeserializer<NodeInfoScript.Builder> op) {

		op.add(Builder::allowedTypes, JsonpDeserializer.stringDeserializer(), "allowed_types");
		op.add(Builder::disableMaxCompilationsRate, JsonpDeserializer.stringDeserializer(),
				"disable_max_compilations_rate");

	}

}
