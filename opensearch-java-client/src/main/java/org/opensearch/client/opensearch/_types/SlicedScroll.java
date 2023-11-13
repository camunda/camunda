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

// typedef: _types.SlicedScroll

@JsonpDeserializable
public class SlicedScroll implements JsonpSerializable {
	@Nullable
	private final String field;

	private final int id;

	private final int max;

	// ---------------------------------------------------------------------------------------------

	private SlicedScroll(Builder builder) {

		this.field = builder.field;
		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.max = ApiTypeHelper.requireNonNull(builder.max, this, "max");

	}

	public static SlicedScroll of(Function<Builder, ObjectBuilder<SlicedScroll>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code field}
	 */
	@Nullable
	public final String field() {
		return this.field;
	}

	/**
	 * Required - API name: {@code id}
	 */
	public final int id() {
		return this.id;
	}

	/**
	 * Required - API name: {@code max}
	 */
	public final int max() {
		return this.max;
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

		if (this.field != null) {
			generator.writeKey("field");
			generator.write(this.field);

		}
		generator.writeKey("id");
		generator.write(this.id);

		generator.writeKey("max");
		generator.write(this.max);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SlicedScroll}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<SlicedScroll> {
		@Nullable
		private String field;

		private Integer id;

		private Integer max;

		/**
		 * API name: {@code field}
		 */
		public final Builder field(@Nullable String value) {
			this.field = value;
			return this;
		}

		/**
		 * Required - API name: {@code id}
		 */
		public final Builder id(int value) {
			this.id = value;
			return this;
		}

		/**
		 * Required - API name: {@code max}
		 */
		public final Builder max(int value) {
			this.max = value;
			return this;
		}

		/**
		 * Builds a {@link SlicedScroll}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public SlicedScroll build() {
			_checkSingleUse();

			return new SlicedScroll(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link SlicedScroll}
	 */
	public static final JsonpDeserializer<SlicedScroll> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			SlicedScroll::setupSlicedScrollDeserializer);

	protected static void setupSlicedScrollDeserializer(ObjectDeserializer<SlicedScroll.Builder> op) {

		op.add(Builder::field, JsonpDeserializer.stringDeserializer(), "field");
		op.add(Builder::id, JsonpDeserializer.integerDeserializer(), "id");
		op.add(Builder::max, JsonpDeserializer.integerDeserializer(), "max");

	}

}
