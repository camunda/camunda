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

package org.opensearch.client.opensearch.shutdown;

import org.opensearch.client.opensearch.shutdown.get_node.NodeShutdownStatus;
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

// typedef: shutdown.get_node.Response

@JsonpDeserializable
public class GetNodeResponse implements JsonpSerializable {
	private final List<NodeShutdownStatus> nodes;

	// ---------------------------------------------------------------------------------------------

	private GetNodeResponse(Builder builder) {

		this.nodes = ApiTypeHelper.unmodifiableRequired(builder.nodes, this, "nodes");

	}

	public static GetNodeResponse of(Function<Builder, ObjectBuilder<GetNodeResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code nodes}
	 */
	public final List<NodeShutdownStatus> nodes() {
		return this.nodes;
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

		if (ApiTypeHelper.isDefined(this.nodes)) {
			generator.writeKey("nodes");
			generator.writeStartArray();
			for (NodeShutdownStatus item0 : this.nodes) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GetNodeResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GetNodeResponse> {
		private List<NodeShutdownStatus> nodes;

		/**
		 * Required - API name: {@code nodes}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>nodes</code>.
		 */
		public final Builder nodes(List<NodeShutdownStatus> list) {
			this.nodes = _listAddAll(this.nodes, list);
			return this;
		}

		/**
		 * Required - API name: {@code nodes}
		 * <p>
		 * Adds one or more values to <code>nodes</code>.
		 */
		public final Builder nodes(NodeShutdownStatus value, NodeShutdownStatus... values) {
			this.nodes = _listAdd(this.nodes, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code nodes}
		 * <p>
		 * Adds a value to <code>nodes</code> using a builder lambda.
		 */
		public final Builder nodes(Function<NodeShutdownStatus.Builder, ObjectBuilder<NodeShutdownStatus>> fn) {
			return nodes(fn.apply(new NodeShutdownStatus.Builder()).build());
		}

		/**
		 * Builds a {@link GetNodeResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GetNodeResponse build() {
			_checkSingleUse();

			return new GetNodeResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GetNodeResponse}
	 */
	public static final JsonpDeserializer<GetNodeResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			GetNodeResponse::setupGetNodeResponseDeserializer);

	protected static void setupGetNodeResponseDeserializer(ObjectDeserializer<GetNodeResponse.Builder> op) {

		op.add(Builder::nodes, JsonpDeserializer.arrayDeserializer(NodeShutdownStatus._DESERIALIZER), "nodes");

	}

}
