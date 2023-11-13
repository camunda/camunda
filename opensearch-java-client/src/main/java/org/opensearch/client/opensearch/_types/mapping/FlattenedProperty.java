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

package org.opensearch.client.opensearch._types.mapping;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.mapping.FlattenedProperty


@JsonpDeserializable
public class FlattenedProperty extends PropertyBase implements PropertyVariant {
	@Nullable
	private final Double boost;

	@Nullable
	private final Integer depthLimit;

	@Nullable
	private final Boolean docValues;

	@Nullable
	private final Boolean eagerGlobalOrdinals;

	@Nullable
	private final Boolean index;

	@Nullable
	private final IndexOptions indexOptions;

	@Nullable
	private final String nullValue;

	@Nullable
	private final String similarity;

	@Nullable
	private final Boolean splitQueriesOnWhitespace;

	// ---------------------------------------------------------------------------------------------

	private FlattenedProperty(Builder builder) {
		super(builder);

		this.boost = builder.boost;
		this.depthLimit = builder.depthLimit;
		this.docValues = builder.docValues;
		this.eagerGlobalOrdinals = builder.eagerGlobalOrdinals;
		this.index = builder.index;
		this.indexOptions = builder.indexOptions;
		this.nullValue = builder.nullValue;
		this.similarity = builder.similarity;
		this.splitQueriesOnWhitespace = builder.splitQueriesOnWhitespace;

	}

	public static FlattenedProperty of(Function<Builder, ObjectBuilder<FlattenedProperty>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Property variant kind.
	 */
	@Override
	public Property.Kind _propertyKind() {
		return Property.Kind.Flattened;
	}

	/**
	 * API name: {@code boost}
	 */
	@Nullable
	public final Double boost() {
		return this.boost;
	}

	/**
	 * API name: {@code depth_limit}
	 */
	@Nullable
	public final Integer depthLimit() {
		return this.depthLimit;
	}

	/**
	 * API name: {@code doc_values}
	 */
	@Nullable
	public final Boolean docValues() {
		return this.docValues;
	}

	/**
	 * API name: {@code eager_global_ordinals}
	 */
	@Nullable
	public final Boolean eagerGlobalOrdinals() {
		return this.eagerGlobalOrdinals;
	}

	/**
	 * API name: {@code index}
	 */
	@Nullable
	public final Boolean index() {
		return this.index;
	}

	/**
	 * API name: {@code index_options}
	 */
	@Nullable
	public final IndexOptions indexOptions() {
		return this.indexOptions;
	}

	/**
	 * API name: {@code null_value}
	 */
	@Nullable
	public final String nullValue() {
		return this.nullValue;
	}

	/**
	 * API name: {@code similarity}
	 */
	@Nullable
	public final String similarity() {
		return this.similarity;
	}

	/**
	 * API name: {@code split_queries_on_whitespace}
	 */
	@Nullable
	public final Boolean splitQueriesOnWhitespace() {
		return this.splitQueriesOnWhitespace;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "flattened");
		super.serializeInternal(generator, mapper);
		if (this.boost != null) {
			generator.writeKey("boost");
			generator.write(this.boost);

		}
		if (this.depthLimit != null) {
			generator.writeKey("depth_limit");
			generator.write(this.depthLimit);

		}
		if (this.docValues != null) {
			generator.writeKey("doc_values");
			generator.write(this.docValues);

		}
		if (this.eagerGlobalOrdinals != null) {
			generator.writeKey("eager_global_ordinals");
			generator.write(this.eagerGlobalOrdinals);

		}
		if (this.index != null) {
			generator.writeKey("index");
			generator.write(this.index);

		}
		if (this.indexOptions != null) {
			generator.writeKey("index_options");
			this.indexOptions.serialize(generator, mapper);
		}
		if (this.nullValue != null) {
			generator.writeKey("null_value");
			generator.write(this.nullValue);

		}
		if (this.similarity != null) {
			generator.writeKey("similarity");
			generator.write(this.similarity);

		}
		if (this.splitQueriesOnWhitespace != null) {
			generator.writeKey("split_queries_on_whitespace");
			generator.write(this.splitQueriesOnWhitespace);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link FlattenedProperty}.
	 */

	public static class Builder extends PropertyBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<FlattenedProperty> {
		@Nullable
		private Double boost;

		@Nullable
		private Integer depthLimit;

		@Nullable
		private Boolean docValues;

		@Nullable
		private Boolean eagerGlobalOrdinals;

		@Nullable
		private Boolean index;

		@Nullable
		private IndexOptions indexOptions;

		@Nullable
		private String nullValue;

		@Nullable
		private String similarity;

		@Nullable
		private Boolean splitQueriesOnWhitespace;

		/**
		 * API name: {@code boost}
		 */
		public final Builder boost(@Nullable Double value) {
			this.boost = value;
			return this;
		}

		/**
		 * API name: {@code depth_limit}
		 */
		public final Builder depthLimit(@Nullable Integer value) {
			this.depthLimit = value;
			return this;
		}

		/**
		 * API name: {@code doc_values}
		 */
		public final Builder docValues(@Nullable Boolean value) {
			this.docValues = value;
			return this;
		}

		/**
		 * API name: {@code eager_global_ordinals}
		 */
		public final Builder eagerGlobalOrdinals(@Nullable Boolean value) {
			this.eagerGlobalOrdinals = value;
			return this;
		}

		/**
		 * API name: {@code index}
		 */
		public final Builder index(@Nullable Boolean value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code index_options}
		 */
		public final Builder indexOptions(@Nullable IndexOptions value) {
			this.indexOptions = value;
			return this;
		}

		/**
		 * API name: {@code null_value}
		 */
		public final Builder nullValue(@Nullable String value) {
			this.nullValue = value;
			return this;
		}

		/**
		 * API name: {@code similarity}
		 */
		public final Builder similarity(@Nullable String value) {
			this.similarity = value;
			return this;
		}

		/**
		 * API name: {@code split_queries_on_whitespace}
		 */
		public final Builder splitQueriesOnWhitespace(@Nullable Boolean value) {
			this.splitQueriesOnWhitespace = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link FlattenedProperty}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public FlattenedProperty build() {
			_checkSingleUse();

			return new FlattenedProperty(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link FlattenedProperty}
	 */
	public static final JsonpDeserializer<FlattenedProperty> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, FlattenedProperty::setupFlattenedPropertyDeserializer);

	protected static void setupFlattenedPropertyDeserializer(ObjectDeserializer<FlattenedProperty.Builder> op) {
		PropertyBase.setupPropertyBaseDeserializer(op);
		op.add(Builder::boost, JsonpDeserializer.doubleDeserializer(), "boost");
		op.add(Builder::depthLimit, JsonpDeserializer.integerDeserializer(), "depth_limit");
		op.add(Builder::docValues, JsonpDeserializer.booleanDeserializer(), "doc_values");
		op.add(Builder::eagerGlobalOrdinals, JsonpDeserializer.booleanDeserializer(), "eager_global_ordinals");
		op.add(Builder::index, JsonpDeserializer.booleanDeserializer(), "index");
		op.add(Builder::indexOptions, IndexOptions._DESERIALIZER, "index_options");
		op.add(Builder::nullValue, JsonpDeserializer.stringDeserializer(), "null_value");
		op.add(Builder::similarity, JsonpDeserializer.stringDeserializer(), "similarity");
		op.add(Builder::splitQueriesOnWhitespace, JsonpDeserializer.booleanDeserializer(),
				"split_queries_on_whitespace");

		op.ignore("type");
	}

}
