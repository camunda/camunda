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

// typedef: _types.Retries

@JsonpDeserializable
public class Retries implements JsonpSerializable {
	private final long bulk;

	private final long search;

	// ---------------------------------------------------------------------------------------------

	private Retries(Builder builder) {

		this.bulk = ApiTypeHelper.requireNonNull(builder.bulk, this, "bulk");
		this.search = ApiTypeHelper.requireNonNull(builder.search, this, "search");

	}

	public static Retries of(Function<Builder, ObjectBuilder<Retries>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code bulk}
	 */
	public final long bulk() {
		return this.bulk;
	}

	/**
	 * Required - API name: {@code search}
	 */
	public final long search() {
		return this.search;
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

		generator.writeKey("bulk");
		generator.write(this.bulk);

		generator.writeKey("search");
		generator.write(this.search);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Retries}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Retries> {
		private Long bulk;

		private Long search;

		/**
		 * Required - API name: {@code bulk}
		 */
		public final Builder bulk(long value) {
			this.bulk = value;
			return this;
		}

		/**
		 * Required - API name: {@code search}
		 */
		public final Builder search(long value) {
			this.search = value;
			return this;
		}

		/**
		 * Builds a {@link Retries}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Retries build() {
			_checkSingleUse();

			return new Retries(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Retries}
	 */
	public static final JsonpDeserializer<Retries> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Retries::setupRetriesDeserializer);

	protected static void setupRetriesDeserializer(ObjectDeserializer<Retries.Builder> op) {

		op.add(Builder::bulk, JsonpDeserializer.longDeserializer(), "bulk");
		op.add(Builder::search, JsonpDeserializer.longDeserializer(), "search");

	}

}
