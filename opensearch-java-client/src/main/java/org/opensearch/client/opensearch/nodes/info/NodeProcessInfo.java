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

// typedef: nodes.info.NodeProcessInfo

@JsonpDeserializable
public class NodeProcessInfo implements JsonpSerializable {
	private final long id;

	private final boolean mlockall;

	private final long refreshIntervalInMillis;

	// ---------------------------------------------------------------------------------------------

	private NodeProcessInfo(Builder builder) {

		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.mlockall = ApiTypeHelper.requireNonNull(builder.mlockall, this, "mlockall");
		this.refreshIntervalInMillis = ApiTypeHelper.requireNonNull(builder.refreshIntervalInMillis, this,
				"refreshIntervalInMillis");

	}

	public static NodeProcessInfo of(Function<Builder, ObjectBuilder<NodeProcessInfo>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - Process identifier (PID)
	 * <p>
	 * API name: {@code id}
	 */
	public final long id() {
		return this.id;
	}

	/**
	 * Required - Indicates if the process address space has been successfully
	 * locked in memory
	 * <p>
	 * API name: {@code mlockall}
	 */
	public final boolean mlockall() {
		return this.mlockall;
	}

	/**
	 * Required - Refresh interval for the process statistics
	 * <p>
	 * API name: {@code refresh_interval_in_millis}
	 */
	public final long refreshIntervalInMillis() {
		return this.refreshIntervalInMillis;
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

		generator.writeKey("id");
		generator.write(this.id);

		generator.writeKey("mlockall");
		generator.write(this.mlockall);

		generator.writeKey("refresh_interval_in_millis");
		generator.write(this.refreshIntervalInMillis);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeProcessInfo}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeProcessInfo> {
		private Long id;

		private Boolean mlockall;

		private Long refreshIntervalInMillis;

		/**
		 * Required - Process identifier (PID)
		 * <p>
		 * API name: {@code id}
		 */
		public final Builder id(long value) {
			this.id = value;
			return this;
		}

		/**
		 * Required - Indicates if the process address space has been successfully
		 * locked in memory
		 * <p>
		 * API name: {@code mlockall}
		 */
		public final Builder mlockall(boolean value) {
			this.mlockall = value;
			return this;
		}

		/**
		 * Required - Refresh interval for the process statistics
		 * <p>
		 * API name: {@code refresh_interval_in_millis}
		 */
		public final Builder refreshIntervalInMillis(long value) {
			this.refreshIntervalInMillis = value;
			return this;
		}

		/**
		 * Builds a {@link NodeProcessInfo}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeProcessInfo build() {
			_checkSingleUse();

			return new NodeProcessInfo(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeProcessInfo}
	 */
	public static final JsonpDeserializer<NodeProcessInfo> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodeProcessInfo::setupNodeProcessInfoDeserializer);

	protected static void setupNodeProcessInfoDeserializer(ObjectDeserializer<NodeProcessInfo.Builder> op) {

		op.add(Builder::id, JsonpDeserializer.longDeserializer(), "id");
		op.add(Builder::mlockall, JsonpDeserializer.booleanDeserializer(), "mlockall");
		op.add(Builder::refreshIntervalInMillis, JsonpDeserializer.longDeserializer(), "refresh_interval_in_millis");

	}

}
