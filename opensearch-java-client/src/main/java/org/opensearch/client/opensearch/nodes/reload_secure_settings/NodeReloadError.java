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

package org.opensearch.client.opensearch.nodes.reload_secure_settings;

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
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: nodes.reload_secure_settings.NodeReloadError

@JsonpDeserializable
public class NodeReloadError implements JsonpSerializable {
	private final String name;

	@Nullable
	private final ErrorCause reloadException;

	// ---------------------------------------------------------------------------------------------

	private NodeReloadError(Builder builder) {

		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
		this.reloadException = builder.reloadException;

	}

	public static NodeReloadError of(Function<Builder, ObjectBuilder<NodeReloadError>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code name}
	 */
	public final String name() {
		return this.name;
	}

	/**
	 * API name: {@code reload_exception}
	 */
	@Nullable
	public final ErrorCause reloadException() {
		return this.reloadException;
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

		if (this.reloadException != null) {
			generator.writeKey("reload_exception");
			this.reloadException.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NodeReloadError}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<NodeReloadError> {
		private String name;

		@Nullable
		private ErrorCause reloadException;

		/**
		 * Required - API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * API name: {@code reload_exception}
		 */
		public final Builder reloadException(@Nullable ErrorCause value) {
			this.reloadException = value;
			return this;
		}

		/**
		 * API name: {@code reload_exception}
		 */
		public final Builder reloadException(Function<ErrorCause.Builder, ObjectBuilder<ErrorCause>> fn) {
			return this.reloadException(fn.apply(new ErrorCause.Builder()).build());
		}

		/**
		 * Builds a {@link NodeReloadError}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NodeReloadError build() {
			_checkSingleUse();

			return new NodeReloadError(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NodeReloadError}
	 */
	public static final JsonpDeserializer<NodeReloadError> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			NodeReloadError::setupNodeReloadErrorDeserializer);

	protected static void setupNodeReloadErrorDeserializer(ObjectDeserializer<NodeReloadError.Builder> op) {

		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::reloadException, ErrorCause._DESERIALIZER, "reload_exception");

	}

}
