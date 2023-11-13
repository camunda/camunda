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

// typedef: _types.ScriptSort

@JsonpDeserializable
public class ScriptSort implements SortOptionsVariant, JsonpSerializable {
	@Nullable
	private final SortOrder order;

	private final Script script;

	@Nullable
	private final ScriptSortType type;

	@Nullable
	private final SortMode mode;

	@Nullable
	private final NestedSortValue nested;

	// ---------------------------------------------------------------------------------------------

	private ScriptSort(Builder builder) {

		this.order = builder.order;
		this.script = ApiTypeHelper.requireNonNull(builder.script, this, "script");
		this.type = builder.type;
		this.mode = builder.mode;
		this.nested = builder.nested;

	}

	public static ScriptSort of(Function<Builder, ObjectBuilder<ScriptSort>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * SortOptions variant kind.
	 */
	@Override
	public SortOptions.Kind _sortOptionsKind() {
		return SortOptions.Kind.Script;
	}

	/**
	 * API name: {@code order}
	 */
	@Nullable
	public final SortOrder order() {
		return this.order;
	}

	/**
	 * Required - API name: {@code script}
	 */
	public final Script script() {
		return this.script;
	}

	/**
	 * API name: {@code type}
	 */
	@Nullable
	public final ScriptSortType type() {
		return this.type;
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
	 * Serialize this object to JSON.
	 */
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		generator.writeStartObject();
		serializeInternal(generator, mapper);
		generator.writeEnd();
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		if (this.order != null) {
			generator.writeKey("order");
			this.order.serialize(generator, mapper);
		}
		generator.writeKey("script");
		this.script.serialize(generator, mapper);

		if (this.type != null) {
			generator.writeKey("type");
			this.type.serialize(generator, mapper);
		}
		if (this.mode != null) {
			generator.writeKey("mode");
			this.mode.serialize(generator, mapper);
		}
		if (this.nested != null) {
			generator.writeKey("nested");
			this.nested.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ScriptSort}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ScriptSort> {
		@Nullable
		private SortOrder order;

		private Script script;

		@Nullable
		private ScriptSortType type;

		@Nullable
		private SortMode mode;

		@Nullable
		private NestedSortValue nested;

		/**
		 * API name: {@code order}
		 */
		public final Builder order(@Nullable SortOrder value) {
			this.order = value;
			return this;
		}

		/**
		 * Required - API name: {@code script}
		 */
		public final Builder script(Script value) {
			this.script = value;
			return this;
		}

		/**
		 * Required - API name: {@code script}
		 */
		public final Builder script(Function<Script.Builder, ObjectBuilder<Script>> fn) {
			return this.script(fn.apply(new Script.Builder()).build());
		}

		/**
		 * API name: {@code type}
		 */
		public final Builder type(@Nullable ScriptSortType value) {
			this.type = value;
			return this;
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
		 * Builds a {@link ScriptSort}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ScriptSort build() {
			_checkSingleUse();

			return new ScriptSort(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ScriptSort}
	 */
	public static final JsonpDeserializer<ScriptSort> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ScriptSort::setupScriptSortDeserializer);

	protected static void setupScriptSortDeserializer(ObjectDeserializer<ScriptSort.Builder> op) {

		op.add(Builder::order, SortOrder._DESERIALIZER, "order");
		op.add(Builder::script, Script._DESERIALIZER, "script");
		op.add(Builder::type, ScriptSortType._DESERIALIZER, "type");
		op.add(Builder::mode, SortMode._DESERIALIZER, "mode");
		op.add(Builder::nested, NestedSortValue._DESERIALIZER, "nested");

	}

}
