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

// typedef: _types.analysis.FingerprintTokenFilter

@JsonpDeserializable
public class FingerprintTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	private final int maxOutputSize;

	private final String separator;

	// ---------------------------------------------------------------------------------------------

	private FingerprintTokenFilter(Builder builder) {
		super(builder);

		this.maxOutputSize = ApiTypeHelper.requireNonNull(builder.maxOutputSize, this, "maxOutputSize");
		this.separator = ApiTypeHelper.requireNonNull(builder.separator, this, "separator");

	}

	public static FingerprintTokenFilter of(Function<Builder, ObjectBuilder<FingerprintTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.Fingerprint;
	}

	/**
	 * Required - API name: {@code max_output_size}
	 */
	public final int maxOutputSize() {
		return this.maxOutputSize;
	}

	/**
	 * Required - API name: {@code separator}
	 */
	public final String separator() {
		return this.separator;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "fingerprint");
		super.serializeInternal(generator, mapper);
		generator.writeKey("max_output_size");
		generator.write(this.maxOutputSize);

		generator.writeKey("separator");
		generator.write(this.separator);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link FingerprintTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<FingerprintTokenFilter> {
		private Integer maxOutputSize;

		private String separator;

		/**
		 * Required - API name: {@code max_output_size}
		 */
		public final Builder maxOutputSize(int value) {
			this.maxOutputSize = value;
			return this;
		}

		/**
		 * Required - API name: {@code separator}
		 */
		public final Builder separator(String value) {
			this.separator = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link FingerprintTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public FingerprintTokenFilter build() {
			_checkSingleUse();

			return new FingerprintTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link FingerprintTokenFilter}
	 */
	public static final JsonpDeserializer<FingerprintTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, FingerprintTokenFilter::setupFingerprintTokenFilterDeserializer);

	protected static void setupFingerprintTokenFilterDeserializer(
			ObjectDeserializer<FingerprintTokenFilter.Builder> op) {
		setupTokenFilterBaseDeserializer(op);
		op.add(Builder::maxOutputSize, JsonpDeserializer.integerDeserializer(), "max_output_size");
		op.add(Builder::separator, JsonpDeserializer.stringDeserializer(), "separator");

		op.ignore("type");
	}

}
