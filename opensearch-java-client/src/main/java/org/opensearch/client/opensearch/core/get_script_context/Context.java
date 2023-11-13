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

package org.opensearch.client.opensearch.core.get_script_context;

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

// typedef: _global.get_script_context.Context


@JsonpDeserializable
public class Context implements JsonpSerializable {
	private final List<ContextMethod> methods;

	private final String name;

	// ---------------------------------------------------------------------------------------------

	private Context(Builder builder) {

		this.methods = ApiTypeHelper.unmodifiableRequired(builder.methods, this, "methods");
		this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");

	}

	public static Context of(Function<Builder, ObjectBuilder<Context>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code methods}
	 */
	public final List<ContextMethod> methods() {
		return this.methods;
	}

	/**
	 * Required - API name: {@code name}
	 */
	public final String name() {
		return this.name;
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

		if (ApiTypeHelper.isDefined(this.methods)) {
			generator.writeKey("methods");
			generator.writeStartArray();
			for (ContextMethod item0 : this.methods) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("name");
		generator.write(this.name);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Context}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Context> {
		private List<ContextMethod> methods;

		private String name;

		/**
		 * Required - API name: {@code methods}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>methods</code>.
		 */
		public final Builder methods(List<ContextMethod> list) {
			this.methods = _listAddAll(this.methods, list);
			return this;
		}

		/**
		 * Required - API name: {@code methods}
		 * <p>
		 * Adds one or more values to <code>methods</code>.
		 */
		public final Builder methods(ContextMethod value, ContextMethod... values) {
			this.methods = _listAdd(this.methods, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code methods}
		 * <p>
		 * Adds a value to <code>methods</code> using a builder lambda.
		 */
		public final Builder methods(Function<ContextMethod.Builder, ObjectBuilder<ContextMethod>> fn) {
			return methods(fn.apply(new ContextMethod.Builder()).build());
		}

		/**
		 * Required - API name: {@code name}
		 */
		public final Builder name(String value) {
			this.name = value;
			return this;
		}

		/**
		 * Builds a {@link Context}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Context build() {
			_checkSingleUse();

			return new Context(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Context}
	 */
	public static final JsonpDeserializer<Context> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Context::setupContextDeserializer);

	protected static void setupContextDeserializer(ObjectDeserializer<Context.Builder> op) {

		op.add(Builder::methods, JsonpDeserializer.arrayDeserializer(ContextMethod._DESERIALIZER), "methods");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");

	}

}
