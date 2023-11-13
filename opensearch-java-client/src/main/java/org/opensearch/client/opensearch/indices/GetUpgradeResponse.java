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

// typedef: indices.get_upgrade.Response

@JsonpDeserializable
public class GetUpgradeResponse implements JsonpSerializable {
	private final List<OverlappingIndexTemplate> overlapping;

	@Nullable
	private final TemplateMapping template;

	// ---------------------------------------------------------------------------------------------

	private GetUpgradeResponse(Builder builder) {

		this.overlapping = ApiTypeHelper.unmodifiable(builder.overlapping);
		this.template = builder.template;

	}

	public static GetUpgradeResponse of(Function<Builder, ObjectBuilder<GetUpgradeResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Any templates that were superseded by the specified template.
	 * <p>
	 * API name: {@code overlapping}
	 */
	public final List<OverlappingIndexTemplate> overlapping() {
		return this.overlapping;
	}

	/**
	 * The settings, mappings, and aliases that would be applied to matching
	 * indices.
	 * <p>
	 * API name: {@code template}
	 */
	@Nullable
	public final TemplateMapping template() {
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

		if (ApiTypeHelper.isDefined(this.overlapping)) {
			generator.writeKey("overlapping");
			generator.writeStartArray();
			for (OverlappingIndexTemplate item0 : this.overlapping) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.template != null) {
			generator.writeKey("template");
			this.template.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GetUpgradeResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GetUpgradeResponse> {
		@Nullable
		private List<OverlappingIndexTemplate> overlapping;

		@Nullable
		private TemplateMapping template;

		/**
		 * Any templates that were superseded by the specified template.
		 * <p>
		 * API name: {@code overlapping}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>overlapping</code>.
		 */
		public final Builder overlapping(List<OverlappingIndexTemplate> list) {
			this.overlapping = _listAddAll(this.overlapping, list);
			return this;
		}

		/**
		 * Any templates that were superseded by the specified template.
		 * <p>
		 * API name: {@code overlapping}
		 * <p>
		 * Adds one or more values to <code>overlapping</code>.
		 */
		public final Builder overlapping(OverlappingIndexTemplate value, OverlappingIndexTemplate... values) {
			this.overlapping = _listAdd(this.overlapping, value, values);
			return this;
		}

		/**
		 * Any templates that were superseded by the specified template.
		 * <p>
		 * API name: {@code overlapping}
		 * <p>
		 * Adds a value to <code>overlapping</code> using a builder lambda.
		 */
		public final Builder overlapping(
				Function<OverlappingIndexTemplate.Builder, ObjectBuilder<OverlappingIndexTemplate>> fn) {
			return overlapping(fn.apply(new OverlappingIndexTemplate.Builder()).build());
		}

		/**
		 * The settings, mappings, and aliases that would be applied to matching
		 * indices.
		 * <p>
		 * API name: {@code template}
		 */
		public final Builder template(@Nullable TemplateMapping value) {
			this.template = value;
			return this;
		}

		/**
		 * The settings, mappings, and aliases that would be applied to matching
		 * indices.
		 * <p>
		 * API name: {@code template}
		 */
		public final Builder template(Function<TemplateMapping.Builder, ObjectBuilder<TemplateMapping>> fn) {
			return this.template(fn.apply(new TemplateMapping.Builder()).build());
		}

		/**
		 * Builds a {@link GetUpgradeResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GetUpgradeResponse build() {
			_checkSingleUse();

			return new GetUpgradeResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GetUpgradeResponse}
	 */
	public static final JsonpDeserializer<GetUpgradeResponse> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, GetUpgradeResponse::setupGetUpgradeResponseDeserializer);

	protected static void setupGetUpgradeResponseDeserializer(ObjectDeserializer<GetUpgradeResponse.Builder> op) {

		op.add(Builder::overlapping, JsonpDeserializer.arrayDeserializer(OverlappingIndexTemplate._DESERIALIZER),
				"overlapping");
		op.add(Builder::template, TemplateMapping._DESERIALIZER, "template");

	}

}
