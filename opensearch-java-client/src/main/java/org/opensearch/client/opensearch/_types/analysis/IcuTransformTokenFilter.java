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

package org.opensearch.client.opensearch._types.analysis;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;

// typedef: _types.analysis.IcuTransformTokenFilter

@JsonpDeserializable
public class IcuTransformTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	private final IcuTransformDirection dir;

	private final String id;

	// ---------------------------------------------------------------------------------------------

	private IcuTransformTokenFilter(Builder builder) {
		super(builder);

		this.dir = ApiTypeHelper.requireNonNull(builder.dir, this, "dir");
		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");

	}

	public static IcuTransformTokenFilter of(Function<Builder, ObjectBuilder<IcuTransformTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.IcuTransform;
	}

	/**
	 * Required - API name: {@code dir}
	 */
	public final IcuTransformDirection dir() {
		return this.dir;
	}

	/**
	 * Required - API name: {@code id}
	 */
	public final String id() {
		return this.id;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "icu_transform");
		super.serializeInternal(generator, mapper);
		generator.writeKey("dir");
		this.dir.serialize(generator, mapper);
		generator.writeKey("id");
		generator.write(this.id);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IcuTransformTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<IcuTransformTokenFilter> {
		private IcuTransformDirection dir;

		private String id;

		/**
		 * Required - API name: {@code dir}
		 */
		public final Builder dir(IcuTransformDirection value) {
			this.dir = value;
			return this;
		}

		/**
		 * Required - API name: {@code id}
		 */
		public final Builder id(String value) {
			this.id = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link IcuTransformTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IcuTransformTokenFilter build() {
			_checkSingleUse();

			return new IcuTransformTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IcuTransformTokenFilter}
	 */
	public static final JsonpDeserializer<IcuTransformTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, IcuTransformTokenFilter::setupIcuTransformTokenFilterDeserializer);

	protected static void setupIcuTransformTokenFilterDeserializer(
			ObjectDeserializer<IcuTransformTokenFilter.Builder> op) {
		TokenFilterBase.setupTokenFilterBaseDeserializer(op);
		op.add(Builder::dir, IcuTransformDirection._DESERIALIZER, "dir");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "id");

		op.ignore("type");
	}

}
