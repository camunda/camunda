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

import org.opensearch.client.json.JsonEnum;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.function.Function;

// typedef: _types.query_dsl.PinnedQuery


@JsonpDeserializable
public class PinnedQuery extends QueryBase
		implements
			TaggedUnion<PinnedQuery.Kind, Object>,
			QueryVariant,
			JsonpSerializable {

	/**
	 * {@link PinnedQuery} variant kinds.
	 */
	/**
	 * {@link PinnedQuery} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		Ids("ids"),

		Docs("docs"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

	}

	/**
	 * Query variant kind.
	 */
	@Override
	public Query.Kind _queryKind() {
		return Query.Kind.Pinned;
	}

	private final Kind _kind;
	private final Object _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final Object _get() {
		return _value;
	}

	private final Query organic;

	private PinnedQuery(Builder builder) {
		super(builder);

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

		this.organic = ApiTypeHelper.requireNonNull(builder.organic, this, "organic");

	}

	public static PinnedQuery of(Function<Builder, ObjectBuilder<PinnedQuery>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code organic}
	 */
	public final Query organic() {
		return this.organic;
	}

	/**
	 * Is this variant instance of kind {@code ids}?
	 */
	public boolean isIds() {
		return _kind == Kind.Ids;
	}

	/**
	 * Get the {@code ids} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code ids} kind.
	 */
	public List<String> ids() {
		return TaggedUnionUtils.get(this, Kind.Ids);
	}

	/**
	 * Is this variant instance of kind {@code docs}?
	 */
	public boolean isDocs() {
		return _kind == Kind.Docs;
	}

	/**
	 * Get the {@code docs} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code docs} kind.
	 */
	public List<PinnedDoc> docs() {
		return TaggedUnionUtils.get(this, Kind.Docs);
	}

	@Override
	@SuppressWarnings("unchecked")
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		generator.writeStartObject();

		super.serializeInternal(generator, mapper);
		generator.writeKey("organic");
		this.organic.serialize(generator, mapper);

		generator.writeKey(_kind.jsonValue());
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		} else {
			switch (_kind) {
				case Ids :
					generator.writeStartArray();
					for (String item0 : ((List<String>) this._value)) {
						generator.write(item0);

					}
					generator.writeEnd();

					break;
				case Docs :
					generator.writeStartArray();
					for (PinnedDoc item0 : ((List<PinnedDoc>) this._value)) {
						item0.serialize(generator, mapper);

					}
					generator.writeEnd();

					break;
			}
		}

		generator.writeEnd();

	}

	public static class Builder extends QueryBase.AbstractBuilder<Builder> {
		private Kind _kind;
		private Object _value;

		private Query organic;

		/**
		 * Required - API name: {@code organic}
		 */
		public final Builder organic(Query value) {
			this.organic = value;
			return this;
		}

		/**
		 * Required - API name: {@code organic}
		 */
		public final Builder organic(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return this.organic(fn.apply(new Query.Builder()).build());
		}

		@Override
		protected Builder self() {
			return this;
		}
		public ContainerBuilder ids(List<String> v) {
			this._kind = Kind.Ids;
			this._value = v;
			return new ContainerBuilder();
		}

		public ContainerBuilder docs(List<PinnedDoc> v) {
			this._kind = Kind.Docs;
			this._value = v;
			return new ContainerBuilder();
		}

		protected PinnedQuery build() {
			_checkSingleUse();
			return new PinnedQuery(this);
		}

		public class ContainerBuilder implements ObjectBuilder<PinnedQuery> {

			/**
			 * Required - API name: {@code organic}
			 */
			public final ContainerBuilder organic(Query value) {
				Builder.this.organic = value;
				return this;
			}

			/**
			 * Required - API name: {@code organic}
			 */
			public final ContainerBuilder organic(Function<Query.Builder, ObjectBuilder<Query>> fn) {
				return this.organic(fn.apply(new Query.Builder()).build());
			}

			public PinnedQuery build() {
				return Builder.this.build();
			}
		}
	}

	protected static void setupPinnedQueryDeserializer(ObjectDeserializer<Builder> op) {
		QueryBase.setupQueryBaseDeserializer(op);
		op.add(Builder::organic, Query._DESERIALIZER, "organic");
		op.add(Builder::ids, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "ids");
		op.add(Builder::docs, JsonpDeserializer.arrayDeserializer(PinnedDoc._DESERIALIZER), "docs");

	}

	public static final JsonpDeserializer<PinnedQuery> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			PinnedQuery::setupPinnedQueryDeserializer, Builder::build);
}
