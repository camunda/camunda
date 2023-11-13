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

package org.opensearch.client.opensearch.tasks;

import org.opensearch.client.opensearch._types.ErrorCause;
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
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: tasks.list.Response


@JsonpDeserializable
public class ListResponse implements JsonpSerializable {
	private final List<ErrorCause> nodeFailures;

	private final Map<String, TaskExecutingNode> nodes;

	private final Map<String, Info> tasks;

	// ---------------------------------------------------------------------------------------------

	protected ListResponse(AbstractBuilder<?> builder) {

		this.nodeFailures = ApiTypeHelper.unmodifiable(builder.nodeFailures);
		this.nodes = ApiTypeHelper.unmodifiable(builder.nodes);
		this.tasks = ApiTypeHelper.unmodifiable(builder.tasks);

	}

	public static ListResponse listResponseOf(Function<Builder, ObjectBuilder<ListResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code node_failures}
	 */
	public final List<ErrorCause> nodeFailures() {
		return this.nodeFailures;
	}

	/**
	 * API name: {@code nodes}
	 */
	public final Map<String, TaskExecutingNode> nodes() {
		return this.nodes;
	}

	/**
	 * API name: {@code tasks}
	 */
	public final Map<String, Info> tasks() {
		return this.tasks;
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

		if (ApiTypeHelper.isDefined(this.nodeFailures)) {
			generator.writeKey("node_failures");
			generator.writeStartArray();
			for (ErrorCause item0 : this.nodeFailures) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.nodes)) {
			generator.writeKey("nodes");
			generator.writeStartObject();
			for (Map.Entry<String, TaskExecutingNode> item0 : this.nodes.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.tasks)) {
			generator.writeKey("tasks");
			generator.writeStartObject();
			for (Map.Entry<String, Info> item0 : this.tasks.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ListResponse}.
	 */

	public static class Builder extends ListResponse.AbstractBuilder<Builder> implements ObjectBuilder<ListResponse> {
		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link ListResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ListResponse build() {
			_checkSingleUse();

			return new ListResponse(this);
		}
	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				ObjectBuilderBase {
		@Nullable
		private List<ErrorCause> nodeFailures;

		@Nullable
		private Map<String, TaskExecutingNode> nodes;

		@Nullable
		private Map<String, Info> tasks;

		/**
		 * API name: {@code node_failures}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>nodeFailures</code>.
		 */
		public final BuilderT nodeFailures(List<ErrorCause> list) {
			this.nodeFailures = _listAddAll(this.nodeFailures, list);
			return self();
		}

		/**
		 * API name: {@code node_failures}
		 * <p>
		 * Adds one or more values to <code>nodeFailures</code>.
		 */
		public final BuilderT nodeFailures(ErrorCause value, ErrorCause... values) {
			this.nodeFailures = _listAdd(this.nodeFailures, value, values);
			return self();
		}

		/**
		 * API name: {@code node_failures}
		 * <p>
		 * Adds a value to <code>nodeFailures</code> using a builder lambda.
		 */
		public final BuilderT nodeFailures(Function<ErrorCause.Builder, ObjectBuilder<ErrorCause>> fn) {
			return nodeFailures(fn.apply(new ErrorCause.Builder()).build());
		}

		/**
		 * API name: {@code nodes}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>nodes</code>.
		 */
		public final BuilderT nodes(Map<String, TaskExecutingNode> map) {
			this.nodes = _mapPutAll(this.nodes, map);
			return self();
		}

		/**
		 * API name: {@code nodes}
		 * <p>
		 * Adds an entry to <code>nodes</code>.
		 */
		public final BuilderT nodes(String key, TaskExecutingNode value) {
			this.nodes = _mapPut(this.nodes, key, value);
			return self();
		}

		/**
		 * API name: {@code nodes}
		 * <p>
		 * Adds an entry to <code>nodes</code> using a builder lambda.
		 */
		public final BuilderT nodes(String key,
				Function<TaskExecutingNode.Builder, ObjectBuilder<TaskExecutingNode>> fn) {
			return nodes(key, fn.apply(new TaskExecutingNode.Builder()).build());
		}

		/**
		 * API name: {@code tasks}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>tasks</code>.
		 */
		public final BuilderT tasks(Map<String, Info> map) {
			this.tasks = _mapPutAll(this.tasks, map);
			return self();
		}

		/**
		 * API name: {@code tasks}
		 * <p>
		 * Adds an entry to <code>tasks</code>.
		 */
		public final BuilderT tasks(String key, Info value) {
			this.tasks = _mapPut(this.tasks, key, value);
			return self();
		}

		/**
		 * API name: {@code tasks}
		 * <p>
		 * Adds an entry to <code>tasks</code> using a builder lambda.
		 */
		public final BuilderT tasks(String key, Function<Info.Builder, ObjectBuilder<Info>> fn) {
			return tasks(key, fn.apply(new Info.Builder()).build());
		}

		protected abstract BuilderT self();

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ListResponse}
	 */
	public static final JsonpDeserializer<ListResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ListResponse::setupListResponseDeserializer);

	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupListResponseDeserializer(
			ObjectDeserializer<BuilderT> op) {

		op.add(AbstractBuilder::nodeFailures, JsonpDeserializer.arrayDeserializer(ErrorCause._DESERIALIZER),
				"node_failures");
		op.add(AbstractBuilder::nodes, JsonpDeserializer.stringMapDeserializer(TaskExecutingNode._DESERIALIZER),
				"nodes");
		op.add(AbstractBuilder::tasks, JsonpDeserializer.stringMapDeserializer(Info._DESERIALIZER), "tasks");

	}

}
