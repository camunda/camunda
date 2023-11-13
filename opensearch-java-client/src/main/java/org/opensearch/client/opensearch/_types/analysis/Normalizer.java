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

package org.opensearch.client.opensearch._types.analysis;

import org.opensearch.client.json.JsonEnum;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import org.opensearch.client.util.TaggedUnion;
import org.opensearch.client.util.TaggedUnionUtils;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;

// typedef: _types.analysis.Normalizer

@JsonpDeserializable
public class Normalizer implements TaggedUnion<Normalizer.Kind, NormalizerVariant>, JsonpSerializable {

	/**
	 * {@link Normalizer} variant kinds.
	 */
	/**
	 * {@link Normalizer} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		Custom("custom"),

		Lowercase("lowercase"),

		;

		private final String jsonValue;

		Kind(String jsonValue) {
			this.jsonValue = jsonValue;
		}

		public String jsonValue() {
			return this.jsonValue;
		}

	}

	private final Kind _kind;
	private final NormalizerVariant _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final NormalizerVariant _get() {
		return _value;
	}

	public Normalizer(NormalizerVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._normalizerKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

	}

	private Normalizer(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static Normalizer of(Function<Builder, ObjectBuilder<Normalizer>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code custom}?
	 */
	public boolean isCustom() {
		return _kind == Kind.Custom;
	}

	/**
	 * Get the {@code custom} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code custom} kind.
	 */
	public CustomNormalizer custom() {
		return TaggedUnionUtils.get(this, Kind.Custom);
	}

	/**
	 * Is this variant instance of kind {@code lowercase}?
	 */
	public boolean isLowercase() {
		return _kind == Kind.Lowercase;
	}

	/**
	 * Get the {@code lowercase} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code lowercase} kind.
	 */
	public LowercaseNormalizer lowercase() {
		return TaggedUnionUtils.get(this, Kind.Lowercase);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		mapper.serialize(_value, generator);

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Normalizer> {
		private Kind _kind;
		private NormalizerVariant _value;

		public ObjectBuilder<Normalizer> custom(CustomNormalizer v) {
			this._kind = Kind.Custom;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Normalizer> custom(
				Function<CustomNormalizer.Builder, ObjectBuilder<CustomNormalizer>> fn) {
			return this.custom(fn.apply(new CustomNormalizer.Builder()).build());
		}

		public ObjectBuilder<Normalizer> lowercase(LowercaseNormalizer v) {
			this._kind = Kind.Lowercase;
			this._value = v;
			return this;
		}

		public ObjectBuilder<Normalizer> lowercase(
				Function<LowercaseNormalizer.Builder, ObjectBuilder<LowercaseNormalizer>> fn) {
			return this.lowercase(fn.apply(new LowercaseNormalizer.Builder()).build());
		}

		public Normalizer build() {
			_checkSingleUse();
			return new Normalizer(this);
		}

	}

	protected static void setupNormalizerDeserializer(ObjectDeserializer<Builder> op) {

		op.add(Builder::custom, CustomNormalizer._DESERIALIZER, "custom");
		op.add(Builder::lowercase, LowercaseNormalizer._DESERIALIZER, "lowercase");

		op.setTypeProperty("type", null);

	}

	public static final JsonpDeserializer<Normalizer> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Normalizer::setupNormalizerDeserializer, Builder::build);
}
