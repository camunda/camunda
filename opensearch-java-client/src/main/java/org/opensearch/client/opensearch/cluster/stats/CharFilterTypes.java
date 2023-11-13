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

package org.opensearch.client.opensearch.cluster.stats;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;

// typedef: cluster.stats.CharFilterTypes


@JsonpDeserializable
public class CharFilterTypes implements JsonpSerializable {
	private final List<FieldTypes> charFilterTypes;

	private final List<FieldTypes> tokenizerTypes;

	private final List<FieldTypes> filterTypes;

	private final List<FieldTypes> analyzerTypes;

	private final List<FieldTypes> builtInCharFilters;

	private final List<FieldTypes> builtInTokenizers;

	private final List<FieldTypes> builtInFilters;

	private final List<FieldTypes> builtInAnalyzers;

	// ---------------------------------------------------------------------------------------------

	private CharFilterTypes(Builder builder) {

		this.charFilterTypes = ApiTypeHelper.unmodifiableRequired(builder.charFilterTypes, this, "charFilterTypes");
		this.tokenizerTypes = ApiTypeHelper.unmodifiableRequired(builder.tokenizerTypes, this, "tokenizerTypes");
		this.filterTypes = ApiTypeHelper.unmodifiableRequired(builder.filterTypes, this, "filterTypes");
		this.analyzerTypes = ApiTypeHelper.unmodifiableRequired(builder.analyzerTypes, this, "analyzerTypes");
		this.builtInCharFilters = ApiTypeHelper.unmodifiableRequired(builder.builtInCharFilters, this,
				"builtInCharFilters");
		this.builtInTokenizers = ApiTypeHelper.unmodifiableRequired(builder.builtInTokenizers, this,
				"builtInTokenizers");
		this.builtInFilters = ApiTypeHelper.unmodifiableRequired(builder.builtInFilters, this, "builtInFilters");
		this.builtInAnalyzers = ApiTypeHelper.unmodifiableRequired(builder.builtInAnalyzers, this, "builtInAnalyzers");

	}

	public static CharFilterTypes of(Function<Builder, ObjectBuilder<CharFilterTypes>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code char_filter_types}
	 */
	public final List<FieldTypes> charFilterTypes() {
		return this.charFilterTypes;
	}

	/**
	 * Required - API name: {@code tokenizer_types}
	 */
	public final List<FieldTypes> tokenizerTypes() {
		return this.tokenizerTypes;
	}

	/**
	 * Required - API name: {@code filter_types}
	 */
	public final List<FieldTypes> filterTypes() {
		return this.filterTypes;
	}

	/**
	 * Required - API name: {@code analyzer_types}
	 */
	public final List<FieldTypes> analyzerTypes() {
		return this.analyzerTypes;
	}

	/**
	 * Required - API name: {@code built_in_char_filters}
	 */
	public final List<FieldTypes> builtInCharFilters() {
		return this.builtInCharFilters;
	}

	/**
	 * Required - API name: {@code built_in_tokenizers}
	 */
	public final List<FieldTypes> builtInTokenizers() {
		return this.builtInTokenizers;
	}

	/**
	 * Required - API name: {@code built_in_filters}
	 */
	public final List<FieldTypes> builtInFilters() {
		return this.builtInFilters;
	}

	/**
	 * Required - API name: {@code built_in_analyzers}
	 */
	public final List<FieldTypes> builtInAnalyzers() {
		return this.builtInAnalyzers;
	}

