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

package org.opensearch.client.opensearch.core.search;

import org.opensearch.client.opensearch._types.query_dsl.Query;
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
import javax.annotation.Nullable;

// typedef: _global.search._types.HighlightField

@JsonpDeserializable
public class HighlightField implements JsonpSerializable {
	@Nullable
	private final String boundaryChars;

	@Nullable
	private final Integer boundaryMaxScan;

	@Nullable
	private final BoundaryScanner boundaryScanner;

	@Nullable
	private final String boundaryScannerLocale;

	@Nullable
	private final String field;

	@Nullable
	private final Boolean forceSource;

	@Nullable
	private final HighlighterFragmenter fragmenter;

	@Nullable
	private final Integer fragmentOffset;

	@Nullable
	private final Integer fragmentSize;

	@Nullable
	private final Query highlightQuery;

	private final List<String> matchedFields;

	@Nullable
	private final Integer maxFragmentLength;

	@Nullable
	private final Integer noMatchSize;

	@Nullable
	private final Integer numberOfFragments;

	@Nullable
	private final HighlighterOrder order;

	@Nullable
	private final Integer phraseLimit;

	private final List<String> postTags;

	private final List<String> preTags;

	@Nullable
	private final Boolean requireFieldMatch;

	@Nullable
	private final HighlighterTagsSchema tagsSchema;

	@Nullable
	private final HighlighterType type;

	// ---------------------------------------------------------------------------------------------

	private HighlightField(Builder builder) {

		this.boundaryChars = builder.boundaryChars;
		this.boundaryMaxScan = builder.boundaryMaxScan;
		this.boundaryScanner = builder.boundaryScanner;
		this.boundaryScannerLocale = builder.boundaryScannerLocale;
		this.field = builder.field;
		this.forceSource = builder.forceSource;
		this.fragmenter = builder.fragmenter;
		this.fragmentOffset = builder.fragmentOffset;
		this.fragmentSize = builder.fragmentSize;
		this.highlightQuery = builder.highlightQuery;
		this.matchedFields = ApiTypeHelper.unmodifiable(builder.matchedFields);
		this.maxFragmentLength = builder.maxFragmentLength;
		this.noMatchSize = builder.noMatchSize;
		this.numberOfFragments = builder.numberOfFragments;
		this.order = builder.order;
		this.phraseLimit = builder.phraseLimit;
		this.postTags = ApiTypeHelper.unmodifiable(builder.postTags);
		this.preTags = ApiTypeHelper.unmodifiable(builder.preTags);
		this.requireFieldMatch = builder.requireFieldMatch;
		this.tagsSchema = builder.tagsSchema;
		this.type = builder.type;

	}

	public static HighlightField of(Function<Builder, ObjectBuilder<HighlightField>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code boundary_chars}
	 */
	@Nullable
	public final String boundaryChars() {
		return this.boundaryChars;
	}

	/**
	 * API name: {@code boundary_max_scan}
	 */
	@Nullable
	public final Integer boundaryMaxScan() {
		return this.boundaryMaxScan;
	}

	/**
	 * API name: {@code boundary_scanner}
	 */
	@Nullable
	public final BoundaryScanner boundaryScanner() {
		return this.boundaryScanner;
	}

	/**
	 * API name: {@code boundary_scanner_locale}
	 */
	@Nullable
	public final String boundaryScannerLocale() {
		return this.boundaryScannerLocale;
	}

