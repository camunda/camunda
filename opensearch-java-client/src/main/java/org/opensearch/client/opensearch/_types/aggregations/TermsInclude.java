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

package org.opensearch.client.opensearch._types.aggregations;

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
import java.util.List;
import java.util.function.Function;

// typedef: _types.aggregations.TermsInclude

@JsonpDeserializable
public class TermsInclude implements TaggedUnion<TermsInclude.Kind, Object>, JsonpSerializable {

	public enum Kind {
		Terms, Partition, Regexp

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

	private TermsInclude(Kind kind, Object value) {
		this._kind = kind;
		this._value = value;
	}

	private TermsInclude(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static TermsInclude of(Function<Builder, ObjectBuilder<TermsInclude>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code terms}?
	 */
	public boolean isTerms() {
		return _kind == Kind.Terms;
	}

	/**
	 * Get the {@code terms} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code terms} kind.
	 */
	public List<String> terms() {
		return TaggedUnionUtils.get(this, Kind.Terms);
	}

	/**
	 * Is this variant instance of kind {@code partition}?
	 */
	public boolean isPartition() {
		return _kind == Kind.Partition;
	}

	/**
	 * Get the {@code partition} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code partition} kind.
	 */
	public TermsPartition partition() {
		return TaggedUnionUtils.get(this, Kind.Partition);
	}

	/**
	 * Is this variant instance of kind {@code regexp}?
	 */
	public boolean isRegexp() {
		return _kind == Kind.Regexp;
	}

	/**
	 * Get the {@code regexp} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code regexp} kind.
	 */
	public String regexp() {
		return TaggedUnionUtils.get(this, Kind.Regexp);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {
		if (_value instanceof JsonpSerializable) {
			((JsonpSerializable) _value).serialize(generator, mapper);
		} else {
			switch (_kind) {
				case Terms :
					generator.writeStartArray();
					for (String item0 : ((List<String>) this._value)) {
						generator.write(item0);

					}
					generator.writeEnd();

					break;
				case Regexp :
					generator.write(((String) this._value));

					break;
			}
		}

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<TermsInclude> {
		private Kind _kind;
		private Object _value;

		public ObjectBuilder<TermsInclude> terms(List<String> v) {
			this._kind = Kind.Terms;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TermsInclude> partition(TermsPartition v) {
			this._kind = Kind.Partition;
			this._value = v;
			return this;
		}

		public ObjectBuilder<TermsInclude> partition(
				Function<TermsPartition.Builder, ObjectBuilder<TermsPartition>> fn) {
			return this.partition(fn.apply(new TermsPartition.Builder()).build());
		}

		public ObjectBuilder<TermsInclude> regexp(String v) {
			this._kind = Kind.Regexp;
			this._value = v;
			return this;
		}

		public TermsInclude build() {
			_checkSingleUse();
			return new TermsInclude(this);
		}

	}

	private static JsonpDeserializer<TermsInclude> buildTermsIncludeDeserializer() {
		return new UnionDeserializer.Builder<TermsInclude, Kind, Object>(TermsInclude::new, false)
				.addMember(Kind.Terms, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()))
				.addMember(Kind.Partition, TermsPartition._DESERIALIZER)
				.addMember(Kind.Regexp, JsonpDeserializer.stringDeserializer()).build();
	}

	public static final JsonpDeserializer<TermsInclude> _DESERIALIZER = JsonpDeserializer
			.lazy(TermsInclude::buildTermsIncludeDeserializer);
}
