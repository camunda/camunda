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

// typedef: _types.analysis.CharFilterDefinition


@JsonpDeserializable
public class CharFilterDefinition
		implements
			TaggedUnion<CharFilterDefinition.Kind, CharFilterDefinitionVariant>,
			JsonpSerializable {

	/**
	 * {@link CharFilterDefinition} variant kinds.
	 */
	/**
	 * {@link CharFilterDefinition} variant kinds.
	 */

	public enum Kind implements JsonEnum {
		HtmlStrip("html_strip"),

		IcuNormalizer("icu_normalizer"),

		KuromojiIterationMark("kuromoji_iteration_mark"),

		Mapping("mapping"),

		PatternReplace("pattern_replace"),

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
	private final CharFilterDefinitionVariant _value;

	@Override
	public final Kind _kind() {
		return _kind;
	}

	@Override
	public final CharFilterDefinitionVariant _get() {
		return _value;
	}

	public CharFilterDefinition(CharFilterDefinitionVariant value) {

		this._kind = ApiTypeHelper.requireNonNull(value._charFilterDefinitionKind(), this, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(value, this, "<variant value>");

	}

	private CharFilterDefinition(Builder builder) {

		this._kind = ApiTypeHelper.requireNonNull(builder._kind, builder, "<variant kind>");
		this._value = ApiTypeHelper.requireNonNull(builder._value, builder, "<variant value>");

	}

	public static CharFilterDefinition of(Function<Builder, ObjectBuilder<CharFilterDefinition>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Is this variant instance of kind {@code html_strip}?
	 */
	public boolean isHtmlStrip() {
		return _kind == Kind.HtmlStrip;
	}

	/**
	 * Get the {@code html_strip} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code html_strip} kind.
	 */
	public HtmlStripCharFilter htmlStrip() {
		return TaggedUnionUtils.get(this, Kind.HtmlStrip);
	}

	/**
	 * Is this variant instance of kind {@code icu_normalizer}?
	 */
	public boolean isIcuNormalizer() {
		return _kind == Kind.IcuNormalizer;
	}

	/**
	 * Get the {@code icu_normalizer} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code icu_normalizer} kind.
	 */
	public IcuNormalizationCharFilter icuNormalizer() {
		return TaggedUnionUtils.get(this, Kind.IcuNormalizer);
	}

	/**
	 * Is this variant instance of kind {@code kuromoji_iteration_mark}?
	 */
	public boolean isKuromojiIterationMark() {
		return _kind == Kind.KuromojiIterationMark;
	}

	/**
	 * Get the {@code kuromoji_iteration_mark} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the
	 *             {@code kuromoji_iteration_mark} kind.
	 */
	public KuromojiIterationMarkCharFilter kuromojiIterationMark() {
		return TaggedUnionUtils.get(this, Kind.KuromojiIterationMark);
	}

	/**
	 * Is this variant instance of kind {@code mapping}?
	 */
	public boolean isMapping() {
		return _kind == Kind.Mapping;
	}

	/**
	 * Get the {@code mapping} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code mapping} kind.
	 */
	public MappingCharFilter mapping() {
		return TaggedUnionUtils.get(this, Kind.Mapping);
	}

	/**
	 * Is this variant instance of kind {@code pattern_replace}?
	 */
	public boolean isPatternReplace() {
		return _kind == Kind.PatternReplace;
	}

	/**
	 * Get the {@code pattern_replace} variant value.
	 *
	 * @throws IllegalStateException
	 *             if the current variant is not of the {@code pattern_replace}
	 *             kind.
	 */
	public PatternReplaceCharFilter patternReplace() {
		return TaggedUnionUtils.get(this, Kind.PatternReplace);
	}

	@Override
	public void serialize(JsonGenerator generator, JsonpMapper mapper) {

		mapper.serialize(_value, generator);

	}

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<CharFilterDefinition> {
		private Kind _kind;
		private CharFilterDefinitionVariant _value;

		public ObjectBuilder<CharFilterDefinition> htmlStrip(HtmlStripCharFilter v) {
			this._kind = Kind.HtmlStrip;
			this._value = v;
			return this;
		}

		public ObjectBuilder<CharFilterDefinition> htmlStrip(
				Function<HtmlStripCharFilter.Builder, ObjectBuilder<HtmlStripCharFilter>> fn) {
			return this.htmlStrip(fn.apply(new HtmlStripCharFilter.Builder()).build());
		}

		public ObjectBuilder<CharFilterDefinition> icuNormalizer(IcuNormalizationCharFilter v) {
			this._kind = Kind.IcuNormalizer;
			this._value = v;
			return this;
		}

		public ObjectBuilder<CharFilterDefinition> icuNormalizer(
				Function<IcuNormalizationCharFilter.Builder, ObjectBuilder<IcuNormalizationCharFilter>> fn) {
			return this.icuNormalizer(fn.apply(new IcuNormalizationCharFilter.Builder()).build());
		}

		public ObjectBuilder<CharFilterDefinition> kuromojiIterationMark(KuromojiIterationMarkCharFilter v) {
			this._kind = Kind.KuromojiIterationMark;
			this._value = v;
			return this;
		}

		public ObjectBuilder<CharFilterDefinition> kuromojiIterationMark(
				Function<KuromojiIterationMarkCharFilter.Builder, ObjectBuilder<KuromojiIterationMarkCharFilter>> fn) {
			return this.kuromojiIterationMark(fn.apply(new KuromojiIterationMarkCharFilter.Builder()).build());
		}

		public ObjectBuilder<CharFilterDefinition> mapping(MappingCharFilter v) {
			this._kind = Kind.Mapping;
			this._value = v;
			return this;
		}

		public ObjectBuilder<CharFilterDefinition> mapping(
				Function<MappingCharFilter.Builder, ObjectBuilder<MappingCharFilter>> fn) {
			return this.mapping(fn.apply(new MappingCharFilter.Builder()).build());
		}

		public ObjectBuilder<CharFilterDefinition> patternReplace(PatternReplaceCharFilter v) {
			this._kind = Kind.PatternReplace;
			this._value = v;
			return this;
		}

		public ObjectBuilder<CharFilterDefinition> patternReplace(
				Function<PatternReplaceCharFilter.Builder, ObjectBuilder<PatternReplaceCharFilter>> fn) {
			return this.patternReplace(fn.apply(new PatternReplaceCharFilter.Builder()).build());
		}

		public CharFilterDefinition build() {
			_checkSingleUse();
			return new CharFilterDefinition(this);
		}

	}

	protected static void setupCharFilterDefinitionDeserializer(ObjectDeserializer<Builder> op) {

		op.add(Builder::htmlStrip, HtmlStripCharFilter._DESERIALIZER, "html_strip");
		op.add(Builder::icuNormalizer, IcuNormalizationCharFilter._DESERIALIZER, "icu_normalizer");
		op.add(Builder::kuromojiIterationMark, KuromojiIterationMarkCharFilter._DESERIALIZER,
				"kuromoji_iteration_mark");
		op.add(Builder::mapping, MappingCharFilter._DESERIALIZER, "mapping");
		op.add(Builder::patternReplace, PatternReplaceCharFilter._DESERIALIZER, "pattern_replace");

		op.setTypeProperty("type", null);

	}

	public static final JsonpDeserializer<CharFilterDefinition> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, CharFilterDefinition::setupCharFilterDefinitionDeserializer, Builder::build);
}
