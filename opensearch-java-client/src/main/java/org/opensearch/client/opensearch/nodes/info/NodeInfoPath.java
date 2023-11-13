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
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: nodes.info.NodeInfoPath

@JsonpDeserializable
public class NodeInfoPath implements JsonpSerializable {
	private final String logs;

	private final String home;

	@Nullable
	private final List<String> repo;

	private final List<String> data;

	// ---------------------------------------------------------------------------------------------

	private NodeInfoPath(Builder builder) {

		this.logs = ApiTypeHelper.requireNonNull(builder.logs, this, "logs");
		this.home = ApiTypeHelper.requireNonNull(builder.home, this, "home");
		this.repo = ApiTypeHelper.unmodifiable(builder.repo);
		this.data = ApiTypeHelper.unmodifiable(builder.data);

	}

	public static NodeInfoPath of(Function<Builder, ObjectBuilder<NodeInfoPath>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code logs}
	 */
	public final String logs() {
		return this.logs;
	}

	/**
	 * Required - API name: {@code home}
	 */
	public final String home() {
		return this.home;
	}

	/**
	 * API name: {@code repo}
	 */
	public final List<String> repo() {
		return this.repo;
	}

	/**
	 * API name: {@code data}
	 */
	public final List<String> data() {
		return this.data;
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

		generator.writeKey("logs");
		generator.write(this.logs);

		generator.writeKey("home");
		generator.write(this.home);

		if (ApiTypeHelper.isDefined(this.repo)) {
			generator.writeKey("repo");
			generator.writeStartArray();
			for (String item0 : this.repo) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.data)) {
			generator.writeKey("data");
			generator.writeStartArray();
			for (String item0 : this.data) {
				generator.write(item0);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeInfoPath}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeInfoPath> {
		private String logs;

		private String home;

		private List<String> repo;

		@Nullable
		private List<String> data;

		/**
		 * Required - API name: {@code logs}
		 */
		public final Builder logs(String value) {
			this.logs = value;
			return this;
		}

		/**
		 * Required - API name: {@code home}
		 */
		public final Builder home(String value) {
			this.home = value;
			return this;
		}

		/**
		 * API name: {@code repo}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>repo</code>.
		 */
		public final Builder repo(List<String> list) {
			this.repo = _listAddAll(this.repo, list);
			return this;
		}

		/**
		 * Required - API name: {@code repo}
		 * <p>
		 * Adds one or more values to <code>repo</code>.
		 */
		public final Builder repo(String value, String... values) {
			this.repo = _listAdd(this.repo, value, values);
			return this;
		}

		/**
		 * API name: {@code data}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>data</code>.
		 */
		public final Builder data(List<String> list) {
			this.data = _listAddAll(this.data, list);
			return this;
		}

		/**
		 * API name: {@code data}
		 * <p>
		 * Adds one or more values to <code>data</code>.
		 */
		public final Builder data(String value, String... values) {
			this.data = _listAdd(this.data, value, values);
			return this;
		}

		/**
		 * Builds a {@link NodeInfoPath}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeInfoPath build() {
			_checkSingleUse();

			return new NodeInfoPath(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeInfoPath}
	 */
	public static final JsonpDeserializer<NodeInfoPath> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodeInfoPath::setupNodeInfoPathDeserializer);

	protected static void setupNodeInfoPathDeserializer(ObjectDeserializer<NodeInfoPath.Builder> op) {

		op.add(Builder::logs, JsonpDeserializer.stringDeserializer(), "logs");
		op.add(Builder::home, JsonpDeserializer.stringDeserializer(), "home");
		op.add(Builder::repo, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "repo");
		op.add(Builder::data, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "data");

	}

}
