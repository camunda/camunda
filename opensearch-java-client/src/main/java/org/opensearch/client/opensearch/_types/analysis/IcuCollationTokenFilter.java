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
import javax.annotation.Nullable;

// typedef: _types.analysis.IcuCollationTokenFilter

@JsonpDeserializable
public class IcuCollationTokenFilter extends TokenFilterBase implements TokenFilterDefinitionVariant {
	private final IcuCollationAlternate alternate;

	private final IcuCollationCaseFirst casefirst;

	private final boolean caselevel;

	private final String country;

	private final IcuCollationDecomposition decomposition;

	private final boolean hiraganaquaternarymode;

	private final String language;

	private final boolean numeric;

	private final IcuCollationStrength strength;

	@Nullable
	private final String variabletop;

	private final String variant;

	// ---------------------------------------------------------------------------------------------

	private IcuCollationTokenFilter(Builder builder) {
		super(builder);

		this.alternate = ApiTypeHelper.requireNonNull(builder.alternate, this, "alternate");
		this.casefirst = ApiTypeHelper.requireNonNull(builder.casefirst, this, "casefirst");
		this.caselevel = ApiTypeHelper.requireNonNull(builder.caselevel, this, "caselevel");
		this.country = ApiTypeHelper.requireNonNull(builder.country, this, "country");
		this.decomposition = ApiTypeHelper.requireNonNull(builder.decomposition, this, "decomposition");
		this.hiraganaquaternarymode = ApiTypeHelper.requireNonNull(builder.hiraganaquaternarymode, this,
				"hiraganaquaternarymode");
		this.language = ApiTypeHelper.requireNonNull(builder.language, this, "language");
		this.numeric = ApiTypeHelper.requireNonNull(builder.numeric, this, "numeric");
		this.strength = ApiTypeHelper.requireNonNull(builder.strength, this, "strength");
		this.variabletop = builder.variabletop;
		this.variant = ApiTypeHelper.requireNonNull(builder.variant, this, "variant");

	}

	public static IcuCollationTokenFilter of(Function<Builder, ObjectBuilder<IcuCollationTokenFilter>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * TokenFilterDefinition variant kind.
	 */
	@Override
	public TokenFilterDefinition.Kind _tokenFilterDefinitionKind() {
		return TokenFilterDefinition.Kind.IcuCollation;
	}

	/**
	 * Required - API name: {@code alternate}
	 */
	public final IcuCollationAlternate alternate() {
		return this.alternate;
	}

	/**
	 * Required - API name: {@code caseFirst}
	 */
	public final IcuCollationCaseFirst casefirst() {
		return this.casefirst;
	}

	/**
	 * Required - API name: {@code caseLevel}
	 */
	public final boolean caselevel() {
		return this.caselevel;
	}

	/**
	 * Required - API name: {@code country}
	 */
	public final String country() {
		return this.country;
	}

	/**
	 * Required - API name: {@code decomposition}
	 */
	public final IcuCollationDecomposition decomposition() {
		return this.decomposition;
	}

	/**
	 * Required - API name: {@code hiraganaQuaternaryMode}
	 */
	public final boolean hiraganaquaternarymode() {
		return this.hiraganaquaternarymode;
	}

	/**
	 * Required - API name: {@code language}
	 */
	public final String language() {
		return this.language;
	}

	/**
	 * Required - API name: {@code numeric}
	 */
	public final boolean numeric() {
		return this.numeric;
	}

	/**
	 * Required - API name: {@code strength}
	 */
	public final IcuCollationStrength strength() {
		return this.strength;
	}

	/**
	 * API name: {@code variableTop}
	 */
	@Nullable
	public final String variabletop() {
		return this.variabletop;
	}

	/**
	 * Required - API name: {@code variant}
	 */
	public final String variant() {
		return this.variant;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "icu_collation");
		super.serializeInternal(generator, mapper);
		generator.writeKey("alternate");
		this.alternate.serialize(generator, mapper);
		generator.writeKey("caseFirst");
		this.casefirst.serialize(generator, mapper);
		generator.writeKey("caseLevel");
		generator.write(this.caselevel);

		generator.writeKey("country");
		generator.write(this.country);

		generator.writeKey("decomposition");
		this.decomposition.serialize(generator, mapper);
		generator.writeKey("hiraganaQuaternaryMode");
		generator.write(this.hiraganaquaternarymode);

		generator.writeKey("language");
		generator.write(this.language);

		generator.writeKey("numeric");
		generator.write(this.numeric);

		generator.writeKey("strength");
		this.strength.serialize(generator, mapper);
		if (this.variabletop != null) {
			generator.writeKey("variableTop");
			generator.write(this.variabletop);

		}
		generator.writeKey("variant");
		generator.write(this.variant);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IcuCollationTokenFilter}.
	 */

