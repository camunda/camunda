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

package org.opensearch.client.opensearch._types;

import org.opensearch.client.opensearch._types.mapping.FieldType;
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
import javax.annotation.Nullable;

// typedef: _types.FieldSort

@JsonpDeserializable
public class FieldSort implements JsonpSerializable {
	// Single key dictionary
	private final String field;

	@Nullable
	private final FieldValue missing;

	@Nullable
	private final SortMode mode;

	@Nullable
	private final NestedSortValue nested;

	@Nullable
	private final SortOrder order;

	@Nullable
	private final FieldType unmappedType;

	@Nullable
	private final FieldSortNumericType numericType;

	@Nullable
	private final String format;

	// ---------------------------------------------------------------------------------------------

	private FieldSort(Builder builder) {

		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");

		this.missing = builder.missing;
		this.mode = builder.mode;
		this.nested = builder.nested;
		this.order = builder.order;
		this.unmappedType = builder.unmappedType;
		this.numericType = builder.numericType;
		this.format = builder.format;

	}

	public static FieldSort of(Function<Builder, ObjectBuilder<FieldSort>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - The target field
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code missing}
	 */
	@Nullable
	public final FieldValue missing() {
		return this.missing;
	}

	/**
	 * API name: {@code mode}
	 */
	@Nullable
	public final SortMode mode() {
		return this.mode;
	}

	/**
	 * API name: {@code nested}
	 */
	@Nullable
	public final NestedSortValue nested() {
		return this.nested;
	}

	/**
	 * API name: {@code order}
	 */
	@Nullable
	public final SortOrder order() {
		return this.order;
	}

	/**
	 * API name: {@code unmapped_type}
	 */
	@Nullable
	public final FieldType unmappedType() {
		return this.unmappedType;
	}

	/**
	 * API name: {@code numeric_type}
	 */
	@Nullable
	public final FieldSortNumericType numericType() {
		return this.numericType;
	}

	/**
	 * API name: {@code format}
	 */
	@Nullable
	public final String format() {
		return this.format;
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
		generator.writeStartObject(this.field);

		if (this.missing != null) {
			generator.writeKey("missing");
			this.missing.serialize(generator, mapper);

		}
		if (this.mode != null) {
			generator.writeKey("mode");
			this.mode.serialize(generator, mapper);
		}
		if (this.nested != null) {
			generator.writeKey("nested");
			this.nested.serialize(generator, mapper);

		}
		if (this.order != null) {
			generator.writeKey("order");
			this.order.serialize(generator, mapper);
		}
		if (this.unmappedType != null) {
			generator.writeKey("unmapped_type");
			this.unmappedType.serialize(generator, mapper);
		}
		if (this.numericType != null) {
			generator.writeKey("numeric_type");
			this.numericType.serialize(generator, mapper);
		}
		if (this.format != null) {
			generator.writeKey("format");
			generator.write(this.format);

		}

		generator.writeEnd();

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link FieldSort}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<FieldSort> {
		private String field;

		/**
		 * Required - The target field
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		@Nullable
		private FieldValue missing;

		@Nullable
		private SortMode mode;

		@Nullable
		private NestedSortValue nested;

		@Nullable
		private SortOrder order;

		@Nullable
		private FieldType unmappedType;

		@Nullable
		private FieldSortNumericType numericType;

		@Nullable
		private String format;

		/**
		 * API name: {@code missing}
		 */
		public final Builder missing(@Nullable FieldValue value) {
			this.missing = value;
			return this;
		}

		/**
		 * API name: {@code missing}
		 */
		public final Builder missing(Function<FieldValue.Builder, ObjectBuilder<FieldValue>> fn) {
			return this.missing(fn.apply(new FieldValue.Builder()).build());
		}

		/**
		 * API name: {@code mode}
		 */
		public final Builder mode(@Nullable SortMode value) {
			this.mode = value;
			return this;
		}

		/**
		 * API name: {@code nested}
		 */
		public final Builder nested(@Nullable NestedSortValue value) {
			this.nested = value;
			return this;
		}

		/**
		 * API name: {@code nested}
		 */
		public final Builder nested(Function<NestedSortValue.Builder, ObjectBuilder<NestedSortValue>> fn) {
			return this.nested(fn.apply(new NestedSortValue.Builder()).build());
		}

		/**
		 * API name: {@code order}
		 */
		public final Builder order(@Nullable SortOrder value) {
			this.order = value;
			return this;
		}

		/**
		 * API name: {@code unmapped_type}
		 */
		public final Builder unmappedType(@Nullable FieldType value) {
			this.unmappedType = value;
			return this;
		}

		/**
		 * API name: {@code numeric_type}
		 */
		public final Builder numericType(@Nullable FieldSortNumericType value) {
			this.numericType = value;
			return this;
		}

		/**
		 * API name: {@code format}
		 */
		public final Builder format(@Nullable String value) {
			this.format = value;
			return this;
		}

		/**
		 * Builds a {@link FieldSort}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public FieldSort build() {
			_checkSingleUse();

			return new FieldSort(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link FieldSort}
	 */
	public static final JsonpDeserializer<FieldSort> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			FieldSort::setupFieldSortDeserializer);

	protected static void setupFieldSortDeserializer(ObjectDeserializer<FieldSort.Builder> op) {

		op.add(Builder::missing, FieldValue._DESERIALIZER, "missing");
		op.add(Builder::mode, SortMode._DESERIALIZER, "mode");
		op.add(Builder::nested, NestedSortValue._DESERIALIZER, "nested");
		op.add(Builder::order, SortOrder._DESERIALIZER, "order");
		op.add(Builder::unmappedType, FieldType._DESERIALIZER, "unmapped_type");
		op.add(Builder::numericType, FieldSortNumericType._DESERIALIZER, "numeric_type");
		op.add(Builder::format, JsonpDeserializer.stringDeserializer(), "format");

		op.setKey(Builder::field, JsonpDeserializer.stringDeserializer());
		op.shortcutProperty("order");

	}

}
