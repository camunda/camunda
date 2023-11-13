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
import java.util.function.Function;

// typedef: _types.aggregations.InferenceTopClassEntry


@JsonpDeserializable
public class InferenceTopClassEntry implements JsonpSerializable {
	private final FieldValue className;

	private final double classProbability;

	private final double classScore;

	// ---------------------------------------------------------------------------------------------

	private InferenceTopClassEntry(Builder builder) {

		this.className = ApiTypeHelper.requireNonNull(builder.className, this, "className");
		this.classProbability = ApiTypeHelper.requireNonNull(builder.classProbability, this, "classProbability");
		this.classScore = ApiTypeHelper.requireNonNull(builder.classScore, this, "classScore");

	}

	public static InferenceTopClassEntry of(Function<Builder, ObjectBuilder<InferenceTopClassEntry>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code class_name}
	 */
	public final FieldValue className() {
		return this.className;
	}

	/**
	 * Required - API name: {@code class_probability}
	 */
	public final double classProbability() {
		return this.classProbability;
	}

	/**
	 * Required - API name: {@code class_score}
	 */
	public final double classScore() {
		return this.classScore;
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

		generator.writeKey("class_name");
		this.className.serialize(generator, mapper);

		generator.writeKey("class_probability");
		generator.write(this.classProbability);

		generator.writeKey("class_score");
		generator.write(this.classScore);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link InferenceTopClassEntry}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<InferenceTopClassEntry> {
		private FieldValue className;

		private Double classProbability;

		private Double classScore;

		/**
		 * Required - API name: {@code class_name}
		 */
		public final Builder className(FieldValue value) {
			this.className = value;
			return this;
		}

		/**
		 * Required - API name: {@code class_name}
		 */
		public final Builder className(Function<FieldValue.Builder, ObjectBuilder<FieldValue>> fn) {
			return this.className(fn.apply(new FieldValue.Builder()).build());
		}

		/**
		 * Required - API name: {@code class_probability}
		 */
		public final Builder classProbability(double value) {
			this.classProbability = value;
			return this;
		}

		/**
		 * Required - API name: {@code class_score}
		 */
		public final Builder classScore(double value) {
			this.classScore = value;
			return this;
		}

		/**
		 * Builds a {@link InferenceTopClassEntry}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public InferenceTopClassEntry build() {
			_checkSingleUse();

			return new InferenceTopClassEntry(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link InferenceTopClassEntry}
	 */
	public static final JsonpDeserializer<InferenceTopClassEntry> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, InferenceTopClassEntry::setupInferenceTopClassEntryDeserializer);

	protected static void setupInferenceTopClassEntryDeserializer(
			ObjectDeserializer<InferenceTopClassEntry.Builder> op) {

		op.add(Builder::className, FieldValue._DESERIALIZER, "class_name");
		op.add(Builder::classProbability, JsonpDeserializer.doubleDeserializer(), "class_probability");
		op.add(Builder::classScore, JsonpDeserializer.doubleDeserializer(), "class_score");

	}

}
