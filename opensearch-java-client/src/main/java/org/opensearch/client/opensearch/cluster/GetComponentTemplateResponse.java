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

package org.opensearch.client.opensearch.cluster;

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

// typedef: cluster.get_component_template.Response

@JsonpDeserializable
public class GetComponentTemplateResponse implements JsonpSerializable {
	private final List<ComponentTemplate> componentTemplates;

	// ---------------------------------------------------------------------------------------------

	private GetComponentTemplateResponse(Builder builder) {

		this.componentTemplates = ApiTypeHelper.unmodifiableRequired(builder.componentTemplates, this,
				"componentTemplates");

	}

	public static GetComponentTemplateResponse of(Function<Builder, ObjectBuilder<GetComponentTemplateResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code component_templates}
	 */
	public final List<ComponentTemplate> componentTemplates() {
		return this.componentTemplates;
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

		if (ApiTypeHelper.isDefined(this.componentTemplates)) {
			generator.writeKey("component_templates");
			generator.writeStartArray();
			for (ComponentTemplate item0 : this.componentTemplates) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GetComponentTemplateResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GetComponentTemplateResponse> {
		private List<ComponentTemplate> componentTemplates;

		/**
		 * Required - API name: {@code component_templates}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>componentTemplates</code>.
		 */
		public final Builder componentTemplates(List<ComponentTemplate> list) {
			this.componentTemplates = _listAddAll(this.componentTemplates, list);
			return this;
		}

		/**
		 * Required - API name: {@code component_templates}
		 * <p>
		 * Adds one or more values to <code>componentTemplates</code>.
		 */
		public final Builder componentTemplates(ComponentTemplate value, ComponentTemplate... values) {
			this.componentTemplates = _listAdd(this.componentTemplates, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code component_templates}
		 * <p>
		 * Adds a value to <code>componentTemplates</code> using a builder lambda.
		 */
		public final Builder componentTemplates(
				Function<ComponentTemplate.Builder, ObjectBuilder<ComponentTemplate>> fn) {
			return componentTemplates(fn.apply(new ComponentTemplate.Builder()).build());
		}

		/**
		 * Builds a {@link GetComponentTemplateResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GetComponentTemplateResponse build() {
			_checkSingleUse();

			return new GetComponentTemplateResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GetComponentTemplateResponse}
	 */
	public static final JsonpDeserializer<GetComponentTemplateResponse> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, GetComponentTemplateResponse::setupGetComponentTemplateResponseDeserializer);

	protected static void setupGetComponentTemplateResponseDeserializer(
			ObjectDeserializer<GetComponentTemplateResponse.Builder> op) {

		op.add(Builder::componentTemplates, JsonpDeserializer.arrayDeserializer(ComponentTemplate._DESERIALIZER),
				"component_templates");

	}

}
