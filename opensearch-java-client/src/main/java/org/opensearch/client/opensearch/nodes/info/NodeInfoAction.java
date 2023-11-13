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

// typedef: nodes.info.NodeInfoAction

@JsonpDeserializable
public class NodeInfoAction implements JsonpSerializable {
	private final String destructiveRequiresName;

	// ---------------------------------------------------------------------------------------------

	private NodeInfoAction(Builder builder) {

		this.destructiveRequiresName = ApiTypeHelper.requireNonNull(builder.destructiveRequiresName, this,
				"destructiveRequiresName");

	}

	public static NodeInfoAction of(Function<Builder, ObjectBuilder<NodeInfoAction>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code destructive_requires_name}
	 */
	public final String destructiveRequiresName() {
		return this.destructiveRequiresName;
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

		generator.writeKey("destructive_requires_name");
		generator.write(this.destructiveRequiresName);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeInfoAction}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeInfoAction> {
		private String destructiveRequiresName;

		/**
		 * Required - API name: {@code destructive_requires_name}
		 */
		public final Builder destructiveRequiresName(String value) {
			this.destructiveRequiresName = value;
			return this;
		}

		/**
		 * Builds a {@link NodeInfoAction}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeInfoAction build() {
			_checkSingleUse();

			return new NodeInfoAction(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeInfoAction}
	 */
	public static final JsonpDeserializer<NodeInfoAction> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodeInfoAction::setupNodeInfoActionDeserializer);

	protected static void setupNodeInfoActionDeserializer(ObjectDeserializer<NodeInfoAction.Builder> op) {

		op.add(Builder::destructiveRequiresName, JsonpDeserializer.stringDeserializer(), "destructive_requires_name");

	}

}
