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
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;

// typedef: _types.analysis.HyphenationDecompounderTokenFilter


@JsonpDeserializable
public class HyphenationDecompounderTokenFilter extends CompoundWordTokenFilterBase
		implements
			TokenFilterDefinitionVariant {
	// ---------------------------------------------------------------------------------------------

	private HyphenationDecompounderTokenFilter(Builder builder) {
		super(builder);

	}

	public static HyphenationDecompounderTokenFilter of(
			Function<Builder, ObjectBuilder<HyphenationDecompounderTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.HyphenationDecompounder;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "hyphenation_decompounder");
		super.serializeInternal(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HyphenationDecompounderTokenFilter}.
	 */

	public static class Builder extends CompoundWordTokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<HyphenationDecompounderTokenFilter> {
		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link HyphenationDecompounderTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HyphenationDecompounderTokenFilter build() {
			_checkSingleUse();

			return new HyphenationDecompounderTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HyphenationDecompounderTokenFilter}
	 */
	public static final JsonpDeserializer<HyphenationDecompounderTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new,
					HyphenationDecompounderTokenFilter::setupHyphenationDecompounderTokenFilterDeserializer);

	protected static void setupHyphenationDecompounderTokenFilterDeserializer(
			ObjectDeserializer<HyphenationDecompounderTokenFilter.Builder> op) {
		setupCompoundWordTokenFilterBaseDeserializer(op);

		op.ignore("type");
	}

}
