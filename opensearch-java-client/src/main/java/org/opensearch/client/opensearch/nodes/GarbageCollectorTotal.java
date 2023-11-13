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

// typedef: nodes._types.GarbageCollectorTotal


@JsonpDeserializable
public class GarbageCollectorTotal implements JsonpSerializable {
	private final long collectionCount;

	private final long collectionTimeInMillis;

	// ---------------------------------------------------------------------------------------------

	private GarbageCollectorTotal(Builder builder) {

		this.collectionCount = ApiTypeHelper.requireNonNull(builder.collectionCount, this, "collectionCount");
		this.collectionTimeInMillis = ApiTypeHelper.requireNonNull(builder.collectionTimeInMillis, this,
				"collectionTimeInMillis");

	}

	public static GarbageCollectorTotal of(Function<Builder, ObjectBuilder<GarbageCollectorTotal>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code collection_count}
	 */
	public final long collectionCount() {
		return this.collectionCount;
	}

	/**
	 * Required - API name: {@code collection_time_in_millis}
	 */
	public final long collectionTimeInMillis() {
		return this.collectionTimeInMillis;
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

		generator.writeKey("collection_count");
		generator.write(this.collectionCount);

		generator.writeKey("collection_time_in_millis");
		generator.write(this.collectionTimeInMillis);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GarbageCollectorTotal}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GarbageCollectorTotal> {
		private Long collectionCount;

		private Long collectionTimeInMillis;

		/**
		 * Required - API name: {@code collection_count}
		 */
		public final Builder collectionCount(long value) {
			this.collectionCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code collection_time_in_millis}
		 */
		public final Builder collectionTimeInMillis(long value) {
			this.collectionTimeInMillis = value;
			return this;
		}

		/**
		 * Builds a {@link GarbageCollectorTotal}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GarbageCollectorTotal build() {
			_checkSingleUse();

			return new GarbageCollectorTotal(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GarbageCollectorTotal}
	 */
	public static final JsonpDeserializer<GarbageCollectorTotal> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, GarbageCollectorTotal::setupGarbageCollectorTotalDeserializer);

	protected static void setupGarbageCollectorTotalDeserializer(ObjectDeserializer<GarbageCollectorTotal.Builder> op) {

		op.add(Builder::collectionCount, JsonpDeserializer.longDeserializer(), "collection_count");
		op.add(Builder::collectionTimeInMillis, JsonpDeserializer.longDeserializer(), "collection_time_in_millis");

	}

}
