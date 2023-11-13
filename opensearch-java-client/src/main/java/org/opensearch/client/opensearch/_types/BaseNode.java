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

package org.opensearch.client.opensearch._types;

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

// typedef: _spec_utils.BaseNode


public abstract class BaseNode implements JsonpSerializable {
	private final Map<String, String> attributes;

	private final String host;

	private final String ip;

	private final String name;

	private final List<NodeRole> roles;

	private final String transportAddress;

	// ---------------------------------------------------------------------------------------------

	protected BaseNode(AbstractBuilder<?> builder) {

		this.attributes = ApiTypeHelper.unmodifiable(builder.attributes);
		this.host = builder.host;
		this.ip = builder.ip;
		this.name = builder.name;
		this.roles = ApiTypeHelper.unmodifiable(builder.roles);
		this.transportAddress = builder.transportAddress;

	}

	/**
	 * API name: {@code attributes}
	 */
	public final Map<String, String> attributes() {
		return this.attributes;
	}

	/**
	 * API name: {@code host}
	 */
	public final String host() {
		return this.host;
	}

	/**
	 * API name: {@code ip}
	 */
	public final String ip() {
		return this.ip;
	}

	/**
	 * API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * API name: {@code roles}
	 */
	public final List<NodeRole> roles() {
		return this.roles;
	}

	/**
	 * API name: {@code transport_address}
	 */
	public final String transportAddress() {
		return this.transportAddress;
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

		if (ApiTypeHelper.isDefined(this.attributes)) {
			generator.writeKey("attributes");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.attributes.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}
		generator.writeKey("host");
		generator.write(this.host);

		generator.writeKey("ip");
		generator.write(this.ip);

		generator.writeKey("name");
		generator.write(this.name);

		if (ApiTypeHelper.isDefined(this.roles)) {
			generator.writeKey("roles");
			generator.writeStartArray();
			for (NodeRole item0 : this.roles) {
				item0.serialize(generator, mapper);
			}
			generator.writeEnd();

		}
		generator.writeKey("transport_address");
		generator.write(this.transportAddress);

	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				ObjectBuilderBase {
		@Nullable			
		private Map<String, String> attributes;

		@Nullable
		private String host;

		@Nullable
		private String ip;

		@Nullable
		private String name;

		@Nullable
		private List<NodeRole> roles;

		@Nullable
		private String transportAddress;

		/**
		 * API name: {@code attributes}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>attributes</code>.
		 */
		public final BuilderT attributes(Map<String, String> map) {
			this.attributes = _mapPutAll(this.attributes, map);
			return self();
		}

		/**
		 * API name: {@code attributes}
		 * <p>
		 * Adds an entry to <code>attributes</code>.
		 */
		public final BuilderT attributes(String key, String value) {
			this.attributes = _mapPut(this.attributes, key, value);
			return self();
		}

		/**
		 * API name: {@code host}
		 */
		public final BuilderT host(String value) {
			this.host = value;
			return self();
		}

		/**
		 * API name: {@code ip}
		 */
		public final BuilderT ip(String value) {
			this.ip = value;
			return self();
		}

		/**
		 * API name: {@code name}
		 */
		public final BuilderT name(String value) {
			this.name = value;
			return self();
		}

		/**
		 * API name: {@code roles}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>roles</code>.
		 */
		public final BuilderT roles(List<NodeRole> list) {
			this.roles = _listAddAll(this.roles, list);
			return self();
		}

		/**
		 * API name: {@code roles}
		 * <p>
		 * Adds one or more values to <code>roles</code>.
		 */
		public final BuilderT roles(NodeRole value, NodeRole... values) {
			this.roles = _listAdd(this.roles, value, values);
			return self();
		}

		/**
		 * API name: {@code transport_address}
		 */
		public final BuilderT transportAddress(String value) {
			this.transportAddress = value;
			return self();
		}

		protected abstract BuilderT self();

	}

	// ---------------------------------------------------------------------------------------------
	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupBaseNodeDeserializer(
			ObjectDeserializer<BuilderT> op) {

		op.add(AbstractBuilder::attributes,
				JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()), "attributes");
		op.add(AbstractBuilder::host, JsonpDeserializer.stringDeserializer(), "host");
		op.add(AbstractBuilder::ip, JsonpDeserializer.stringDeserializer(), "ip");
		op.add(AbstractBuilder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(AbstractBuilder::roles, JsonpDeserializer.arrayDeserializer(NodeRole._DESERIALIZER), "roles");
		op.add(AbstractBuilder::transportAddress, JsonpDeserializer.stringDeserializer(), "transport_address");

	}

}
