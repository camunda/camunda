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

package org.opensearch.client.opensearch.ingest;

import org.opensearch.client.json.JsonData;
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

// typedef: ingest._types.CsvProcessor


@JsonpDeserializable
public class CsvProcessor extends ProcessorBase implements ProcessorVariant {
	private final JsonData emptyValue;

	@Nullable
	private final String description;

	private final String field;

	@Nullable
	private final Boolean ignoreMissing;

	@Nullable
	private final String quote;

	@Nullable
	private final String separator;

	private final List<String> targetFields;

	private final boolean trim;

	// ---------------------------------------------------------------------------------------------

	private CsvProcessor(Builder builder) {
		super(builder);

		this.emptyValue = ApiTypeHelper.requireNonNull(builder.emptyValue, this, "emptyValue");
		this.description = builder.description;
		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");
		this.ignoreMissing = builder.ignoreMissing;
		this.quote = builder.quote;
		this.separator = builder.separator;
		this.targetFields = ApiTypeHelper.unmodifiableRequired(builder.targetFields, this, "targetFields");
		this.trim = ApiTypeHelper.requireNonNull(builder.trim, this, "trim");

	}

	public static CsvProcessor of(Function<Builder, ObjectBuilder<CsvProcessor>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Processor variant kind.
	 */
	@Override
	public Processor.Kind _processorKind() {
		return Processor.Kind.Csv;
	}

	/**
	 * Required - API name: {@code empty_value}
	 */
	public final JsonData emptyValue() {
		return this.emptyValue;
	}

	/**
	 * API name: {@code description}
	 */
	@Nullable
	public final String description() {
		return this.description;
	}

	/**
	 * Required - API name: {@code field}
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code ignore_missing}
	 */
	@Nullable
	public final Boolean ignoreMissing() {
		return this.ignoreMissing;
	}

	/**
	 * API name: {@code quote}
	 */
	@Nullable
	public final String quote() {
		return this.quote;
	}

	/**
	 * API name: {@code separator}
	 */
	@Nullable
	public final String separator() {
		return this.separator;
	}

	/**
	 * Required - API name: {@code target_fields}
	 */
	public final List<String> targetFields() {
		return this.targetFields;
	}

	/**
	 * Required - API name: {@code trim}
	 */
	public final boolean trim() {
		return this.trim;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("empty_value");
		this.emptyValue.serialize(generator, mapper);

		if (this.description != null) {
			generator.writeKey("description");
			generator.write(this.description);

		}
		generator.writeKey("field");
		generator.write(this.field);

		if (this.ignoreMissing != null) {
			generator.writeKey("ignore_missing");
			generator.write(this.ignoreMissing);

		}
		if (this.quote != null) {
			generator.writeKey("quote");
			generator.write(this.quote);

		}
		if (this.separator != null) {
			generator.writeKey("separator");
			generator.write(this.separator);

		}
		if (ApiTypeHelper.isDefined(this.targetFields)) {
			generator.writeKey("target_fields");
			generator.writeStartArray();
			for (String item0 : this.targetFields) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		generator.writeKey("trim");
		generator.write(this.trim);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link CsvProcessor}.
	 */

	public static class Builder extends ProcessorBase.AbstractBuilder<Builder> implements ObjectBuilder<CsvProcessor> {
		private JsonData emptyValue;

		@Nullable
		private String description;

		private String field;

		@Nullable
		private Boolean ignoreMissing;

		@Nullable
		private String quote;

		@Nullable
		private String separator;

		private List<String> targetFields;

		private Boolean trim;

		/**
		 * Required - API name: {@code empty_value}
		 */
		public final Builder emptyValue(JsonData value) {
			this.emptyValue = value;
			return this;
		}

		/**
		 * API name: {@code description}
		 */
		public final Builder description(@Nullable String value) {
			this.description = value;
			return this;
		}

		/**
		 * Required - API name: {@code field}
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		/**
		 * API name: {@code ignore_missing}
		 */
		public final Builder ignoreMissing(@Nullable Boolean value) {
			this.ignoreMissing = value;
			return this;
		}

		/**
		 * API name: {@code quote}
		 */
		public final Builder quote(@Nullable String value) {
			this.quote = value;
			return this;
		}

		/**
		 * API name: {@code separator}
		 */
		public final Builder separator(@Nullable String value) {
			this.separator = value;
			return this;
		}

		/**
		 * Required - API name: {@code target_fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>targetFields</code>.
		 */
		public final Builder targetFields(List<String> list) {
			this.targetFields = _listAddAll(this.targetFields, list);
			return this;
		}

		/**
		 * Required - API name: {@code target_fields}
		 * <p>
		 * Adds one or more values to <code>targetFields</code>.
		 */
		public final Builder targetFields(String value, String... values) {
			this.targetFields = _listAdd(this.targetFields, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code trim}
		 */
		public final Builder trim(boolean value) {
			this.trim = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link CsvProcessor}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public CsvProcessor build() {
			_checkSingleUse();

			return new CsvProcessor(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link CsvProcessor}
	 */
	public static final JsonpDeserializer<CsvProcessor> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			CsvProcessor::setupCsvProcessorDeserializer);

	protected static void setupCsvProcessorDeserializer(ObjectDeserializer<CsvProcessor.Builder> op) {
		setupProcessorBaseDeserializer(op);
		op.add(Builder::emptyValue, JsonData._DESERIALIZER, "empty_value");
		op.add(Builder::description, JsonpDeserializer.stringDeserializer(), "description");
		op.add(Builder::field, JsonpDeserializer.stringDeserializer(), "field");
		op.add(Builder::ignoreMissing, JsonpDeserializer.booleanDeserializer(), "ignore_missing");
		op.add(Builder::quote, JsonpDeserializer.stringDeserializer(), "quote");
		op.add(Builder::separator, JsonpDeserializer.stringDeserializer(), "separator");
		op.add(Builder::targetFields, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"target_fields");
		op.add(Builder::trim, JsonpDeserializer.booleanDeserializer(), "trim");

	}

}
