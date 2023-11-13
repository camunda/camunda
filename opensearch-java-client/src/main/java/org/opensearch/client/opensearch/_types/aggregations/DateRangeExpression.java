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

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.aggregations.DateRangeExpression

@JsonpDeserializable
public class DateRangeExpression implements JsonpSerializable {
	@Nullable
	private final FieldDateMath from;

	@Nullable
	private final String key;

	@Nullable
	private final FieldDateMath to;

	// ---------------------------------------------------------------------------------------------

	private DateRangeExpression(Builder builder) {

		this.from = builder.from;
		this.key = builder.key;
		this.to = builder.to;

	}

	public static DateRangeExpression of(Function<Builder, ObjectBuilder<DateRangeExpression>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code from}
	 */
	@Nullable
	public final FieldDateMath from() {
		return this.from;
	}

	/**
	 * API name: {@code key}
	 */
	@Nullable
	public final String key() {
		return this.key;
	}

	/**
	 * API name: {@code to}
	 */
	@Nullable
	public final FieldDateMath to() {
		return this.to;
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

		if (this.from != null) {
			generator.writeKey("from");
			this.from.serialize(generator, mapper);

		}
		if (this.key != null) {
			generator.writeKey("key");
			generator.write(this.key);

		}
		if (this.to != null) {
			generator.writeKey("to");
			this.to.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DateRangeExpression}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DateRangeExpression> {
		@Nullable
		private FieldDateMath from;

		@Nullable
		private String key;

		@Nullable
		private FieldDateMath to;

		/**
		 * API name: {@code from}
		 */
		public final Builder from(@Nullable FieldDateMath value) {
			this.from = value;
			return this;
		}

		/**
		 * API name: {@code from}
		 */
		public final Builder from(Function<FieldDateMath.Builder, ObjectBuilder<FieldDateMath>> fn) {
			return this.from(fn.apply(new FieldDateMath.Builder()).build());
		}

		/**
		 * API name: {@code key}
		 */
		public final Builder key(@Nullable String value) {
			this.key = value;
			return this;
		}

		/**
		 * API name: {@code to}
		 */
		public final Builder to(@Nullable FieldDateMath value) {
			this.to = value;
			return this;
		}

		/**
		 * API name: {@code to}
		 */
		public final Builder to(Function<FieldDateMath.Builder, ObjectBuilder<FieldDateMath>> fn) {
			return this.to(fn.apply(new FieldDateMath.Builder()).build());
		}

		/**
		 * Builds a {@link DateRangeExpression}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DateRangeExpression build() {
			_checkSingleUse();

			return new DateRangeExpression(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link DateRangeExpression}
	 */
	public static final JsonpDeserializer<DateRangeExpression> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, DateRangeExpression::setupDateRangeExpressionDeserializer);

	protected static void setupDateRangeExpressionDeserializer(ObjectDeserializer<DateRangeExpression.Builder> op) {

		op.add(Builder::from, FieldDateMath._DESERIALIZER, "from");
		op.add(Builder::key, JsonpDeserializer.stringDeserializer(), "key");
		op.add(Builder::to, FieldDateMath._DESERIALIZER, "to");

	}

}
