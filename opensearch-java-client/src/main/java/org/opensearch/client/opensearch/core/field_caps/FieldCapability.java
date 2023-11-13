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

package org.opensearch.client.opensearch.core.field_caps;

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
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.field_caps.FieldCapability


@JsonpDeserializable
public class FieldCapability implements JsonpSerializable {
	private final boolean aggregatable;

	private final List<String> indices;

	private final Map<String, List<String>> meta;

	private final List<String> nonAggregatableIndices;

	private final List<String> nonSearchableIndices;

	private final boolean searchable;

	private final String type;

	@Nullable
	private final Boolean metadataField;

	// ---------------------------------------------------------------------------------------------

	private FieldCapability(Builder builder) {

		this.aggregatable = ApiTypeHelper.requireNonNull(builder.aggregatable, this, "aggregatable");
		this.indices = ApiTypeHelper.unmodifiable(builder.indices);
		this.meta = ApiTypeHelper.unmodifiable(builder.meta);
		this.nonAggregatableIndices = ApiTypeHelper.unmodifiable(builder.nonAggregatableIndices);
		this.nonSearchableIndices = ApiTypeHelper.unmodifiable(builder.nonSearchableIndices);
		this.searchable = ApiTypeHelper.requireNonNull(builder.searchable, this, "searchable");
		this.type = ApiTypeHelper.requireNonNull(builder.type, this, "type");
		this.metadataField = builder.metadataField;

	}

	public static FieldCapability of(Function<Builder, ObjectBuilder<FieldCapability>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code aggregatable}
	 */
	public final boolean aggregatable() {
		return this.aggregatable;
	}

	/**
	 * API name: {@code indices}
	 */
	public final List<String> indices() {
		return this.indices;
	}

	/**
	 * API name: {@code meta}
	 */
	public final Map<String, List<String>> meta() {
		return this.meta;
	}

	/**
	 * API name: {@code non_aggregatable_indices}
	 */
	public final List<String> nonAggregatableIndices() {
		return this.nonAggregatableIndices;
	}

	/**
	 * API name: {@code non_searchable_indices}
	 */
	public final List<String> nonSearchableIndices() {
		return this.nonSearchableIndices;
	}

	/**
	 * Required - API name: {@code searchable}
	 */
	public final boolean searchable() {
		return this.searchable;
	}

	/**
	 * Required - API name: {@code type}
	 */
	public final String type() {
		return this.type;
	}