	/**
	 * Serialize this object to JSON.
	 */
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		serializeInternal(generator, mapper);
		generator.writeEnd();
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		if (ApiTypeHelper.isDefined(this.charFilterTypes)) {
			generator.writeKey("char_filter_types");
			generator.writeStartArray();
			for (FieldTypes item0 : this.charFilterTypes) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.tokenizerTypes)) {
			generator.writeKey("tokenizer_types");
			generator.writeStartArray();
			for (FieldTypes item0 : this.tokenizerTypes) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.filterTypes)) {
			generator.writeKey("filter_types");
			generator.writeStartArray();
			for (FieldTypes item0 : this.filterTypes) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.analyzerTypes)) {
			generator.writeKey("analyzer_types");
			generator.writeStartArray();
			for (FieldTypes item0 : this.analyzerTypes) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.builtInCharFilters)) {
			generator.writeKey("built_in_char_filters");
			generator.writeStartArray();
			for (FieldTypes item0 : this.builtInCharFilters) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.builtInTokenizers)) {
			generator.writeKey("built_in_tokenizers");
			generator.writeStartArray();
			for (FieldTypes item0 : this.builtInTokenizers) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.builtInFilters)) {
			generator.writeKey("built_in_filters");
			generator.writeStartArray();
			for (FieldTypes item0 : this.builtInFilters) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.builtInAnalyzers)) {
			generator.writeKey("built_in_analyzers");
			generator.writeStartArray();
			for (FieldTypes item0 : this.builtInAnalyzers) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CharFilterTypes}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<CharFilterTypes> {
		private List<FieldTypes> charFilterTypes;

		private List<FieldTypes> tokenizerTypes;

		private List<FieldTypes> filterTypes;

		private List<FieldTypes> analyzerTypes;

		private List<FieldTypes> builtInCharFilters;

		private List<FieldTypes> builtInTokenizers;

		private List<FieldTypes> builtInFilters;

		private List<FieldTypes> builtInAnalyzers;

		/**
		 * Required - API name: {@code char_filter_types}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>charFilterTypes</code>.
		 */
		public final Builder charFilterTypes(List<FieldTypes> list) {
			this.charFilterTypes = _listAddAll(this.charFilterTypes, list);
			return this;
		}

		/**
		 * Required - API name: {@code char_filter_types}
		 * <p>
		 * Adds one or more values to <code>charFilterTypes</code>.
		 */
		public final Builder charFilterTypes(FieldTypes value, FieldTypes... values) {
			this.charFilterTypes = _listAdd(this.charFilterTypes, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code char_filter_types}
		 * <p>
		 * Adds a value to <code>charFilterTypes</code> using a builder lambda.
		 */
		public final Builder charFilterTypes(Function<FieldTypes.Builder, ObjectBuilder<FieldTypes>> fn) {
			return charFilterTypes(fn.apply(new FieldTypes.Builder()).build());
		}

		/**
		 * Required - API name: {@code tokenizer_types}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>tokenizerTypes</code>.
		 */
		public final Builder tokenizerTypes(List<FieldTypes> list) {
			this.tokenizerTypes = _listAddAll(this.tokenizerTypes, list);
			return this;
		}

		/**
		 * Required - API name: {@code tokenizer_types}
		 * <p>
		 * Adds one or more values to <code>tokenizerTypes</code>.
		 */
		public final Builder tokenizerTypes(FieldTypes value, FieldTypes... values) {
			this.tokenizerTypes = _listAdd(this.tokenizerTypes, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code tokenizer_types}
		 * <p>
		 * Adds a value to <code>tokenizerTypes</code> using a builder lambda.
		 */
		public final Builder tokenizerTypes(Function<FieldTypes.Builder, ObjectBuilder<FieldTypes>> fn) {
			return tokenizerTypes(fn.apply(new FieldTypes.Builder()).build());
		}

		/**
		 * Required - API name: {@code filter_types}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>filterTypes</code>.
		 */
		public final Builder filterTypes(List<FieldTypes> list) {
			this.filterTypes = _listAddAll(this.filterTypes, list);
			return this;
		}

		/**
		 * Required - API name: {@code filter_types}
		 * <p>
		 * Adds one or more values to <code>filterTypes</code>.
		 */
		public final Builder filterTypes(FieldTypes value, FieldTypes... values) {
			this.filterTypes = _listAdd(this.filterTypes, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code filter_types}
		 * <p>
		 * Adds a value to <code>filterTypes</code> using a builder lambda.
		 */
		public final Builder filterTypes(Function<FieldTypes.Builder, ObjectBuilder<FieldTypes>> fn) {
			return filterTypes(fn.apply(new FieldTypes.Builder()).build());
		}

		/**
		 * Required - API name: {@code analyzer_types}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>analyzerTypes</code>.
		 */
		public final Builder analyzerTypes(List<FieldTypes> list) {
			this.analyzerTypes = _listAddAll(this.analyzerTypes, list);
			return this;
		}

		/**
		 * Required - API name: {@code analyzer_types}
		 * <p>
		 * Adds one or more values to <code>analyzerTypes</code>.
		 */
		public final Builder analyzerTypes(FieldTypes value, FieldTypes... values) {
			this.analyzerTypes = _listAdd(this.analyzerTypes, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code analyzer_types}
		 * <p>
		 * Adds a value to <code>analyzerTypes</code> using a builder lambda.
		 */
		public final Builder analyzerTypes(Function<FieldTypes.Builder, ObjectBuilder<FieldTypes>> fn) {
			return analyzerTypes(fn.apply(new FieldTypes.Builder()).build());
		}

		/**
		 * Required - API name: {@code built_in_char_filters}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>builtInCharFilters</code>.
		 */
		public final Builder builtInCharFilters(List<FieldTypes> list) {
			this.builtInCharFilters = _listAddAll(this.builtInCharFilters, list);
			return this;
		}

		/**
		 * Required - API name: {@code built_in_char_filters}
		 * <p>
		 * Adds one or more values to <code>builtInCharFilters</code>.
		 */
		public final Builder builtInCharFilters(FieldTypes value, FieldTypes... values) {
			this.builtInCharFilters = _listAdd(this.builtInCharFilters, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code built_in_char_filters}
		 * <p>
		 * Adds a value to <code>builtInCharFilters</code> using a builder lambda.
		 */
		public final Builder builtInCharFilters(Function<FieldTypes.Builder, ObjectBuilder<FieldTypes>> fn) {
			return builtInCharFilters(fn.apply(new FieldTypes.Builder()).build());
		}

		/**
		 * Required - API name: {@code built_in_tokenizers}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>builtInTokenizers</code>.
		 */
		public final Builder builtInTokenizers(List<FieldTypes> list) {
			this.builtInTokenizers = _listAddAll(this.builtInTokenizers, list);
			return this;
		}

		/**
		 * Required - API name: {@code built_in_tokenizers}
		 * <p>
		 * Adds one or more values to <code>builtInTokenizers</code>.
		 */
		public final Builder builtInTokenizers(FieldTypes value, FieldTypes... values) {
			this.builtInTokenizers = _listAdd(this.builtInTokenizers, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code built_in_tokenizers}
		 * <p>
		 * Adds a value to <code>builtInTokenizers</code> using a builder lambda.
		 */
		public final Builder builtInTokenizers(Function<FieldTypes.Builder, ObjectBuilder<FieldTypes>> fn) {
			return builtInTokenizers(fn.apply(new FieldTypes.Builder()).build());
		}

		/**
		 * Required - API name: {@code built_in_filters}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>builtInFilters</code>.
		 */
		public final Builder builtInFilters(List<FieldTypes> list) {
			this.builtInFilters = _listAddAll(this.builtInFilters, list);
			return this;
		}

		/**
		 * Required - API name: {@code built_in_filters}
		 * <p>
		 * Adds one or more values to <code>builtInFilters</code>.
		 */
		public final Builder builtInFilters(FieldTypes value, FieldTypes... values) {
			this.builtInFilters = _listAdd(this.builtInFilters, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code built_in_filters}
		 * <p>
		 * Adds a value to <code>builtInFilters</code> using a builder lambda.
		 */
		public final Builder builtInFilters(Function<FieldTypes.Builder, ObjectBuilder<FieldTypes>> fn) {
			return builtInFilters(fn.apply(new FieldTypes.Builder()).build());
		}

		/**
		 * Required - API name: {@code built_in_analyzers}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>builtInAnalyzers</code>.
		 */
		public final Builder builtInAnalyzers(List<FieldTypes> list) {
			this.builtInAnalyzers = _listAddAll(this.builtInAnalyzers, list);
			return this;
		}

		/**
		 * Required - API name: {@code built_in_analyzers}
		 * <p>
		 * Adds one or more values to <code>builtInAnalyzers</code>.
		 */
		public final Builder builtInAnalyzers(FieldTypes value, FieldTypes... values) {
			this.builtInAnalyzers = _listAdd(this.builtInAnalyzers, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code built_in_analyzers}
		 * <p>
		 * Adds a value to <code>builtInAnalyzers</code> using a builder lambda.
		 */
		public final Builder builtInAnalyzers(Function<FieldTypes.Builder, ObjectBuilder<FieldTypes>> fn) {
			return builtInAnalyzers(fn.apply(new FieldTypes.Builder()).build());
		}

		/**
		 * Builds a {@link CharFilterTypes}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CharFilterTypes build() {
			_checkSingleUse();

			return new CharFilterTypes(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CharFilterTypes}
	 */
	public static final JsonpDeserializer<CharFilterTypes> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			CharFilterTypes::setupCharFilterTypesDeserializer);

	protected static void setupCharFilterTypesDeserializer(ObjectDeserializer<CharFilterTypes.Builder> op) {

		op.add(Builder::charFilterTypes, JsonpDeserializer.arrayDeserializer(FieldTypes._DESERIALIZER),
				"char_filter_types");
		op.add(Builder::tokenizerTypes, JsonpDeserializer.arrayDeserializer(FieldTypes._DESERIALIZER),
				"tokenizer_types");
		op.add(Builder::filterTypes, JsonpDeserializer.arrayDeserializer(FieldTypes._DESERIALIZER), "filter_types");
		op.add(Builder::analyzerTypes, JsonpDeserializer.arrayDeserializer(FieldTypes._DESERIALIZER), "analyzer_types");
		op.add(Builder::builtInCharFilters, JsonpDeserializer.arrayDeserializer(FieldTypes._DESERIALIZER),
				"built_in_char_filters");
		op.add(Builder::builtInTokenizers, JsonpDeserializer.arrayDeserializer(FieldTypes._DESERIALIZER),
				"built_in_tokenizers");
		op.add(Builder::builtInFilters, JsonpDeserializer.arrayDeserializer(FieldTypes._DESERIALIZER),
				"built_in_filters");
		op.add(Builder::builtInAnalyzers, JsonpDeserializer.arrayDeserializer(FieldTypes._DESERIALIZER),
				"built_in_analyzers");

	}

}