	public static class Builder extends TokenFilterBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<IcuCollationTokenFilter> {
		private IcuCollationAlternate alternate;

		private IcuCollationCaseFirst casefirst;

		private Boolean caselevel;

		private String country;

		private IcuCollationDecomposition decomposition;

		private Boolean hiraganaquaternarymode;

		private String language;

		private Boolean numeric;

		private IcuCollationStrength strength;

		@Nullable
		private String variabletop;

		private String variant;

		/**
		 * Required - API name: {@code alternate}
		 */
		public final Builder alternate(IcuCollationAlternate value) {
			this.alternate = value;
			return this;
		}

		/**
		 * Required - API name: {@code caseFirst}
		 */
		public final Builder casefirst(IcuCollationCaseFirst value) {
			this.casefirst = value;
			return this;
		}

		/**
		 * Required - API name: {@code caseLevel}
		 */
		public final Builder caselevel(boolean value) {
			this.caselevel = value;
			return this;
		}

		/**
		 * Required - API name: {@code country}
		 */
		public final Builder country(String value) {
			this.country = value;
			return this;
		}

		/**
		 * Required - API name: {@code decomposition}
		 */
		public final Builder decomposition(IcuCollationDecomposition value) {
			this.decomposition = value;
			return this;
		}

		/**
		 * Required - API name: {@code hiraganaQuaternaryMode}
		 */
		public final Builder hiraganaquaternarymode(boolean value) {
			this.hiraganaquaternarymode = value;
			return this;
		}

		/**
		 * Required - API name: {@code language}
		 */
		public final Builder language(String value) {
			this.language = value;
			return this;
		}

		/**
		 * Required - API name: {@code numeric}
		 */
		public final Builder numeric(boolean value) {
			this.numeric = value;
			return this;
		}

		/**
		 * Required - API name: {@code strength}
		 */
		public final Builder strength(IcuCollationStrength value) {
			this.strength = value;
			return this;
		}

		/**
		 * API name: {@code variableTop}
		 */
		public final Builder variabletop(@Nullable String value) {
			this.variabletop = value;
			return this;
		}

		/**
		 * Required - API name: {@code variant}
		 */
		public final Builder variant(String value) {
			this.variant = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link IcuCollationTokenFilter}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IcuCollationTokenFilter build() {
			_checkSingleUse();

			return new IcuCollationTokenFilter(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IcuCollationTokenFilter}
	 */
	public static final JsonpDeserializer<IcuCollationTokenFilter> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, IcuCollationTokenFilter::setupIcuCollationTokenFilterDeserializer);

	protected static void setupIcuCollationTokenFilterDeserializer(
			ObjectDeserializer<IcuCollationTokenFilter.Builder> op) {
		TokenFilterBase.setupTokenFilterBaseDeserializer(op);
		op.add(Builder::alternate, IcuCollationAlternate._DESERIALIZER, "alternate");
		op.add(Builder::casefirst, IcuCollationCaseFirst._DESERIALIZER, "caseFirst");
		op.add(Builder::caselevel, JsonpDeserializer.booleanDeserializer(), "caseLevel");
		op.add(Builder::country, JsonpDeserializer.stringDeserializer(), "country");
		op.add(Builder::decomposition, IcuCollationDecomposition._DESERIALIZER, "decomposition");
		op.add(Builder::hiraganaquaternarymode, JsonpDeserializer.booleanDeserializer(), "hiraganaQuaternaryMode");
		op.add(Builder::language, JsonpDeserializer.stringDeserializer(), "language");
		op.add(Builder::numeric, JsonpDeserializer.booleanDeserializer(), "numeric");
		op.add(Builder::strength, IcuCollationStrength._DESERIALIZER, "strength");
		op.add(Builder::variabletop, JsonpDeserializer.stringDeserializer(), "variableTop");
		op.add(Builder::variant, JsonpDeserializer.stringDeserializer(), "variant");

		op.ignore("type");
	}

}
