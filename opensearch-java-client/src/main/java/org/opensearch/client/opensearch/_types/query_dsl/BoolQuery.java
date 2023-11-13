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
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.query_dsl.BoolQuery


@JsonpDeserializable
public class BoolQuery extends QueryBase implements QueryVariant {
	private final List<Query> filter;

	@Nullable
	private final String minimumShouldMatch;

	private final List<Query> must;

	private final List<Query> mustNot;

	private final List<Query> should;

	// ---------------------------------------------------------------------------------------------

	private BoolQuery(Builder builder) {
		super(builder);

		this.filter = ApiTypeHelper.unmodifiable(builder.filter);
		this.minimumShouldMatch = builder.minimumShouldMatch;
		this.must = ApiTypeHelper.unmodifiable(builder.must);
		this.mustNot = ApiTypeHelper.unmodifiable(builder.mustNot);
		this.should = ApiTypeHelper.unmodifiable(builder.should);

	}

	public static BoolQuery of(Function<Builder, ObjectBuilder<BoolQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.Bool;
	}

	/**
	 * API name: {@code filter}
	 */
	public final List<Query> filter() {
		return this.filter;
	}

	/**
	 * API name: {@code minimum_should_match}
	 */
	@Nullable
	public final String minimumShouldMatch() {
		return this.minimumShouldMatch;
	}

	/**
	 * API name: {@code must}
	 */
	public final List<Query> must() {
		return this.must;
	}

	/**
	 * API name: {@code must_not}
	 */
	public final List<Query> mustNot() {
		return this.mustNot;
	}

	/**
	 * API name: {@code should}
	 */
	public final List<Query> should() {
		return this.should;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.filter)) {
			generator.writeKey("filter");
			generator.writeStartArray();
			for (Query item0 : this.filter) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.minimumShouldMatch != null) {
			generator.writeKey("minimum_should_match");
			generator.write(this.minimumShouldMatch);

		}
		if (ApiTypeHelper.isDefined(this.must)) {
			generator.writeKey("must");
			generator.writeStartArray();
			for (Query item0 : this.must) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.mustNot)) {
			generator.writeKey("must_not");
			generator.writeStartArray();
			for (Query item0 : this.mustNot) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.should)) {
			generator.writeKey("should");
			generator.writeStartArray();
			for (Query item0 : this.should) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link BoolQuery}.
	 */

	public static class Builder extends QueryBase.AbstractBuilder<Builder> implements ObjectBuilder<BoolQuery> {
		@Nullable
		private List<Query> filter;

		@Nullable
		private String minimumShouldMatch;

		@Nullable
		private List<Query> must;

		@Nullable
		private List<Query> mustNot;

		@Nullable
		private List<Query> should;

		/**
		 * API name: {@code filter}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>filter</code>.
		 */
		public final Builder filter(List<Query> list) {
			this.filter = _listAddAll(this.filter, list);
			return this;
		}

		/**
		 * API name: {@code filter}
		 * <p>
		 * Adds one or more values to <code>filter</code>.
		 */
		public final Builder filter(Query value, Query... values) {
			this.filter = _listAdd(this.filter, value, values);
			return this;
		}

		/**
		 * API name: {@code filter}
		 * <p>
		 * Adds a value to <code>filter</code> using a builder lambda.
		 */
		public final Builder filter(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return filter(fn.apply(new Query.Builder()).build());
		}

		/**
		 * API name: {@code minimum_should_match}
		 */
		public final Builder minimumShouldMatch(@Nullable String value) {
			this.minimumShouldMatch = value;
			return this;
		}

		/**
		 * API name: {@code must}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>must</code>.
		 */
		public final Builder must(List<Query> list) {
			this.must = _listAddAll(this.must, list);
			return this;
		}

		/**
		 * API name: {@code must}
		 * <p>
		 * Adds one or more values to <code>must</code>.
		 */
		public final Builder must(Query value, Query... values) {
			this.must = _listAdd(this.must, value, values);
			return this;
		}

		/**
		 * API name: {@code must}
		 * <p>
		 * Adds a value to <code>must</code> using a builder lambda.
		 */
		public final Builder must(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return must(fn.apply(new Query.Builder()).build());
		}

		/**
		 * API name: {@code must_not}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>mustNot</code>.
		 */
		public final Builder mustNot(List<Query> list) {
			this.mustNot = _listAddAll(this.mustNot, list);
			return this;
		}

		/**
		 * API name: {@code must_not}
		 * <p>
		 * Adds one or more values to <code>mustNot</code>.
		 */
		public final Builder mustNot(Query value, Query... values) {
			this.mustNot = _listAdd(this.mustNot, value, values);
			return this;
		}

		/**
		 * API name: {@code must_not}
		 * <p>
		 * Adds a value to <code>mustNot</code> using a builder lambda.
		 */
		public final Builder mustNot(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return mustNot(fn.apply(new Query.Builder()).build());
		}

		/**
		 * API name: {@code should}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>should</code>.
		 */
		public final Builder should(List<Query> list) {
			this.should = _listAddAll(this.should, list);
			return this;
		}

		/**
		 * API name: {@code should}
		 * <p>
		 * Adds one or more values to <code>should</code>.
		 */
		public final Builder should(Query value, Query... values) {
			this.should = _listAdd(this.should, value, values);
			return this;
		}

		/**
		 * API name: {@code should}
		 * <p>
		 * Adds a value to <code>should</code> using a builder lambda.
		 */
		public final Builder should(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return should(fn.apply(new Query.Builder()).build());
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link BoolQuery}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public BoolQuery build() {
			_checkSingleUse();

			return new BoolQuery(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link BoolQuery}
	 */
	public static final JsonpDeserializer<BoolQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			BoolQuery::setupBoolQueryDeserializer);

	protected static void setupBoolQueryDeserializer(ObjectDeserializer<BoolQuery.Builder> op) {
		setupQueryBaseDeserializer(op);
		op.add(Builder::filter, JsonpDeserializer.arrayDeserializer(Query._DESERIALIZER), "filter");
		op.add(Builder::minimumShouldMatch, JsonpDeserializer.stringDeserializer(), "minimum_should_match");
		op.add(Builder::must, JsonpDeserializer.arrayDeserializer(Query._DESERIALIZER), "must");
		op.add(Builder::mustNot, JsonpDeserializer.arrayDeserializer(Query._DESERIALIZER), "must_not");
		op.add(Builder::should, JsonpDeserializer.arrayDeserializer(Query._DESERIALIZER), "should");

	}

}
