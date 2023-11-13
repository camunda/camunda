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

package org.opensearch.client.opensearch.indices;

import org.opensearch.client.opensearch.indices.simulate_template.Template;
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

// typedef: indices.simulate_template.Response

@JsonpDeserializable
public class SimulateTemplateResponse implements JsonpSerializable {
	private final Template template;

	// ---------------------------------------------------------------------------------------------

	private SimulateTemplateResponse(Builder builder) {

		this.template = ApiTypeHelper.requireNonNull(builder.template, this, "template");

	}

	public static SimulateTemplateResponse of(Function<Builder, ObjectBuilder<SimulateTemplateResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code template}
	 */
	public final Template template() {
		return this.template;
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

		generator.writeKey("template");
		this.template.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SimulateTemplateResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<SimulateTemplateResponse> {
		private Template template;

		/**
		 * Required - API name: {@code template}
		 */
		public final Builder template(Template value) {
			this.template = value;
			return this;
		}

		/**
		 * Required - API name: {@code template}
		 */
		public final Builder template(Function<Template.Builder, ObjectBuilder<Template>> fn) {
			return this.template(fn.apply(new Template.Builder()).build());
		}

		/**
		 * Builds a {@link SimulateTemplateResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public SimulateTemplateResponse build() {
			_checkSingleUse();

			return new SimulateTemplateResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link SimulateTemplateResponse}
	 */
	public static final JsonpDeserializer<SimulateTemplateResponse> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, SimulateTemplateResponse::setupSimulateTemplateResponseDeserializer);

	protected static void setupSimulateTemplateResponseDeserializer(
			ObjectDeserializer<SimulateTemplateResponse.Builder> op) {

		op.add(Builder::template, Template._DESERIALIZER, "template");

	}

}
