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

package org.opensearch.client.opensearch.core;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch.core.scripts_painless_execute.PainlessContextSetup;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.Collections;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.scripts_painless_execute.Request

/**
 * Allows an arbitrary script to be executed and a result to be returned
 * 
 */
@JsonpDeserializable
public class ScriptsPainlessExecuteRequest extends RequestBase implements JsonpSerializable {
	@Nullable
	private final String context;

	@Nullable
	private final PainlessContextSetup contextSetup;

	@Nullable
	private final InlineScript script;

	// ---------------------------------------------------------------------------------------------

	private ScriptsPainlessExecuteRequest(Builder builder) {

		this.context = builder.context;
		this.contextSetup = builder.contextSetup;
		this.script = builder.script;

	}

	public static ScriptsPainlessExecuteRequest of(Function<Builder, ObjectBuilder<ScriptsPainlessExecuteRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code context}
	 */
	@Nullable
	public final String context() {
		return this.context;
	}

	/**
	 * API name: {@code context_setup}
	 */
	@Nullable
	public final PainlessContextSetup contextSetup() {
		return this.contextSetup;
	}

	/**
	 * API name: {@code script}
	 */
	@Nullable
	public final InlineScript script() {
		return this.script;
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

		if (this.context != null) {
			generator.writeKey("context");
			generator.write(this.context);

		}
		if (this.contextSetup != null) {
			generator.writeKey("context_setup");
			this.contextSetup.serialize(generator, mapper);

		}
		if (this.script != null) {
			generator.writeKey("script");
			this.script.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ScriptsPainlessExecuteRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ScriptsPainlessExecuteRequest> {
		@Nullable
		private String context;

		@Nullable
		private PainlessContextSetup contextSetup;

		@Nullable
		private InlineScript script;

		/**
		 * API name: {@code context}
		 */
		public final Builder context(@Nullable String value) {
			this.context = value;
			return this;
		}

		/**
		 * API name: {@code context_setup}
		 */
		public final Builder contextSetup(@Nullable PainlessContextSetup value) {
			this.contextSetup = value;
			return this;
		}

		/**
		 * API name: {@code context_setup}
		 */
		public final Builder contextSetup(
				Function<PainlessContextSetup.Builder, ObjectBuilder<PainlessContextSetup>> fn) {
			return this.contextSetup(fn.apply(new PainlessContextSetup.Builder()).build());
		}

		/**
		 * API name: {@code script}
		 */
		public final Builder script(@Nullable InlineScript value) {
			this.script = value;
			return this;
		}

		/**
		 * API name: {@code script}
		 */
		public final Builder script(Function<InlineScript.Builder, ObjectBuilder<InlineScript>> fn) {
			return this.script(fn.apply(new InlineScript.Builder()).build());
		}

		/**
		 * Builds a {@link ScriptsPainlessExecuteRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ScriptsPainlessExecuteRequest build() {
			_checkSingleUse();

			return new ScriptsPainlessExecuteRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ScriptsPainlessExecuteRequest}
	 */
	public static final JsonpDeserializer<ScriptsPainlessExecuteRequest> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, ScriptsPainlessExecuteRequest::setupScriptsPainlessExecuteRequestDeserializer);

	protected static void setupScriptsPainlessExecuteRequestDeserializer(
			ObjectDeserializer<ScriptsPainlessExecuteRequest.Builder> op) {

		op.add(Builder::context, JsonpDeserializer.stringDeserializer(), "context");
		op.add(Builder::contextSetup, PainlessContextSetup._DESERIALIZER, "context_setup");
		op.add(Builder::script, InlineScript._DESERIALIZER, "script");

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code scripts_painless_execute}".
	 */
	public static final SimpleEndpoint<ScriptsPainlessExecuteRequest, ?> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "POST";

			},

			// Request path
			request -> {
				return "/_scripts/painless/_execute";

			},

			// Request parameters
			request -> {
				return Collections.emptyMap();

			}, SimpleEndpoint.emptyMap(), true, ScriptsPainlessExecuteResponse._DESERIALIZER);

	/**
	 * Create an "{@code scripts_painless_execute}" endpoint.
	 */
	public static <TResult> Endpoint<ScriptsPainlessExecuteRequest, ScriptsPainlessExecuteResponse<TResult>,
			ErrorResponse> createScriptsPainlessExecuteEndpoint(
			JsonpDeserializer<TResult> tResultDeserializer) {
		return _ENDPOINT.withResponseDeserializer(
				ScriptsPainlessExecuteResponse.createScriptsPainlessExecuteResponseDeserializer(tResultDeserializer));
	}
}
