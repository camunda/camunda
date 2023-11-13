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

package org.opensearch.client.opensearch.core.mtermvectors;

import org.opensearch.client.opensearch._types.VersionType;
import org.opensearch.client.opensearch.core.termvectors.Filter;
import org.opensearch.client.json.JsonData;
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

// typedef: _global.mtermvectors.Operation

@JsonpDeserializable
public class MultiTermVectorsOperation implements JsonpSerializable {
	private final JsonData doc;

	private final List<String> fields;

	private final boolean fieldStatistics;

	private final Filter filter;

	private final String id;

	private final String index;

	private final boolean offsets;

	private final boolean payloads;

	private final boolean positions;

	private final String routing;

	private final boolean termStatistics;

	private final long version;

	private final VersionType versionType;

	// ---------------------------------------------------------------------------------------------

	private MultiTermVectorsOperation(Builder builder) {

		this.doc = ApiTypeHelper.requireNonNull(builder.doc, this, "doc");
		this.fields = ApiTypeHelper.unmodifiableRequired(builder.fields, this, "fields");
		this.fieldStatistics = ApiTypeHelper.requireNonNull(builder.fieldStatistics, this, "fieldStatistics");
		this.filter = ApiTypeHelper.requireNonNull(builder.filter, this, "filter");
		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.offsets = ApiTypeHelper.requireNonNull(builder.offsets, this, "offsets");
		this.payloads = ApiTypeHelper.requireNonNull(builder.payloads, this, "payloads");
		this.positions = ApiTypeHelper.requireNonNull(builder.positions, this, "positions");
		this.routing = ApiTypeHelper.requireNonNull(builder.routing, this, "routing");
		this.termStatistics = ApiTypeHelper.requireNonNull(builder.termStatistics, this, "termStatistics");
		this.version = ApiTypeHelper.requireNonNull(builder.version, this, "version");
		this.versionType = ApiTypeHelper.requireNonNull(builder.versionType, this, "versionType");

	}

	public static MultiTermVectorsOperation of(Function<Builder, ObjectBuilder<MultiTermVectorsOperation>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code doc}
	 */
	public final JsonData doc() {
		return this.doc;
	}

	/**
	 * Required - API name: {@code fields}
	 */
	public final List<String> fields() {
		return this.fields;
	}

	/**
	 * Required - API name: {@code field_statistics}
	 */
	public final boolean fieldStatistics() {
		return this.fieldStatistics;
	}

	/**
	 * Required - API name: {@code filter}
	 */
	public final Filter filter() {
		return this.filter;
	}

	/**
	 * Required - API name: {@code _id}
	 */
	public final String id() {
		return this.id;
	}

	/**
	 * Required - API name: {@code _index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * Required - API name: {@code offsets}
	 */
	public final boolean offsets() {
		return this.offsets;
	}

	/**
	 * Required - API name: {@code payloads}
	 */
	public final boolean payloads() {
		return this.payloads;
	}

	/**
	 * Required - API name: {@code positions}
	 */
	public final boolean positions() {
		return this.positions;
	}

	/**
	 * Required - API name: {@code routing}
	 */
	public final String routing() {
		return this.routing;
	}

	/**
	 * Required - API name: {@code term_statistics}
	 */
	public final boolean termStatistics() {
		return this.termStatistics;
	}

	/**
	 * Required - API name: {@code version}
	 */
	public final long version() {
		return this.version;
	}

