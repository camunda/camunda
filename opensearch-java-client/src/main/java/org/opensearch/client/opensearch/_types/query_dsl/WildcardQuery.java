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

// typedef: _types.query_dsl.WildcardQuery


@JsonpDeserializable
public class WildcardQuery extends QueryBase implements QueryVariant {
	// Single key dictionary
	private final String field;

	@Nullable
	private final Boolean caseInsensitive;

	@Nullable
	private final String rewrite;

	@Nullable
	private final String value;

	@Nullable
	private final String wildcard;

	// ---------------------------------------------------------------------------------------------

	private WildcardQuery(Builder builder) {
		super(builder);
		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");

		this.caseInsensitive = builder.caseInsensitive;
		this.rewrite = builder.rewrite;
		this.value = builder.value;
		this.wildcard = builder.wildcard;

	}

	public static WildcardQuery of(Function<Builder, ObjectBuilder<WildcardQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.Wildcard;
	}

	/**
	 * Required - The target field
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * Allows case insensitive matching of the pattern with the indexed field values
	 * when set to true. Default is false which means the case sensitivity of
	 * matching depends on the underlying field's mapping.
	 * <p>
	 * API name: {@code case_insensitive}
	 */
	@Nullable
	public final Boolean caseInsensitive() {
		return this.caseInsensitive;
	}

	/**
	 * Method used to rewrite the query
	 * <p>
	 * API name: {@code rewrite}
	 */
	@Nullable
	public final String rewrite() {
		return this.rewrite;
	}

	/**
	 * Wildcard pattern for terms you wish to find in the provided field. Required,
	 * when wildcard is not set.
	 * <p>
	 * API name: {@code value}
	 */
	@Nullable
	public final String value() {
		return this.value;
	}

	/**
	 * Wildcard pattern for terms you wish to find in the provided field. Required,
	 * when value is not set.
	 * <p>
	 * API name: {@code wildcard}
	 */
	@Nullable
	public final String wildcard() {
		return this.wildcard;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject(this.field);

		super.serializeInternal(generator, mapper);
		if (this.caseInsensitive != null) {
			generator.writeKey("case_insensitive");
			generator.write(this.caseInsensitive);

		}
		if (this.rewrite != null) {
			generator.writeKey("rewrite");
			generator.write(this.rewrite);

		}
		if (this.value != null) {
			generator.writeKey("value");
			generator.write(this.value);

		}
		if (this.wildcard != null) {
			generator.writeKey("wildcard");
			generator.write(this.wildcard);

		}

		generator.writeEnd();

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link WildcardQuery}.
	 */

	public static class Builder extends QueryBase.AbstractBuilder<Builder> implements ObjectBuilder<WildcardQuery> {
		private String field;

		/**
		 * Required - The target field
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		@Nullable
		private Boolean caseInsensitive;

		@Nullable
		private String rewrite;

		@Nullable
		private String value;

		@Nullable
		private String wildcard;

		/**
		 * Allows case insensitive matching of the pattern with the indexed field values
		 * when set to true. Default is false which means the case sensitivity of
		 * matching depends on the underlying field's mapping.
		 * <p>
		 * API name: {@code case_insensitive}
		 */
		public final Builder caseInsensitive(@Nullable Boolean value) {
			this.caseInsensitive = value;
			return this;
		}

		/**
		 * Method used to rewrite the query
		 * <p>
		 * API name: {@code rewrite}
		 */
		public final Builder rewrite(@Nullable String value) {
			this.rewrite = value;
			return this;
		}

		/**
		 * Wildcard pattern for terms you wish to find in the provided field. Required,
		 * when wildcard is not set.
		 * <p>
		 * API name: {@code value}
		 */
		public final Builder value(@Nullable String value) {
			this.value = value;
			return this;
		}

		/**
		 * Wildcard pattern for terms you wish to find in the provided field. Required,
		 * when value is not set.
		 * <p>
		 * API name: {@code wildcard}
		 */
		public final Builder wildcard(@Nullable String value) {
			this.wildcard = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link WildcardQuery}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public WildcardQuery build() {
			_checkSingleUse();

			return new WildcardQuery(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link WildcardQuery}
	 */
	public static final JsonpDeserializer<WildcardQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			WildcardQuery::setupWildcardQueryDeserializer);

	protected static void setupWildcardQueryDeserializer(ObjectDeserializer<WildcardQuery.Builder> op) {
		QueryBase.setupQueryBaseDeserializer(op);
		op.add(Builder::caseInsensitive, JsonpDeserializer.booleanDeserializer(), "case_insensitive");
		op.add(Builder::rewrite, JsonpDeserializer.stringDeserializer(), "rewrite");
		op.add(Builder::value, JsonpDeserializer.stringDeserializer(), "value");
		op.add(Builder::wildcard, JsonpDeserializer.stringDeserializer(), "wildcard");

		op.setKey(Builder::field, JsonpDeserializer.stringDeserializer());
		op.shortcutProperty("value");

	}

}
