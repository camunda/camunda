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
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.mapping.CompletionProperty


@JsonpDeserializable
public class CompletionProperty extends DocValuesPropertyBase implements PropertyVariant {
	@Nullable
	private final String analyzer;

	private final List<SuggestContext> contexts;

	@Nullable
	private final Integer maxInputLength;

	@Nullable
	private final Boolean preservePositionIncrements;

	@Nullable
	private final Boolean preserveSeparators;

	@Nullable
	private final String searchAnalyzer;

	// ---------------------------------------------------------------------------------------------

	private CompletionProperty(Builder builder) {
		super(builder);

		this.analyzer = builder.analyzer;
		this.contexts = ApiTypeHelper.unmodifiable(builder.contexts);
		this.maxInputLength = builder.maxInputLength;
		this.preservePositionIncrements = builder.preservePositionIncrements;
		this.preserveSeparators = builder.preserveSeparators;
		this.searchAnalyzer = builder.searchAnalyzer;

	}

	public static CompletionProperty of(Function<Builder, ObjectBuilder<CompletionProperty>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Property variant kind.
	 */
	@Override
	public Property.Kind _propertyKind() {
		return Property.Kind.Completion;
	}

	/**
	 * API name: {@code analyzer}
	 */
	@Nullable
	public final String analyzer() {
		return this.analyzer;
	}

	/**
	 * API name: {@code contexts}
	 */
	public final List<SuggestContext> contexts() {
		return this.contexts;
	}

	/**
	 * API name: {@code max_input_length}
	 */
	@Nullable
	public final Integer maxInputLength() {
		return this.maxInputLength;
	}

	/**
	 * API name: {@code preserve_position_increments}
	 */
	@Nullable
	public final Boolean preservePositionIncrements() {
		return this.preservePositionIncrements;
	}

	/**
	 * API name: {@code preserve_separators}
	 */
	@Nullable
	public final Boolean preserveSeparators() {
		return this.preserveSeparators;
	}

	/**
	 * API name: {@code search_analyzer}
	 */
	@Nullable
	public final String searchAnalyzer() {
		return this.searchAnalyzer;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		generator.write("type", "completion");
		super.serializeInternal(generator, mapper);
		if (this.analyzer != null) {
			generator.writeKey("analyzer");
			generator.write(this.analyzer);

		}
		if (ApiTypeHelper.isDefined(this.contexts)) {
			generator.writeKey("contexts");
			generator.writeStartArray();
			for (SuggestContext item0 : this.contexts) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.maxInputLength != null) {
			generator.writeKey("max_input_length");
			generator.write(this.maxInputLength);

		}
		if (this.preservePositionIncrements != null) {
			generator.writeKey("preserve_position_increments");
			generator.write(this.preservePositionIncrements);

		}
		if (this.preserveSeparators != null) {
			generator.writeKey("preserve_separators");
			generator.write(this.preserveSeparators);

		}
		if (this.searchAnalyzer != null) {
			generator.writeKey("search_analyzer");
			generator.write(this.searchAnalyzer);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CompletionProperty}.
	 */

	public static class Builder extends DocValuesPropertyBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<CompletionProperty> {
		@Nullable
		private String analyzer;

		@Nullable
		private List<SuggestContext> contexts;

		@Nullable
		private Integer maxInputLength;

		@Nullable
		private Boolean preservePositionIncrements;

		@Nullable
		private Boolean preserveSeparators;

		@Nullable
		private String searchAnalyzer;

		/**
		 * API name: {@code analyzer}
		 */
		public final Builder analyzer(@Nullable String value) {
			this.analyzer = value;
			return this;
		}

		/**
		 * API name: {@code contexts}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>contexts</code>.
		 */
		public final Builder contexts(List<SuggestContext> list) {
			this.contexts = _listAddAll(this.contexts, list);
			return this;
		}

		/**
		 * API name: {@code contexts}
		 * <p>
		 * Adds one or more values to <code>contexts</code>.
		 */
		public final Builder contexts(SuggestContext value, SuggestContext... values) {
			this.contexts = _listAdd(this.contexts, value, values);
			return this;
		}

		/**
		 * API name: {@code contexts}
		 * <p>
		 * Adds a value to <code>contexts</code> using a builder lambda.
		 */
		public final Builder contexts(Function<SuggestContext.Builder, ObjectBuilder<SuggestContext>> fn) {
			return contexts(fn.apply(new SuggestContext.Builder()).build());
		}

		/**
		 * API name: {@code max_input_length}
		 */
		public final Builder maxInputLength(@Nullable Integer value) {
			this.maxInputLength = value;
			return this;
		}

		/**
		 * API name: {@code preserve_position_increments}
		 */
		public final Builder preservePositionIncrements(@Nullable Boolean value) {
			this.preservePositionIncrements = value;
			return this;
		}

		/**
		 * API name: {@code preserve_separators}
		 */
		public final Builder preserveSeparators(@Nullable Boolean value) {
			this.preserveSeparators = value;
			return this;
		}

		/**
		 * API name: {@code search_analyzer}
		 */
		public final Builder searchAnalyzer(@Nullable String value) {
			this.searchAnalyzer = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link CompletionProperty}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CompletionProperty build() {
			_checkSingleUse();

			return new CompletionProperty(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CompletionProperty}
	 */
	public static final JsonpDeserializer<CompletionProperty> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, CompletionProperty::setupCompletionPropertyDeserializer);

	protected static void setupCompletionPropertyDeserializer(ObjectDeserializer<CompletionProperty.Builder> op) {
		DocValuesPropertyBase.setupDocValuesPropertyBaseDeserializer(op);
		op.add(Builder::analyzer, JsonpDeserializer.stringDeserializer(), "analyzer");
		op.add(Builder::contexts, JsonpDeserializer.arrayDeserializer(SuggestContext._DESERIALIZER), "contexts");
		op.add(Builder::maxInputLength, JsonpDeserializer.integerDeserializer(), "max_input_length");
		op.add(Builder::preservePositionIncrements, JsonpDeserializer.booleanDeserializer(),
				"preserve_position_increments");
		op.add(Builder::preserveSeparators, JsonpDeserializer.booleanDeserializer(), "preserve_separators");
		op.add(Builder::searchAnalyzer, JsonpDeserializer.stringDeserializer(), "search_analyzer");

		op.ignore("type");
	}

}
