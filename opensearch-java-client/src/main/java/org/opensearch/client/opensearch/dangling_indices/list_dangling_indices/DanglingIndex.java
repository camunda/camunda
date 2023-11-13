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

package org.opensearch.client.opensearch.dangling_indices.list_dangling_indices;

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

// typedef: dangling_indices.list_dangling_indices.DanglingIndex

@JsonpDeserializable
public class DanglingIndex implements JsonpSerializable {
	private final String indexName;

	private final String indexUuid;

	private final String creationDateMillis;

	private final List<String> nodeIds;

	// ---------------------------------------------------------------------------------------------

	private DanglingIndex(Builder builder) {

		this.indexName = ApiTypeHelper.requireNonNull(builder.indexName, this, "indexName");
		this.indexUuid = ApiTypeHelper.requireNonNull(builder.indexUuid, this, "indexUuid");
		this.creationDateMillis = ApiTypeHelper.requireNonNull(builder.creationDateMillis, this, "creationDateMillis");
		this.nodeIds = ApiTypeHelper.unmodifiableRequired(builder.nodeIds, this, "nodeIds");

	}

	public static DanglingIndex of(Function<Builder, ObjectBuilder<DanglingIndex>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code index_name}
	 */
	public final String indexName() {
		return this.indexName;
	}

	/**
	 * Required - API name: {@code index_uuid}
	 */
	public final String indexUuid() {
		return this.indexUuid;
	}

	/**
	 * Required - API name: {@code creation_date_millis}
	 */
	public final String creationDateMillis() {
		return this.creationDateMillis;
	}

	/**
	 * Required - API name: {@code node_ids}
	 */
	public final List<String> nodeIds() {
		return this.nodeIds;
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

		generator.writeKey("index_name");
		generator.write(this.indexName);

		generator.writeKey("index_uuid");
		generator.write(this.indexUuid);

		generator.writeKey("creation_date_millis");
		generator.write(this.creationDateMillis);

		if (ApiTypeHelper.isDefined(this.nodeIds)) {
			generator.writeKey("node_ids");
			generator.writeStartArray();
			for (String item0 : this.nodeIds) {
				generator.write(item0);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DanglingIndex}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DanglingIndex> {
		private String indexName;

		private String indexUuid;

		private String creationDateMillis;

		private List<String> nodeIds;

		/**
		 * Required - API name: {@code index_name}
		 */
		public final Builder indexName(String value) {
			this.indexName = value;
			return this;
		}

		/**
		 * Required - API name: {@code index_uuid}
		 */
		public final Builder indexUuid(String value) {
			this.indexUuid = value;
			return this;
		}

		/**
		 * Required - API name: {@code creation_date_millis}
		 */
		public final Builder creationDateMillis(String value) {
			this.creationDateMillis = value;
			return this;
		}

		/**
		 * Required - API name: {@code node_ids}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>nodeIds</code>.
		 */
		public final Builder nodeIds(List<String> list) {
			this.nodeIds = _listAddAll(this.nodeIds, list);
			return this;
		}

		/**
		 * Required - API name: {@code node_ids}
		 * <p>
		 * Adds one or more values to <code>nodeIds</code>.
		 */
		public final Builder nodeIds(String value, String... values) {
			this.nodeIds = _listAdd(this.nodeIds, value, values);
			return this;
		}

		/**
		 * Builds a {@link DanglingIndex}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DanglingIndex build() {
			_checkSingleUse();

			return new DanglingIndex(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link DanglingIndex}
	 */
	public static final JsonpDeserializer<DanglingIndex> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			DanglingIndex::setupDanglingIndexDeserializer);

	protected static void setupDanglingIndexDeserializer(ObjectDeserializer<DanglingIndex.Builder> op) {

		op.add(Builder::indexName, JsonpDeserializer.stringDeserializer(), "index_name");
		op.add(Builder::indexUuid, JsonpDeserializer.stringDeserializer(), "index_uuid");
		op.add(Builder::creationDateMillis, JsonpDeserializer.stringDeserializer(), "creation_date_millis");
		op.add(Builder::nodeIds, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"node_ids");

	}

}
