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

package org.opensearch.client.opensearch._types.query_dsl;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.query_dsl.PercolateQuery


@JsonpDeserializable
public class PercolateQuery extends QueryBase implements QueryVariant {
	@Nullable
	private final JsonData document;

	private final List<JsonData> documents;

	private final String field;

	@Nullable
	private final String id;

	@Nullable
	private final String index;

	@Nullable
	private final String name;

	@Nullable
	private final String preference;

	@Nullable
	private final String routing;

	@Nullable
	private final Long version;

	// ---------------------------------------------------------------------------------------------

	private PercolateQuery(Builder builder) {
		super(builder);

		this.document = builder.document;
		this.documents = ApiTypeHelper.unmodifiable(builder.documents);
		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");
		this.id = builder.id;
		this.index = builder.index;
		this.name = builder.name;
		this.preference = builder.preference;
		this.routing = builder.routing;
		this.version = builder.version;

	}

	public static PercolateQuery of(Function<Builder, ObjectBuilder<PercolateQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.Percolate;
	}

	/**
	 * API name: {@code document}
	 */
	@Nullable
	public final JsonData document() {
		return this.document;
	}

	/**
	 * API name: {@code documents}
	 */
	public final List<JsonData> documents() {
		return this.documents;
	}

	/**
	 * Required - API name: {@code field}
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code id}
	 */
	@Nullable
	public final String id() {
		return this.id;
	}

	/**
	 * API name: {@code index}
	 */
	@Nullable
	public final String index() {
		return this.index;
	}

	/**
	 * API name: {@code name}
	 */
	@Nullable
	public final String name() {
		return this.name;
	}

	/**
	 * API name: {@code preference}
	 */
	@Nullable
	public final String preference() {
		return this.preference;
	}

	/**
	 * API name: {@code routing}
	 */
	@Nullable
	public final String routing() {
		return this.routing;
	}

	/**
	 * API name: {@code version}
	 */
	@Nullable
	public final Long version() {
		return this.version;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (this.document != null) {
			generator.writeKey("document");
			this.document.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.documents)) {
			generator.writeKey("documents");
			generator.writeStartArray();
			for (JsonData item0 : this.documents) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("field");
		generator.write(this.field);

		if (this.id != null) {
			generator.writeKey("id");
			generator.write(this.id);

		}
		if (this.index != null) {
			generator.writeKey("index");
			generator.write(this.index);

		}
		if (this.name != null) {
			generator.writeKey("name");
			generator.write(this.name);

		}
		if (this.preference != null) {
			generator.writeKey("preference");
			generator.write(this.preference);

		}
		if (this.routing != null) {
			generator.writeKey("routing");
			generator.write(this.routing);

		}
		if (this.version != null) {
			generator.writeKey("version");
			generator.write(this.version);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link PercolateQuery}.
	 */

	public static class Builder extends QueryBase.AbstractBuilder<Builder> implements ObjectBuilder<PercolateQuery> {
		@Nullable
		private JsonData document;

		@Nullable
		private List<JsonData> documents;

		private String field;

		@Nullable
		private String id;

		@Nullable
		private String index;

		@Nullable
		private String name;

		@Nullable
		private String preference;

		@Nullable
		private String routing;

		@Nullable
		private Long version;

		/**
		 * API name: {@code document}
		 */
		public final Builder document(@Nullable JsonData value) {
			this.document = value;
			return this;
		}

		/**
		 * API name: {@code documents}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>documents</code>.
		 */
		public final Builder documents(List<JsonData> list) {
			this.documents = _listAddAll(this.documents, list);
			return this;
		}

		/**
		 * API name: {@code documents}
		 * <p>
		 * Adds one or more values to <code>documents</code>.
		 */
		public final Builder documents(JsonData value, JsonData... values) {
			this.documents = _listAdd(this.documents, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code field}
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		/**
		 * API name: {@code id}
		 */
		public final Builder id(@Nullable String value) {
			this.id = value;
			return this;
		}

		/**
		 * API name: {@code index}
		 */
		public final Builder index(@Nullable String value) {
			this.index = value;
			return this;
		}

		/**
		 * API name: {@code name}
		 */
		public final Builder name(@Nullable String value) {
			this.name = value;
			return this;
		}

		/**
		 * API name: {@code preference}
		 */
		public final Builder preference(@Nullable String value) {
			this.preference = value;
			return this;
		}

		/**
		 * API name: {@code routing}
		 */
		public final Builder routing(@Nullable String value) {
			this.routing = value;
			return this;
		}

		/**
		 * API name: {@code version}
		 */
		public final Builder version(@Nullable Long value) {
			this.version = value;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds a {@link PercolateQuery}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public PercolateQuery build() {
			_checkSingleUse();

			return new PercolateQuery(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link PercolateQuery}
	 */
	public static final JsonpDeserializer<PercolateQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			PercolateQuery::setupPercolateQueryDeserializer);

	protected static void setupPercolateQueryDeserializer(ObjectDeserializer<PercolateQuery.Builder> op) {
		QueryBase.setupQueryBaseDeserializer(op);
		op.add(Builder::document, JsonData._DESERIALIZER, "document");
		op.add(Builder::documents, JsonpDeserializer.arrayDeserializer(JsonData._DESERIALIZER), "documents");
		op.add(Builder::field, JsonpDeserializer.stringDeserializer(), "field");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "id");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index");
		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
		op.add(Builder::preference, JsonpDeserializer.stringDeserializer(), "preference");
		op.add(Builder::routing, JsonpDeserializer.stringDeserializer(), "routing");
		op.add(Builder::version, JsonpDeserializer.longDeserializer(), "version");

	}

}
