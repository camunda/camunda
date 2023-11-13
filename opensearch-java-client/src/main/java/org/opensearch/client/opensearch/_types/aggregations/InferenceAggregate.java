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

package org.opensearch.client.opensearch._types.aggregations;

import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.InferenceAggregate

@JsonpDeserializable
public class InferenceAggregate extends AggregateBase implements AggregateVariant {
	private final Map<String, JsonData> data;

	@Nullable
	private final FieldValue value;

	private final List<InferenceFeatureImportance> featureImportance;

	private final List<InferenceTopClassEntry> topClasses;

	@Nullable
	private final String warning;

	// ---------------------------------------------------------------------------------------------

	private InferenceAggregate(Builder builder) {
		super(builder);
		this.data = ApiTypeHelper.unmodifiable(builder.data);

		this.value = builder.value;
		this.featureImportance = ApiTypeHelper.unmodifiable(builder.featureImportance);
		this.topClasses = ApiTypeHelper.unmodifiable(builder.topClasses);
		this.warning = builder.warning;

	}

	public static InferenceAggregate of(Function<Builder, ObjectBuilder<InferenceAggregate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Aggregate variant kind.
	 */
	@Override
	public Aggregate.Kind _aggregateKind() {
		return Aggregate.Kind.Inference;
	}

	/**
	 * Additional data
	 */
	public final Map<String, JsonData> data() {
		return this.data;
	}

	/**
	 * API name: {@code value}
	 */
	@Nullable
	public final FieldValue value() {
		return this.value;
	}

	/**
	 * API name: {@code feature_importance}
	 */
	public final List<InferenceFeatureImportance> featureImportance() {
		return this.featureImportance;
	}

	/**
	 * API name: {@code top_classes}
	 */
	public final List<InferenceTopClassEntry> topClasses() {
		return this.topClasses;
	}

	/**
	 * API name: {@code warning}
	 */
	@Nullable
	public final String warning() {
		return this.warning;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		for (Map.Entry<String, JsonData> item0 : this.data.entrySet()) {
			generator.writeKey(item0.getKey());
			item0.getValue().serialize(generator, mapper);

		}

		super.serializeInternal(generator, mapper);
		if (this.value != null) {
			generator.writeKey("value");
			this.value.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.featureImportance)) {
			generator.writeKey("feature_importance");
			generator.writeStartArray();
			for (InferenceFeatureImportance item0 : this.featureImportance) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.topClasses)) {
			generator.writeKey("top_classes");
			generator.writeStartArray();
			for (InferenceTopClassEntry item0 : this.topClasses) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.warning != null) {
			generator.writeKey("warning");
			generator.write(this.warning);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link InferenceAggregate}.
	 */

	public static class Builder extends AggregateBase.AbstractBuilder<Builder>
			implements
				ObjectBuilder<InferenceAggregate> {
		@Nullable
		private Map<String, JsonData> data = new HashMap<>();

		/**
		 * Additional data
		 * <p>
		 * Adds all entries of <code>map</code> to <code>data</code>.
		 */
		public final Builder data(Map<String, JsonData> map) {
			this.data = _mapPutAll(this.data, map);
			return this;
		}

		/**
		 * Additional data
		 * <p>
		 * Adds an entry to <code>data</code>.
		 */
		public final Builder data(String key, JsonData value) {
			this.data = _mapPut(this.data, key, value);
			return this;
		}

		@Nullable
		private FieldValue value;

		@Nullable
		private List<InferenceFeatureImportance> featureImportance;

		@Nullable
		private List<InferenceTopClassEntry> topClasses;

		@Nullable
		private String warning;

		/**
		 * API name: {@code value}
		 */
		public final Builder value(@Nullable FieldValue value) {
			this.value = value;
			return this;
		}

		/**
		 * API name: {@code value}
		 */
		public final Builder value(Function<FieldValue.Builder, ObjectBuilder<FieldValue>> fn) {
			return this.value(fn.apply(new FieldValue.Builder()).build());
		}

		/**
		 * API name: {@code feature_importance}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>featureImportance</code>.
		 */
		public final Builder featureImportance(List<InferenceFeatureImportance> list) {
			this.featureImportance = _listAddAll(this.featureImportance, list);
			return this;
		}

		/**
		 * API name: {@code feature_importance}
		 * <p>
		 * Adds one or more values to <code>featureImportance</code>.
		 */
		public final Builder featureImportance(InferenceFeatureImportance value, InferenceFeatureImportance... values) {
			this.featureImportance = _listAdd(this.featureImportance, value, values);
			return this;
		}

		/**
		 * API name: {@code feature_importance}
		 * <p>
		 * Adds a value to <code>featureImportance</code> using a builder lambda.
		 */
		public final Builder featureImportance(
				Function<InferenceFeatureImportance.Builder, ObjectBuilder<InferenceFeatureImportance>> fn) {
			return featureImportance(fn.apply(new InferenceFeatureImportance.Builder()).build());
		}

		/**
		 * API name: {@code top_classes}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>topClasses</code>.
		 */
		public final Builder topClasses(List<InferenceTopClassEntry> list) {
			this.topClasses = _listAddAll(this.topClasses, list);
			return this;
		}

		/**
		 * API name: {@code top_classes}
		 * <p>
		 * Adds one or more values to <code>topClasses</code>.
		 */
		public final Builder topClasses(InferenceTopClassEntry value, InferenceTopClassEntry... values) {
			this.topClasses = _listAdd(this.topClasses, value, values);
			return this;
		}

		/**
		 * API name: {@code top_classes}
		 * <p>
		 * Adds a value to <code>topClasses</code> using a builder lambda.
		 */
		public final Builder topClasses(
				Function<InferenceTopClassEntry.Builder, ObjectBuilder<InferenceTopClassEntry>> fn) {
			return topClasses(fn.apply(new InferenceTopClassEntry.Builder()).build());
		}

		/**
		 * API name: {@code warning}
		 */
		public final Builder warning(@Nullable String value) {
			this.warning = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link InferenceAggregate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public InferenceAggregate build() {
			_checkSingleUse();

			return new InferenceAggregate(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link InferenceAggregate}
	 */
	public static final JsonpDeserializer<InferenceAggregate> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, InferenceAggregate::setupInferenceAggregateDeserializer);

	protected static void setupInferenceAggregateDeserializer(ObjectDeserializer<InferenceAggregate.Builder> op) {
		AggregateBase.setupAggregateBaseDeserializer(op);
		op.add(Builder::value, FieldValue._DESERIALIZER, "value");
		op.add(Builder::featureImportance,
				JsonpDeserializer.arrayDeserializer(InferenceFeatureImportance._DESERIALIZER), "feature_importance");
		op.add(Builder::topClasses, JsonpDeserializer.arrayDeserializer(InferenceTopClassEntry._DESERIALIZER),
				"top_classes");
		op.add(Builder::warning, JsonpDeserializer.stringDeserializer(), "warning");

		op.setUnknownFieldHandler((builder, name, parser, mapper) -> {
			if (builder.data == null) {
				builder.data = new HashMap<>();
			}
			builder.data.put(name, JsonData._DESERIALIZER.deserialize(parser, mapper));
		});

	}

}
