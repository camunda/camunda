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

package org.opensearch.client.opensearch.nodes;

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

// typedef: nodes._types.JvmClasses

@JsonpDeserializable
public class JvmClasses implements JsonpSerializable {
	private final long currentLoadedCount;

	private final long totalLoadedCount;

	private final long totalUnloadedCount;

	// ---------------------------------------------------------------------------------------------

	private JvmClasses(Builder builder) {

		this.currentLoadedCount = ApiTypeHelper.requireNonNull(builder.currentLoadedCount, this, "currentLoadedCount");
		this.totalLoadedCount = ApiTypeHelper.requireNonNull(builder.totalLoadedCount, this, "totalLoadedCount");
		this.totalUnloadedCount = ApiTypeHelper.requireNonNull(builder.totalUnloadedCount, this, "totalUnloadedCount");

	}

	public static JvmClasses of(Function<Builder, ObjectBuilder<JvmClasses>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code current_loaded_count}
	 */
	public final long currentLoadedCount() {
		return this.currentLoadedCount;
	}

	/**
	 * Required - API name: {@code total_loaded_count}
	 */
	public final long totalLoadedCount() {
		return this.totalLoadedCount;
	}

	/**
	 * Required - API name: {@code total_unloaded_count}
	 */
	public final long totalUnloadedCount() {
		return this.totalUnloadedCount;
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

		generator.writeKey("current_loaded_count");
		generator.write(this.currentLoadedCount);

		generator.writeKey("total_loaded_count");
		generator.write(this.totalLoadedCount);

		generator.writeKey("total_unloaded_count");
		generator.write(this.totalUnloadedCount);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link JvmClasses}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<JvmClasses> {
		private Long currentLoadedCount;

		private Long totalLoadedCount;

		private Long totalUnloadedCount;

		/**
		 * Required - API name: {@code current_loaded_count}
		 */
		public final Builder currentLoadedCount(long value) {
			this.currentLoadedCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_loaded_count}
		 */
		public final Builder totalLoadedCount(long value) {
			this.totalLoadedCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code total_unloaded_count}
		 */
		public final Builder totalUnloadedCount(long value) {
			this.totalUnloadedCount = value;
			return this;
		}

		/**
		 * Builds a {@link JvmClasses}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public JvmClasses build() {
			_checkSingleUse();

			return new JvmClasses(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link JvmClasses}
	 */
	public static final JsonpDeserializer<JvmClasses> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			JvmClasses::setupJvmClassesDeserializer);

	protected static void setupJvmClassesDeserializer(ObjectDeserializer<JvmClasses.Builder> op) {

		op.add(Builder::currentLoadedCount, JsonpDeserializer.longDeserializer(), "current_loaded_count");
		op.add(Builder::totalLoadedCount, JsonpDeserializer.longDeserializer(), "total_loaded_count");
		op.add(Builder::totalUnloadedCount, JsonpDeserializer.longDeserializer(), "total_unloaded_count");

	}

}
