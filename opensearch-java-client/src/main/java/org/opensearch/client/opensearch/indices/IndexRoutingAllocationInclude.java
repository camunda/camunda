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

package org.opensearch.client.opensearch.indices;

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

// typedef: indices._types.IndexRoutingAllocationInclude


@JsonpDeserializable
public class IndexRoutingAllocationInclude implements JsonpSerializable {
	@Nullable
	private final String tierPreference;

	@Nullable
	private final String id;

	// ---------------------------------------------------------------------------------------------

	private IndexRoutingAllocationInclude(Builder builder) {

		this.tierPreference = builder.tierPreference;
		this.id = builder.id;

	}

	public static IndexRoutingAllocationInclude of(Function<Builder, ObjectBuilder<IndexRoutingAllocationInclude>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code _tier_preference}
	 */
	@Nullable
	public final String tierPreference() {
		return this.tierPreference;
	}

	/**
	 * API name: {@code _id}
	 */
	@Nullable
	public final String id() {
		return this.id;
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

		if (this.tierPreference != null) {
			generator.writeKey("_tier_preference");
			generator.write(this.tierPreference);

		}
		if (this.id != null) {
			generator.writeKey("_id");
			generator.write(this.id);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndexRoutingAllocationInclude}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexRoutingAllocationInclude> {
		@Nullable
		private String tierPreference;

		@Nullable
		private String id;

		/**
		 * API name: {@code _tier_preference}
		 */
		public final Builder tierPreference(@Nullable String value) {
			this.tierPreference = value;
			return this;
		}

		/**
		 * API name: {@code _id}
		 */
		public final Builder id(@Nullable String value) {
			this.id = value;
			return this;
		}

		/**
		 * Builds a {@link IndexRoutingAllocationInclude}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndexRoutingAllocationInclude build() {
			_checkSingleUse();

			return new IndexRoutingAllocationInclude(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndexRoutingAllocationInclude}
	 */
	public static final JsonpDeserializer<IndexRoutingAllocationInclude> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, IndexRoutingAllocationInclude::setupIndexRoutingAllocationIncludeDeserializer);

	protected static void setupIndexRoutingAllocationIncludeDeserializer(
			ObjectDeserializer<IndexRoutingAllocationInclude.Builder> op) {

		op.add(Builder::tierPreference, JsonpDeserializer.stringDeserializer(), "_tier_preference");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "_id");

	}

}
