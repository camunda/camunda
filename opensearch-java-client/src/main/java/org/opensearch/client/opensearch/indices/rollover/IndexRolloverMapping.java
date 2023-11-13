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

package org.opensearch.client.opensearch.indices.rollover;

import org.opensearch.client.opensearch._types.mapping.TypeMapping;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.UnionDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;
import java.util.Map;
import java.util.function.Function;

// typedef: indices.rollover.IndexRolloverMapping


@JsonpDeserializable
public class IndexRolloverMapping implements TaggedUnion<IndexRolloverMapping.Kind, Object>, JsonpSerializable {

	public enum Kind {
		ByType, Single

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

	private IndexRolloverMapping(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	private IndexRolloverMapping(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static IndexRolloverMapping of(Function<Builder, ObjectBuilder<IndexRolloverMapping>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code by_type}?
	 */
	public boolean isByType() {
		return _kind == Kind.ByType;
	}

	/**
	 * Get the {@code by_type} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code by_type} kind.
	 */
	public Map<String, TypeMapping> byType() {
		return TaggedUnionUtils.get(this, Kind.ByType);
	}

	/**
	 * Is this variant instance of kind {@code single}?
	 */
	public boolean isSingle() {
		return _kind == Kind.Single;
	}

	/**
	 * Get the {@code single} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code single} kind.
	 */
	public TypeMapping single() {
		return TaggedUnionUtils.get(this, Kind.Single);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		} else {
			switch (_kind) {
				case ByType :
					generator.writeStartObject();
					for (Map.Entry<String, TypeMapping> item0 : ((Map<String, TypeMapping>) this._value).entrySet()) {
						generator.writeKey(item0.getKey());
						item0.getValue().serialize(generator, mapper);

					}
					generator.writeEnd();

					break;
			}
		}

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexRolloverMapping> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<IndexRolloverMapping> byType(Map<String, TypeMapping> v) {
			this._kind = Kind.ByType;
			this._value = v;
			return this;
		}

		public ObjectBuilder<IndexRolloverMapping> single(TypeMapping v) {
			this._kind = Kind.Single;
			this._value = v;
			return this;
		}

		public ObjectBuilder<IndexRolloverMapping> single(
				Function<TypeMapping.Builder, ObjectBuilder<TypeMapping>> fn) {
			return this.single(fn.apply(new TypeMapping.Builder()).build());
		}

		public IndexRolloverMapping build() {
			_checkSingleUse();
			return new IndexRolloverMapping(this);
		}

	}

	private static JsonpDeserializer<IndexRolloverMapping> buildIndexRolloverMappingDeserializer() {
		return new UnionDeserializer.Builder<IndexRolloverMapping, Kind, Object>(IndexRolloverMapping::new, false)
				.addMember(Kind.ByType, JsonpDeserializer.stringMapDeserializer(TypeMapping._DESERIALIZER))
				.addMember(Kind.Single, TypeMapping._DESERIALIZER).build();
	}

	public static final JsonpDeserializer<IndexRolloverMapping> _DESERIALIZER = JsonpDeserializer
			.lazy(IndexRolloverMapping::buildIndexRolloverMappingDeserializer);
}
