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

// typedef: _types.query_dsl.BoostingQuery


@JsonpDeserializable
public class BoostingQuery extends QueryBase implements QueryVariant {
	private final double negativeBoost;

	private final Query negative;

	private final Query positive;

	// ---------------------------------------------------------------------------------------------

	private BoostingQuery(Builder builder) {
		super(builder);

		this.negativeBoost = ApiTypeHelper.requireNonNull(builder.negativeBoost, this, "negativeBoost");
		this.negative = ApiTypeHelper.requireNonNull(builder.negative, this, "negative");
		this.positive = ApiTypeHelper.requireNonNull(builder.positive, this, "positive");

	}

	public static BoostingQuery of(Function<Builder, ObjectBuilder<BoostingQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.Boosting;
	}

	/**
	 * Required - API name: {@code negative_boost}
	 */
	public final double negativeBoost() {
		return this.negativeBoost;
	}

	/**
	 * Required - API name: {@code negative}
	 */
	public final Query negative() {
		return this.negative;
	}

	/**
	 * Required - API name: {@code positive}
	 */
	public final Query positive() {
		return this.positive;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("negative_boost");
		generator.write(this.negativeBoost);

		generator.writeKey("negative");
		this.negative.serialize(generator, mapper);

		generator.writeKey("positive");
		this.positive.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link BoostingQuery}.
	 */

	public static class Builder extends QueryBase.AbstractBuilder<Builder> implements ObjectBuilder<BoostingQuery> {
		private Double negativeBoost;

		private Query negative;

		private Query positive;

		/**
		 * Required - API name: {@code negative_boost}
		 */
		public final Builder negativeBoost(double value) {
			this.negativeBoost = value;
			return this;
		}

		/**
		 * Required - API name: {@code negative}
		 */
		public final Builder negative(Query value) {
			this.negative = value;
			return this;
		}

		/**
		 * Required - API name: {@code negative}
		 */
		public final Builder negative(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return this.negative(fn.apply(new Query.Builder()).build());
		}

		/**
		 * Required - API name: {@code positive}
		 */
		public final Builder positive(Query value) {
			this.positive = value;
			return this;
		}

		/**
		 * Required - API name: {@code positive}
		 */
		public final Builder positive(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return this.positive(fn.apply(new Query.Builder()).build());
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link BoostingQuery}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public BoostingQuery build() {
			_checkSingleUse();

			return new BoostingQuery(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link BoostingQuery}
	 */
	public static final JsonpDeserializer<BoostingQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			BoostingQuery::setupBoostingQueryDeserializer);

	protected static void setupBoostingQueryDeserializer(ObjectDeserializer<BoostingQuery.Builder> op) {
		setupQueryBaseDeserializer(op);
		op.add(Builder::negativeBoost, JsonpDeserializer.doubleDeserializer(), "negative_boost");
		op.add(Builder::negative, Query._DESERIALIZER, "negative");
		op.add(Builder::positive, Query._DESERIALIZER, "positive");

	}

}
