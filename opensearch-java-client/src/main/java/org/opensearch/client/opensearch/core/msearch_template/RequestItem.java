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

package org.opensearch.client.opensearch.core.msearch_template;

import org.opensearch.client.opensearch.core.msearch.MultisearchHeader;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.NdJsonpSerializable;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;

// typedef: _global.msearch_template.RequestItem

public class RequestItem implements NdJsonpSerializable, JsonpSerializable {
	private final MultisearchHeader header;

	private final TemplateConfig body;

	// ---------------------------------------------------------------------------------------------

	private RequestItem(Builder builder) {

		this.header = ApiTypeHelper.requireNonNull(builder.header, this, "header");
		this.body = ApiTypeHelper.requireNonNull(builder.body, this, "body");

	}

	public static RequestItem of(Function<Builder, ObjectBuilder<RequestItem>> fn) {
		return fn.apply(new Builder()).build();
	}

	@Override
	public Iterator<?> _serializables() {
		return Arrays.asList(header, body).iterator();
	}

	/**
	 * Required - API name: {@code header}
	 */
	public final MultisearchHeader header() {
		return this.header;
	}

	/**
	 * Required - API name: {@code body}
	 */
	public final TemplateConfig body() {
		return this.body;
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

		generator.writeKey("header");
		this.header.serialize(generator, mapper);

		generator.writeKey("body");
		this.body.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link RequestItem}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<RequestItem> {
		private MultisearchHeader header;

		private TemplateConfig body;

		/**
		 * Required - API name: {@code header}
		 */
		public final Builder header(MultisearchHeader value) {
			this.header = value;
			return this;
		}

		/**
		 * Required - API name: {@code header}
		 */
		public final Builder header(Function<MultisearchHeader.Builder, ObjectBuilder<MultisearchHeader>> fn) {
			return this.header(fn.apply(new MultisearchHeader.Builder()).build());
		}

		/**
		 * Required - API name: {@code body}
		 */
		public final Builder body(TemplateConfig value) {
			this.body = value;
			return this;
		}

		/**
		 * Required - API name: {@code body}
		 */
		public final Builder body(Function<TemplateConfig.Builder, ObjectBuilder<TemplateConfig>> fn) {
			return this.body(fn.apply(new TemplateConfig.Builder()).build());
		}

		/**
		 * Builds a {@link RequestItem}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public RequestItem build() {
			_checkSingleUse();

			return new RequestItem(this);
		}
	}

}
