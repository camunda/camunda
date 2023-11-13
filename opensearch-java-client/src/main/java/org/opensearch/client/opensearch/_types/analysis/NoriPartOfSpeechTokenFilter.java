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
import java.util.List;
import java.util.function.Function;

// typedef: _types.analysis.NoriPartOfSpeechTokenFilter


@JsonpDeserializable
public class NoriPartOfSpeechTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	private final List<String> stoptags;

	// ---------------------------------------------------------------------------------------------

	private NoriPartOfSpeechTokenFilter(Builder builder) {
		super(builder);

		this.stoptags = ApiTypeHelper.unmodifiableRequired(builder.stoptags, this, "stoptags");

	}

	public static NoriPartOfSpeechTokenFilter of(Function<Builder, ObjectBuilder<NoriPartOfSpeechTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.NoriPartOfSpeech;
	}

	/**
	 * Required - API name: {@code stoptags}
	 */
	public final List<String> stoptags() {
		return this.stoptags;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "nori_part_of_speech");
		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.stoptags)) {
			generator.writeKey("stoptags");
			generator.writeStartArray();
			for (String item0 : this.stoptags) {
				generator.write(item0);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link NoriPartOfSpeechTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<NoriPartOfSpeechTokenFilter> {
		private List<String> stoptags;

		/**
		 * Required - API name: {@code stoptags}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>stoptags</code>.
		 */
		public final Builder stoptags(List<String> list) {
			this.stoptags = _listAddAll(this.stoptags, list);
			return this;
		}

		/**
		 * Required - API name: {@code stoptags}
		 * <p>
		 * Adds one or more values to <code>stoptags</code>.
		 */
		public final Builder stoptags(String value, String... values) {
			this.stoptags = _listAdd(this.stoptags, value, values);
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link NoriPartOfSpeechTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public NoriPartOfSpeechTokenFilter build() {
			_checkSingleUse();

			return new NoriPartOfSpeechTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link NoriPartOfSpeechTokenFilter}
	 */
	public static final JsonpDeserializer<NoriPartOfSpeechTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, NoriPartOfSpeechTokenFilter::setupNoriPartOfSpeechTokenFilterDeserializer);

	protected static void setupNoriPartOfSpeechTokenFilterDeserializer(
			ObjectDeserializer<NoriPartOfSpeechTokenFilter.Builder> op) {
		TokenFilterBase.setupTokenFilterBaseDeserializer(op);
		op.add(Builder::stoptags, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"stoptags");

		op.ignore("type");
	}

}
