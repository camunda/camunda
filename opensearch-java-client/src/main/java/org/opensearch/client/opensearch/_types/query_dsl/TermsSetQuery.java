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

package org.opensearch.client.opensearch._types.query_dsl;

import org.opensearch.client.opensearch._types.Script;
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

// typedef: _types.query_dsl.TermsSetQuery


@JsonpDeserializable
public class TermsSetQuery extends QueryBase implements QueryVariant {
	// Single key dictionary
	private final String field;

	@Nullable
	private final String minimumShouldMatchField;

	@Nullable
	private final Script minimumShouldMatchScript;

	private final List<String> terms;

	// ---------------------------------------------------------------------------------------------

	private TermsSetQuery(Builder builder) {
		super(builder);
		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");

		this.minimumShouldMatchField = builder.minimumShouldMatchField;
		this.minimumShouldMatchScript = builder.minimumShouldMatchScript;
		this.terms = ApiTypeHelper.unmodifiableRequired(builder.terms, this, "terms");

	}

	public static TermsSetQuery of(Function<Builder, ObjectBuilder<TermsSetQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.TermsSet;
	}

	/**
	 * Required - The target field
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code minimum_should_match_field}
	 */
	@Nullable
	public final String minimumShouldMatchField() {
		return this.minimumShouldMatchField;
	}

	/**
	 * API name: {@code minimum_should_match_script}
	 */
	@Nullable
	public final Script minimumShouldMatchScript() {
		return this.minimumShouldMatchScript;
	}

	/**
	 * Required - API name: {@code terms}
	 */
	public final List<String> terms() {
		return this.terms;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject(this.field);

		super.serializeInternal(generator, mapper);
		if (this.minimumShouldMatchField != null) {
			generator.writeKey("minimum_should_match_field");
			generator.write(this.minimumShouldMatchField);

		}
		if (this.minimumShouldMatchScript != null) {
			generator.writeKey("minimum_should_match_script");
			this.minimumShouldMatchScript.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.terms)) {
			generator.writeKey("terms");
			generator.writeStartArray();
			for (String item0 : this.terms) {
				generator.write(item0);

			}
			generator.writeEnd();

		}

		generator.writeEnd();

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link TermsSetQuery}.
	 */

	public static class Builder extends QueryBase.AbstractBuilder<Builder> implements ObjectBuilder<TermsSetQuery> {
		private String field;

		/**
		 * Required - The target field
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		@Nullable
		private String minimumShouldMatchField;

		@Nullable
		private Script minimumShouldMatchScript;

		private List<String> terms;

		/**
		 * API name: {@code minimum_should_match_field}
		 */
		public final Builder minimumShouldMatchField(@Nullable String value) {
			this.minimumShouldMatchField = value;
			return this;
		}

		/**
		 * API name: {@code minimum_should_match_script}
		 */
		public final Builder minimumShouldMatchScript(@Nullable Script value) {
			this.minimumShouldMatchScript = value;
			return this;
		}

		/**
		 * API name: {@code minimum_should_match_script}
		 */
		public final Builder minimumShouldMatchScript(Function<Script.Builder, ObjectBuilder<Script>> fn) {
			return this.minimumShouldMatchScript(fn.apply(new Script.Builder()).build());
		}

		/**
		 * Required - API name: {@code terms}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>terms</code>.
		 */
		public final Builder terms(List<String> list) {
			this.terms = _listAddAll(this.terms, list);
			return this;
		}

		/**
		 * Required - API name: {@code terms}
		 * <p>
		 * Adds one or more values to <code>terms</code>.
		 */
		public final Builder terms(String value, String... values) {
			this.terms = _listAdd(this.terms, value, values);
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link TermsSetQuery}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public TermsSetQuery build() {
			_checkSingleUse();

			return new TermsSetQuery(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link TermsSetQuery}
	 */
	public static final JsonpDeserializer<TermsSetQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			TermsSetQuery::setupTermsSetQueryDeserializer);

	protected static void setupTermsSetQueryDeserializer(ObjectDeserializer<TermsSetQuery.Builder> op) {
		QueryBase.setupQueryBaseDeserializer(op);
		op.add(Builder::minimumShouldMatchField, JsonpDeserializer.stringDeserializer(), "minimum_should_match_field");
		op.add(Builder::minimumShouldMatchScript, Script._DESERIALIZER, "minimum_should_match_script");
		op.add(Builder::terms, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "terms");

		op.setKey(Builder::field, JsonpDeserializer.stringDeserializer());

	}

}
