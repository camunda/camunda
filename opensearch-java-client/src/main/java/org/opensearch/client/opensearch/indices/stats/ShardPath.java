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
import java.util.function.Function;

// typedef: indices.stats.ShardPath


@JsonpDeserializable
public class ShardPath implements JsonpSerializable {
	private final String dataPath;

	private final boolean isCustomDataPath;

	private final String statePath;

	// ---------------------------------------------------------------------------------------------

	private ShardPath(Builder builder) {

		this.dataPath = ApiTypeHelper.requireNonNull(builder.dataPath, this, "dataPath");
		this.isCustomDataPath = ApiTypeHelper.requireNonNull(builder.isCustomDataPath, this, "isCustomDataPath");
		this.statePath = ApiTypeHelper.requireNonNull(builder.statePath, this, "statePath");

	}

	public static ShardPath of(Function<Builder, ObjectBuilder<ShardPath>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code data_path}
	 */
	public final String dataPath() {
		return this.dataPath;
	}

	/**
	 * Required - API name: {@code is_custom_data_path}
	 */
	public final boolean isCustomDataPath() {
		return this.isCustomDataPath;
	}

	/**
	 * Required - API name: {@code state_path}
	 */
	public final String statePath() {
		return this.statePath;
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

		generator.writeKey("data_path");
		generator.write(this.dataPath);

		generator.writeKey("is_custom_data_path");
		generator.write(this.isCustomDataPath);

		generator.writeKey("state_path");
		generator.write(this.statePath);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ShardPath}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ShardPath> {
		private String dataPath;

		private Boolean isCustomDataPath;

		private String statePath;

		/**
		 * Required - API name: {@code data_path}
		 */
		public final Builder dataPath(String value) {
			this.dataPath = value;
			return this;
		}

		/**
		 * Required - API name: {@code is_custom_data_path}
		 */
		public final Builder isCustomDataPath(boolean value) {
			this.isCustomDataPath = value;
			return this;
		}

		/**
		 * Required - API name: {@code state_path}
		 */
		public final Builder statePath(String value) {
			this.statePath = value;
			return this;
		}

		/**
		 * Builds a {@link ShardPath}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ShardPath build() {
			_checkSingleUse();

			return new ShardPath(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ShardPath}
	 */
	public static final JsonpDeserializer<ShardPath> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ShardPath::setupShardPathDeserializer);

	protected static void setupShardPathDeserializer(ObjectDeserializer<ShardPath.Builder> op) {

		op.add(Builder::dataPath, JsonpDeserializer.stringDeserializer(), "data_path");
		op.add(Builder::isCustomDataPath, JsonpDeserializer.booleanDeserializer(), "is_custom_data_path");
		op.add(Builder::statePath, JsonpDeserializer.stringDeserializer(), "state_path");

	}

}
