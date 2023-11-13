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

package org.opensearch.client.opensearch.indices.stats;

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
import java.util.Map;
import java.util.function.Function;

// typedef: indices.stats.ShardCommit


@JsonpDeserializable
public class ShardCommit implements JsonpSerializable {
	private final int generation;

	private final String id;

	private final long numDocs;

	private final Map<String, String> userData;

	// ---------------------------------------------------------------------------------------------

	private ShardCommit(Builder builder) {

		this.generation = ApiTypeHelper.requireNonNull(builder.generation, this, "generation");
		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.numDocs = ApiTypeHelper.requireNonNull(builder.numDocs, this, "numDocs");
		this.userData = ApiTypeHelper.unmodifiableRequired(builder.userData, this, "userData");

	}

	public static ShardCommit of(Function<Builder, ObjectBuilder<ShardCommit>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code generation}
	 */
	public final int generation() {
		return this.generation;
	}

	/**
	 * Required - API name: {@code id}
	 */
	public final String id() {
		return this.id;
	}

	/**
	 * Required - API name: {@code num_docs}
	 */
	public final long numDocs() {
		return this.numDocs;
	}

	/**
	 * Required - API name: {@code user_data}
	 */
	public final Map<String, String> userData() {
		return this.userData;
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

		generator.writeKey("generation");
		generator.write(this.generation);

		generator.writeKey("id");
		generator.write(this.id);

		generator.writeKey("num_docs");
		generator.write(this.numDocs);

		if (ApiTypeHelper.isDefined(this.userData)) {
			generator.writeKey("user_data");
			generator.writeStartObject();
			for (Map.Entry<String, String> item0 : this.userData.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ShardCommit}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ShardCommit> {
		private Integer generation;

		private String id;

		private Long numDocs;

		private Map<String, String> userData;

		/**
		 * Required - API name: {@code generation}
		 */
		public final Builder generation(int value) {
			this.generation = value;
			return this;
		}

		/**
		 * Required - API name: {@code id}
		 */
		public final Builder id(String value) {
			this.id = value;
			return this;
		}

		/**
		 * Required - API name: {@code num_docs}
		 */
		public final Builder numDocs(long value) {
			this.numDocs = value;
			return this;
		}

		/**
		 * Required - API name: {@code user_data}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>userData</code>.
		 */
		public final Builder userData(Map<String, String> map) {
			this.userData = _mapPutAll(this.userData, map);
			return this;
		}

		/**
		 * Required - API name: {@code user_data}
		 * <p>
		 * Adds an entry to <code>userData</code>.
		 */
		public final Builder userData(String key, String value) {
			this.userData = _mapPut(this.userData, key, value);
			return this;
		}

		/**
		 * Builds a {@link ShardCommit}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ShardCommit build() {
			_checkSingleUse();

			return new ShardCommit(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ShardCommit}
	 */
	public static final JsonpDeserializer<ShardCommit> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ShardCommit::setupShardCommitDeserializer);

	protected static void setupShardCommitDeserializer(ObjectDeserializer<ShardCommit.Builder> op) {

		op.add(Builder::generation, JsonpDeserializer.integerDeserializer(), "generation");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "id");
		op.add(Builder::numDocs, JsonpDeserializer.longDeserializer(), "num_docs");
		op.add(Builder::userData, JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.stringDeserializer()),
				"user_data");

	}

}