	/**
	 * Required - API name: {@code version_type}
	 */
	public final VersionType versionType() {
		return this.versionType;
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

		generator.writeKey("doc");
		this.doc.serialize(generator, mapper);

		if (ApiTypeHelper.isDefined(this.fields)) {
			generator.writeKey("fields");
			generator.writeStartArray();
			for (String item0 : this.fields) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		generator.writeKey("field_statistics");
		generator.write(this.fieldStatistics);

		generator.writeKey("filter");
		this.filter.serialize(generator, mapper);

		generator.writeKey("_id");
		generator.write(this.id);

		generator.writeKey("_index");
		generator.write(this.index);

		generator.writeKey("offsets");
		generator.write(this.offsets);

		generator.writeKey("payloads");
		generator.write(this.payloads);

		generator.writeKey("positions");
		generator.write(this.positions);

		generator.writeKey("routing");
		generator.write(this.routing);

		generator.writeKey("term_statistics");
		generator.write(this.termStatistics);

		generator.writeKey("version");
		generator.write(this.version);

		generator.writeKey("version_type");
		this.versionType.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link MultiTermVectorsOperation}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<MultiTermVectorsOperation> {
		private JsonData doc;

		private List<String> fields;

		private Boolean fieldStatistics;

		private Filter filter;

		private String id;

		private String index;

		private Boolean offsets;

		private Boolean payloads;

		private Boolean positions;

		private String routing;

		private Boolean termStatistics;

		private Long version;

		private VersionType versionType;

		/**
		 * Required - API name: {@code doc}
		 */
		public final Builder doc(JsonData value) {
			this.doc = value;
			return this;
		}

		/**
		 * Required - API name: {@code fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>fields</code>.
		 */
		public final Builder fields(List<String> list) {
			this.fields = _listAddAll(this.fields, list);
			return this;
		}

		/**
		 * Required - API name: {@code fields}
		 * <p>
		 * Adds one or more values to <code>fields</code>.
		 */
		public final Builder fields(String value, String... values) {
			this.fields = _listAdd(this.fields, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code field_statistics}
		 */
		public final Builder fieldStatistics(boolean value) {
			this.fieldStatistics = value;
			return this;
		}

		/**
		 * Required - API name: {@code filter}
		 */
		public final Builder filter(Filter value) {
			this.filter = value;
			return this;
		}

		/**
		 * Required - API name: {@code filter}
		 */
		public final Builder filter(Function<Filter.Builder, ObjectBuilder<Filter>> fn) {
			return this.filter(fn.apply(new Filter.Builder()).build());
		}

		/**
		 * Required - API name: {@code _id}
		 */
		public final Builder id(String value) {
			this.id = value;
			return this;
		}

		/**
		 * Required - API name: {@code _index}
		 */
		public final Builder index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * Required - API name: {@code offsets}
		 */
		public final Builder offsets(boolean value) {
			this.offsets = value;
			return this;
		}

		/**
		 * Required - API name: {@code payloads}
		 */
		public final Builder payloads(boolean value) {
			this.payloads = value;
			return this;
		}

		/**
		 * Required - API name: {@code positions}
		 */
		public final Builder positions(boolean value) {
			this.positions = value;
			return this;
		}

		/**
		 * Required - API name: {@code routing}
		 */
		public final Builder routing(String value) {
			this.routing = value;
			return this;
		}

		/**
		 * Required - API name: {@code term_statistics}
		 */
		public final Builder termStatistics(boolean value) {
			this.termStatistics = value;
			return this;
		}

		/**
		 * Required - API name: {@code version}
		 */
		public final Builder version(long value) {
			this.version = value;
			return this;
		}

		/**
		 * Required - API name: {@code version_type}
		 */
		public final Builder versionType(VersionType value) {
			this.versionType = value;
			return this;
		}

		/**
		 * Builds a {@link MultiTermVectorsOperation}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public MultiTermVectorsOperation build() {
			_checkSingleUse();

			return new MultiTermVectorsOperation(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link MultiTermVectorsOperation}
	 */
	public static final JsonpDeserializer<MultiTermVectorsOperation> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, MultiTermVectorsOperation::setupMultiTermVectorsOperationDeserializer);

	protected static void setupMultiTermVectorsOperationDeserializer(
			ObjectDeserializer<MultiTermVectorsOperation.Builder> op) {

		op.add(Builder::doc, JsonData._DESERIALIZER, "doc");
		op.add(Builder::fields, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "fields");
		op.add(Builder::fieldStatistics, JsonpDeserializer.booleanDeserializer(), "field_statistics");
		op.add(Builder::filter, Filter._DESERIALIZER, "filter");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "_id");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "_index");
		op.add(Builder::offsets, JsonpDeserializer.booleanDeserializer(), "offsets");
		op.add(Builder::payloads, JsonpDeserializer.booleanDeserializer(), "payloads");
		op.add(Builder::positions, JsonpDeserializer.booleanDeserializer(), "positions");
		op.add(Builder::routing, JsonpDeserializer.stringDeserializer(), "routing");
		op.add(Builder::termStatistics, JsonpDeserializer.booleanDeserializer(), "term_statistics");
		op.add(Builder::version, JsonpDeserializer.longDeserializer(), "version");
		op.add(Builder::versionType, VersionType._DESERIALIZER, "version_type");

	}

}
