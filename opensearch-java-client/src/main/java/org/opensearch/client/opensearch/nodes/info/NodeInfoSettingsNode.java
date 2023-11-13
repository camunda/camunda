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
import javax.annotation.Nullable;

// typedef: nodes.info.NodeInfoSettingsNode

@JsonpDeserializable
public class NodeInfoSettingsNode implements JsonpSerializable {
	private final String name;

	@Nullable
	private final Map<String, JsonData> attr;

	@Nullable
	private final String maxLocalStorageNodes;

	// ---------------------------------------------------------------------------------------------

	private NodeInfoSettingsNode(Builder builder) {

		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.attr = ApiTypeHelper.unmodifiable(builder.attr);
		this.maxLocalStorageNodes = builder.maxLocalStorageNodes;

	}

	public static NodeInfoSettingsNode of(Function<Builder, ObjectBuilder<NodeInfoSettingsNode>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * API name: {@code attr}
	 */
	@Nullable
	public final Map<String, JsonData> attr() {
		return this.attr;
	}

	/**
	 * API name: {@code max_local_storage_nodes}
	 */
	@Nullable
	public final String maxLocalStorageNodes() {
		return this.maxLocalStorageNodes;
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

		generator.writeKey("name");
		generator.write(this.name);

		if (ApiTypeHelper.isDefined(this.attr)) {
			generator.writeKey("attr");
			generator.writeStartObject();
			for (Map.Entry<String, JsonData> item0 : this.attr.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.maxLocalStorageNodes != null) {
			generator.writeKey("max_local_storage_nodes");
			generator.write(this.maxLocalStorageNodes);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeInfoSettingsNode}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeInfoSettingsNode> {
		private String name;

		@Nullable
		private Map<String, JsonData> attr;

		@Nullable
		private String maxLocalStorageNodes;

		/**
		 * Required - API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * Required - API name: {@code attr}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>attr</code>.
		 */
		public final Builder attr(Map<String, JsonData> map) {
			this.attr = _mapPutAll(this.attr, map);
			return this;
		}

		/**
		 * API name: {@code attr}
		 * <p>
		 * Adds an entry to <code>attr</code>.
		 */
		public final Builder attr(String key, JsonData value) {
			this.attr = _mapPut(this.attr, key, value);
			return this;
		}

		/**
		 * API name: {@code max_local_storage_nodes}
		 */
		public final Builder maxLocalStorageNodes(@Nullable String value) {
			this.maxLocalStorageNodes = value;
			return this;
		}

		/**
		 * Builds a {@link NodeInfoSettingsNode}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeInfoSettingsNode build() {
			_checkSingleUse();

			return new NodeInfoSettingsNode(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeInfoSettingsNode}
	 */
	public static final JsonpDeserializer<NodeInfoSettingsNode> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, NodeInfoSettingsNode::setupNodeInfoSettingsNodeDeserializer);

	protected static void setupNodeInfoSettingsNodeDeserializer(ObjectDeserializer<NodeInfoSettingsNode.Builder> op) {

		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::attr, JsonpDeserializer.stringMapDeserializer(JsonData._DESERIALIZER), "attr");
		op.add(Builder::maxLocalStorageNodes, JsonpDeserializer.stringDeserializer(), "max_local_storage_nodes");

	}

}