	/**
	 * API name: {@code metadata_field}
	 */
	@Nullable
	public final Boolean metadataField() {
		return this.metadataField;
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

		generator.writeKey("aggregatable");
		generator.write(this.aggregatable);

		if (ApiTypeHelper.isDefined(this.indices)) {
			generator.writeKey("indices");
			generator.writeStartArray();
			for (String item0 : this.indices) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.meta)) {
			generator.writeKey("meta");
			generator.writeStartObject();
			for (Map.Entry<String, List<String>> item0 : this.meta.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.writeStartArray();
				if (item0.getValue() != null) {
					for (String item1 : item0.getValue()) {
						generator.write(item1);

					}
				}
				generator.writeEnd();

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.nonAggregatableIndices)) {
			generator.writeKey("non_aggregatable_indices");
			generator.writeStartArray();
			for (String item0 : this.nonAggregatableIndices) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.nonSearchableIndices)) {
			generator.writeKey("non_searchable_indices");
			generator.writeStartArray();
			for (String item0 : this.nonSearchableIndices) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		generator.writeKey("searchable");
		generator.write(this.searchable);

		generator.writeKey("type");
		generator.write(this.type);

		if (this.metadataField != null) {
			generator.writeKey("metadata_field");
			generator.write(this.metadataField);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link FieldCapability}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<FieldCapability> {
		private Boolean aggregatable;

		@Nullable
		private List<String> indices;

		@Nullable
		private Map<String, List<String>> meta;

		@Nullable
		private List<String> nonAggregatableIndices;

		@Nullable
		private List<String> nonSearchableIndices;

		private Boolean searchable;

		private String type;

		@Nullable
		private Boolean metadataField;

		/**
		 * Required - API name: {@code aggregatable}
		 */
		public final Builder aggregatable(boolean value) {
			this.aggregatable = value;
			return this;
		}

		/**
		 * API name: {@code indices}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>indices</code>.
		 */
		public final Builder indices(List<String> list) {
			this.indices = _listAddAll(this.indices, list);
			return this;
		}

		/**
		 * API name: {@code indices}
		 * <p>
		 * Adds one or more values to <code>indices</code>.
		 */
		public final Builder indices(String value, String... values) {
			this.indices = _listAdd(this.indices, value, values);
			return this;
		}

		/**
		 * API name: {@code meta}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>meta</code>.
		 */
		public final Builder meta(Map<String, List<String>> map) {
			this.meta = _mapPutAll(this.meta, map);
			return this;
		}

		/**
		 * API name: {@code meta}
		 * <p>
		 * Adds an entry to <code>meta</code>.
		 */
		public final Builder meta(String key, List<String> value) {
			this.meta = _mapPut(this.meta, key, value);
			return this;
		}

		/**
		 * API name: {@code non_aggregatable_indices}
		 * <p>
		 * Adds all elements of <code>list</code> to
		 * <code>nonAggregatableIndices</code>.
		 */
		public final Builder nonAggregatableIndices(List<String> list) {
			this.nonAggregatableIndices = _listAddAll(this.nonAggregatableIndices, list);
			return this;
		}

		/**
		 * API name: {@code non_aggregatable_indices}
		 * <p>
		 * Adds one or more values to <code>nonAggregatableIndices</code>.
		 */
		public final Builder nonAggregatableIndices(String value, String... values) {
			this.nonAggregatableIndices = _listAdd(this.nonAggregatableIndices, value, values);
			return this;
		}

		/**
		 * API name: {@code non_searchable_indices}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>nonSearchableIndices</code>.
		 */
		public final Builder nonSearchableIndices(List<String> list) {
			this.nonSearchableIndices = _listAddAll(this.nonSearchableIndices, list);
			return this;
		}

		/**
		 * API name: {@code non_searchable_indices}
		 * <p>
		 * Adds one or more values to <code>nonSearchableIndices</code>.
		 */
		public final Builder nonSearchableIndices(String value, String... values) {
			this.nonSearchableIndices = _listAdd(this.nonSearchableIndices, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code searchable}
		 */
		public final Builder searchable(boolean value) {
			this.searchable = value;
			return this;
		}

		/**
		 * Required - API name: {@code type}
		 */
		public final Builder type(String value) {
			this.type = value;
			return this;
		}

		/**
		 * API name: {@code metadata_field}
		 */
		public final Builder metadataField(@Nullable Boolean value) {
			this.metadataField = value;
			return this;
		}

		/**
		 * Builds a {@link FieldCapability}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public FieldCapability build() {
			_checkSingleUse();

			return new FieldCapability(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link FieldCapability}
	 */
	public static final JsonpDeserializer<FieldCapability> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			FieldCapability::setupFieldCapabilityDeserializer);

	protected static void setupFieldCapabilityDeserializer(ObjectDeserializer<FieldCapability.Builder> op) {

		op.add(Builder::aggregatable, JsonpDeserializer.booleanDeserializer(), "aggregatable");
		op.add(Builder::indices, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"indices");
		op.add(Builder::meta, JsonpDeserializer.stringMapDeserializer(
				JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer())), "meta");
		op.add(Builder::nonAggregatableIndices,
				JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"non_aggregatable_indices");
		op.add(Builder::nonSearchableIndices,
				JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "non_searchable_indices");
		op.add(Builder::searchable, JsonpDeserializer.booleanDeserializer(), "searchable");
		op.add(Builder::type, JsonpDeserializer.stringDeserializer(), "type");
		op.add(Builder::metadataField, JsonpDeserializer.booleanDeserializer(), "metadata_field");

	}

}
