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
import javax.annotation.Nullable;

// typedef: _types.analysis.StemmerOverrideTokenFilter


@JsonpDeserializable
public class StemmerOverrideTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	private final List<String> rules;

	@Nullable
	private final String rulesPath;

	// ---------------------------------------------------------------------------------------------

	private StemmerOverrideTokenFilter(Builder builder) {
		super(builder);

		this.rules = ApiTypeHelper.unmodifiable(builder.rules);
		this.rulesPath = builder.rulesPath;

	}

	public static StemmerOverrideTokenFilter of(Function<Builder, ObjectBuilder<StemmerOverrideTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.StemmerOverride;
	}

	/**
	 * API name: {@code rules}
	 */
	public final List<String> rules() {
		return this.rules;
	}

	/**
	 * API name: {@code rules_path}
	 */
	@Nullable
	public final String rulesPath() {
		return this.rulesPath;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "stemmer_override");
		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.rules)) {
			generator.writeKey("rules");
			generator.writeStartArray();
			for (String item0 : this.rules) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.rulesPath != null) {
			generator.writeKey("rules_path");
			generator.write(this.rulesPath);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link StemmerOverrideTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<StemmerOverrideTokenFilter> {
		@Nullable
		private List<String> rules;

		@Nullable
		private String rulesPath;

		/**
		 * API name: {@code rules}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>rules</code>.
		 */
		public final Builder rules(List<String> list) {
			this.rules = _listAddAll(this.rules, list);
			return this;
		}

		/**
		 * API name: {@code rules}
		 * <p>
		 * Adds one or more values to <code>rules</code>.
		 */
		public final Builder rules(String value, String... values) {
			this.rules = _listAdd(this.rules, value, values);
			return this;
		}

		/**
		 * API name: {@code rules_path}
		 */
		public final Builder rulesPath(@Nullable String value) {
			this.rulesPath = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link StemmerOverrideTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public StemmerOverrideTokenFilter build() {
			_checkSingleUse();

			return new StemmerOverrideTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link StemmerOverrideTokenFilter}
	 */
	public static final JsonpDeserializer<StemmerOverrideTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, StemmerOverrideTokenFilter::setupStemmerOverrideTokenFilterDeserializer);

	protected static void setupStemmerOverrideTokenFilterDeserializer(
			ObjectDeserializer<StemmerOverrideTokenFilter.Builder> op) {
		setupTokenFilterBaseDeserializer(op);
		op.add(Builder::rules, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "rules");
		op.add(Builder::rulesPath, JsonpDeserializer.stringDeserializer(), "rules_path");

		op.ignore("type");
	}

}