	/**
	 * API name: {@code field}
	 */
	@Nullable
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code force_source}
	 */
	@Nullable
	public final Boolean forceSource() {
		return this.forceSource;
	}

	/**
	 * API name: {@code fragmenter}
	 */
	@Nullable
	public final HighlighterFragmenter fragmenter() {
		return this.fragmenter;
	}

	/**
	 * API name: {@code fragment_offset}
	 */
	@Nullable
	public final Integer fragmentOffset() {
		return this.fragmentOffset;
	}

	/**
	 * API name: {@code fragment_size}
	 */
	@Nullable
	public final Integer fragmentSize() {
		return this.fragmentSize;
	}

	/**
	 * API name: {@code highlight_query}
	 */
	@Nullable
	public final Query highlightQuery() {
		return this.highlightQuery;
	}

	/**
	 * API name: {@code matched_fields}
	 */
	public final List<String> matchedFields() {
		return this.matchedFields;
	}

	/**
	 * API name: {@code max_fragment_length}
	 */
	@Nullable
	public final Integer maxFragmentLength() {
		return this.maxFragmentLength;
	}

	/**
	 * API name: {@code no_match_size}
	 */
	@Nullable
	public final Integer noMatchSize() {
		return this.noMatchSize;
	}

	/**
	 * API name: {@code number_of_fragments}
	 */
	@Nullable
	public final Integer numberOfFragments() {
		return this.numberOfFragments;
	}

	/**
	 * API name: {@code order}
	 */
	@Nullable
	public final HighlighterOrder order() {
		return this.order;
	}

	/**
	 * API name: {@code phrase_limit}
	 */
	@Nullable
	public final Integer phraseLimit() {
		return this.phraseLimit;
	}

	/**
	 * API name: {@code post_tags}
	 */
	public final List<String> postTags() {
		return this.postTags;
	}

	/**
	 * API name: {@code pre_tags}
	 */
	public final List<String> preTags() {
		return this.preTags;
	}

	/**
	 * API name: {@code require_field_match}
	 */
	@Nullable
	public final Boolean requireFieldMatch() {
		return this.requireFieldMatch;
	}

	/**
	 * API name: {@code tags_schema}
	 */
	@Nullable
	public final HighlighterTagsSchema tagsSchema() {
		return this.tagsSchema;
	}

	/**
	 * API name: {@code type}
	 */
	@Nullable
	public final HighlighterType type() {
		return this.type;
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

		if (this.boundaryChars != null) {
			generator.writeKey("boundary_chars");
			generator.write(this.boundaryChars);

		}
		if (this.boundaryMaxScan != null) {
			generator.writeKey("boundary_max_scan");
			generator.write(this.boundaryMaxScan);

		}
		if (this.boundaryScanner != null) {
			generator.writeKey("boundary_scanner");
			this.boundaryScanner.serialize(generator, mapper);
		}
		if (this.boundaryScannerLocale != null) {
			generator.writeKey("boundary_scanner_locale");
			generator.write(this.boundaryScannerLocale);

		}
		if (this.field != null) {
			generator.writeKey("field");
			generator.write(this.field);

		}
		if (this.forceSource != null) {
			generator.writeKey("force_source");
			generator.write(this.forceSource);

		}
		if (this.fragmenter != null) {
			generator.writeKey("fragmenter");
			this.fragmenter.serialize(generator, mapper);
		}
		if (this.fragmentOffset != null) {
			generator.writeKey("fragment_offset");
			generator.write(this.fragmentOffset);

		}
		if (this.fragmentSize != null) {
			generator.writeKey("fragment_size");
			generator.write(this.fragmentSize);

		}
		if (this.highlightQuery != null) {
			generator.writeKey("highlight_query");
			this.highlightQuery.serialize(generator, mapper);

		}
		if (ApiTypeHelper.isDefined(this.matchedFields)) {
			generator.writeKey("matched_fields");
			generator.writeStartArray();
			for (String item0 : this.matchedFields) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.maxFragmentLength != null) {
			generator.writeKey("max_fragment_length");
			generator.write(this.maxFragmentLength);

		}
		if (this.noMatchSize != null) {
			generator.writeKey("no_match_size");
			generator.write(this.noMatchSize);

		}
		if (this.numberOfFragments != null) {
			generator.writeKey("number_of_fragments");
			generator.write(this.numberOfFragments);

		}
		if (this.order != null) {
			generator.writeKey("order");
			this.order.serialize(generator, mapper);
		}
		if (this.phraseLimit != null) {
			generator.writeKey("phrase_limit");
			generator.write(this.phraseLimit);

		}
		if (ApiTypeHelper.isDefined(this.postTags)) {
			generator.writeKey("post_tags");
			generator.writeStartArray();
			for (String item0 : this.postTags) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.preTags)) {
			generator.writeKey("pre_tags");
			generator.writeStartArray();
			for (String item0 : this.preTags) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (this.requireFieldMatch != null) {
			generator.writeKey("require_field_match");
			generator.write(this.requireFieldMatch);

		}
		if (this.tagsSchema != null) {
			generator.writeKey("tags_schema");
			this.tagsSchema.serialize(generator, mapper);
		}
		if (this.type != null) {
			generator.writeKey("type");
			this.type.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HighlightField}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<HighlightField> {
		@Nullable
		private String boundaryChars;

		@Nullable
		private Integer boundaryMaxScan;

		@Nullable
		private BoundaryScanner boundaryScanner;

		@Nullable
		private String boundaryScannerLocale;

		@Nullable
		private String field;

		@Nullable
		private Boolean forceSource;

		@Nullable
		private HighlighterFragmenter fragmenter;

		@Nullable
		private Integer fragmentOffset;

		@Nullable
		private Integer fragmentSize;

		@Nullable
		private Query highlightQuery;

		@Nullable
		private List<String> matchedFields;

		@Nullable
		private Integer maxFragmentLength;

		@Nullable
		private Integer noMatchSize;

		@Nullable
		private Integer numberOfFragments;

		@Nullable
		private HighlighterOrder order;

		@Nullable
		private Integer phraseLimit;

		@Nullable
		private List<String> postTags;

		@Nullable
		private List<String> preTags;

		@Nullable
		private Boolean requireFieldMatch;

		@Nullable
		private HighlighterTagsSchema tagsSchema;

		@Nullable
		private HighlighterType type;

		/**
		 * API name: {@code boundary_chars}
		 */
		public final Builder boundaryChars(@Nullable String value) {
			this.boundaryChars = value;
			return this;
		}

		/**
		 * API name: {@code boundary_max_scan}
		 */
		public final Builder boundaryMaxScan(@Nullable Integer value) {
			this.boundaryMaxScan = value;
			return this;
		}

		/**
		 * API name: {@code boundary_scanner}
		 */
		public final Builder boundaryScanner(@Nullable BoundaryScanner value) {
			this.boundaryScanner = value;
			return this;
		}

		/**
		 * API name: {@code boundary_scanner_locale}
		 */
		public final Builder boundaryScannerLocale(@Nullable String value) {
			this.boundaryScannerLocale = value;
			return this;
		}

		/**
		 * API name: {@code field}
		 */
		public final Builder field(@Nullable String value) {
			this.field = value;
			return this;
		}

		/**
		 * API name: {@code force_source}
		 */
		public final Builder forceSource(@Nullable Boolean value) {
			this.forceSource = value;
			return this;
		}

		/**
		 * API name: {@code fragmenter}
		 */
		public final Builder fragmenter(@Nullable HighlighterFragmenter value) {
			this.fragmenter = value;
			return this;
		}

		/**
		 * API name: {@code fragment_offset}
		 */
		public final Builder fragmentOffset(@Nullable Integer value) {
			this.fragmentOffset = value;
			return this;
		}

		/**
		 * API name: {@code fragment_size}
		 */
		public final Builder fragmentSize(@Nullable Integer value) {
			this.fragmentSize = value;
			return this;
		}

		/**
		 * API name: {@code highlight_query}
		 */
		public final Builder highlightQuery(@Nullable Query value) {
			this.highlightQuery = value;
			return this;
		}

		/**
		 * API name: {@code highlight_query}
		 */
		public final Builder highlightQuery(Function<Query.Builder, ObjectBuilder<Query>> fn) {
			return this.highlightQuery(fn.apply(new Query.Builder()).build());
		}

		/**
		 * API name: {@code matched_fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>matchedFields</code>.
		 */
		public final Builder matchedFields(List<String> list) {
			this.matchedFields = _listAddAll(this.matchedFields, list);
			return this;
		}

		/**
		 * API name: {@code matched_fields}
		 * <p>
		 * Adds one or more values to <code>matchedFields</code>.
		 */
		public final Builder matchedFields(String value, String... values) {
			this.matchedFields = _listAdd(this.matchedFields, value, values);
			return this;
		}

		/**
		 * API name: {@code max_fragment_length}
		 */
		public final Builder maxFragmentLength(@Nullable Integer value) {
			this.maxFragmentLength = value;
			return this;
		}

		/**
		 * API name: {@code no_match_size}
		 */
		public final Builder noMatchSize(@Nullable Integer value) {
			this.noMatchSize = value;
			return this;
		}

		/**
		 * API name: {@code number_of_fragments}
		 */
		public final Builder numberOfFragments(@Nullable Integer value) {
			this.numberOfFragments = value;
			return this;
		}

		/**
		 * API name: {@code order}
		 */
		public final Builder order(@Nullable HighlighterOrder value) {
			this.order = value;
			return this;
		}

		/**
		 * API name: {@code phrase_limit}
		 */
		public final Builder phraseLimit(@Nullable Integer value) {
			this.phraseLimit = value;
			return this;
		}

		/**
		 * API name: {@code post_tags}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>postTags</code>.
		 */
		public final Builder postTags(List<String> list) {
			this.postTags = _listAddAll(this.postTags, list);
			return this;
		}

		/**
		 * API name: {@code post_tags}
		 * <p>
		 * Adds one or more values to <code>postTags</code>.
		 */
		public final Builder postTags(String value, String... values) {
			this.postTags = _listAdd(this.postTags, value, values);
			return this;
		}

		/**
		 * API name: {@code pre_tags}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>preTags</code>.
		 */
		public final Builder preTags(List<String> list) {
			this.preTags = _listAddAll(this.preTags, list);
			return this;
		}

		/**
		 * API name: {@code pre_tags}
		 * <p>
		 * Adds one or more values to <code>preTags</code>.
		 */
		public final Builder preTags(String value, String... values) {
			this.preTags = _listAdd(this.preTags, value, values);
			return this;
		}

		/**
		 * API name: {@code require_field_match}
		 */
		public final Builder requireFieldMatch(@Nullable Boolean value) {
			this.requireFieldMatch = value;
			return this;
		}

		/**
		 * API name: {@code tags_schema}
		 */
		public final Builder tagsSchema(@Nullable HighlighterTagsSchema value) {
			this.tagsSchema = value;
			return this;
		}

		/**
		 * API name: {@code type}
		 */
		public final Builder type(@Nullable HighlighterType value) {
			this.type = value;
			return this;
		}

		/**
		 * API name: {@code type}
		 */
		public final Builder type(Function<HighlighterType.Builder, ObjectBuilder<HighlighterType>> fn) {
			return this.type(fn.apply(new HighlighterType.Builder()).build());
		}

		/**
		 * Builds a {@link HighlightField}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HighlightField build() {
			_checkSingleUse();

			return new HighlightField(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HighlightField}
	 */
	public static final JsonpDeserializer<HighlightField> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			HighlightField::setupHighlightFieldDeserializer);

	protected static void setupHighlightFieldDeserializer(ObjectDeserializer<HighlightField.Builder> op) {

		op.add(Builder::boundaryChars, JsonpDeserializer.stringDeserializer(), "boundary_chars");
		op.add(Builder::boundaryMaxScan, JsonpDeserializer.integerDeserializer(), "boundary_max_scan");
		op.add(Builder::boundaryScanner, BoundaryScanner._DESERIALIZER, "boundary_scanner");
		op.add(Builder::boundaryScannerLocale, JsonpDeserializer.stringDeserializer(), "boundary_scanner_locale");
		op.add(Builder::field, JsonpDeserializer.stringDeserializer(), "field");
		op.add(Builder::forceSource, JsonpDeserializer.booleanDeserializer(), "force_source");
		op.add(Builder::fragmenter, HighlighterFragmenter._DESERIALIZER, "fragmenter");
		op.add(Builder::fragmentOffset, JsonpDeserializer.integerDeserializer(), "fragment_offset");
		op.add(Builder::fragmentSize, JsonpDeserializer.integerDeserializer(), "fragment_size");
		op.add(Builder::highlightQuery, Query._DESERIALIZER, "highlight_query");
		op.add(Builder::matchedFields, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"matched_fields");
		op.add(Builder::maxFragmentLength, JsonpDeserializer.integerDeserializer(), "max_fragment_length");
		op.add(Builder::noMatchSize, JsonpDeserializer.integerDeserializer(), "no_match_size");
		op.add(Builder::numberOfFragments, JsonpDeserializer.integerDeserializer(), "number_of_fragments");
		op.add(Builder::order, HighlighterOrder._DESERIALIZER, "order");
		op.add(Builder::phraseLimit, JsonpDeserializer.integerDeserializer(), "phrase_limit");
		op.add(Builder::postTags, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"post_tags");
		op.add(Builder::preTags, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"pre_tags");
		op.add(Builder::requireFieldMatch, JsonpDeserializer.booleanDeserializer(), "require_field_match");
		op.add(Builder::tagsSchema, HighlighterTagsSchema._DESERIALIZER, "tags_schema");
		op.add(Builder::type, HighlighterType._DESERIALIZER, "type");

	}

}
