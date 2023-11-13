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
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.search._types.Highlight

@JsonpDeserializable
public class Highlight implements JsonpSerializable {
	private final Map<String, HighlightField> fields;

	@Nullable
	private final HighlighterType type;

	@Nullable
	private final String boundaryChars;

	@Nullable
	private final Integer boundaryMaxScan;

	@Nullable
	private final BoundaryScanner boundaryScanner;

	@Nullable
	private final String boundaryScannerLocale;

	@Nullable
	private final HighlighterEncoder encoder;

	@Nullable
	private final HighlighterFragmenter fragmenter;

	@Nullable
	private final Integer fragmentOffset;

	@Nullable
	private final Integer fragmentSize;

	@Nullable
	private final Integer maxFragmentLength;

	@Nullable
	private final Integer noMatchSize;

	@Nullable
	private final Integer numberOfFragments;

	@Nullable
	private final HighlighterOrder order;

	private final List<String> postTags;

	private final List<String> preTags;

	@Nullable
	private final Boolean requireFieldMatch;

	@Nullable
	private final HighlighterTagsSchema tagsSchema;

	@Nullable
	private final Query highlightQuery;

	@Nullable
	private final String maxAnalyzedOffset;

	// ---------------------------------------------------------------------------------------------

	private Highlight(Builder builder) {

		this.fields = ApiTypeHelper.unmodifiableRequired(builder.fields, this, "fields");
		this.type = builder.type;
		this.boundaryChars = builder.boundaryChars;
		this.boundaryMaxScan = builder.boundaryMaxScan;
		this.boundaryScanner = builder.boundaryScanner;
		this.boundaryScannerLocale = builder.boundaryScannerLocale;
		this.encoder = builder.encoder;
		this.fragmenter = builder.fragmenter;
		this.fragmentOffset = builder.fragmentOffset;
		this.fragmentSize = builder.fragmentSize;
		this.maxFragmentLength = builder.maxFragmentLength;
		this.noMatchSize = builder.noMatchSize;
		this.numberOfFragments = builder.numberOfFragments;
		this.order = builder.order;
		this.postTags = ApiTypeHelper.unmodifiable(builder.postTags);
		this.preTags = ApiTypeHelper.unmodifiable(builder.preTags);
		this.requireFieldMatch = builder.requireFieldMatch;
		this.tagsSchema = builder.tagsSchema;
		this.highlightQuery = builder.highlightQuery;
		this.maxAnalyzedOffset = builder.maxAnalyzedOffset;

	}

	public static Highlight of(Function<Builder, ObjectBuilder<Highlight>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code fields}
	 */
	public final Map<String, HighlightField> fields() {
		return this.fields;
	}

	/**
	 * API name: {@code type}
	 */
	@Nullable
	public final HighlighterType type() {
		return this.type;
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
	 * API name: {@code encoder}
	 */
	@Nullable
	public final HighlighterEncoder encoder() {
		return this.encoder;
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
	 * API name: {@code highlight_query}
	 */
	@Nullable
	public final Query highlightQuery() {
		return this.highlightQuery;
	}

	/**
	 * API name: {@code max_analyzed_offset}
	 */
	@Nullable
	public final String maxAnalyzedOffset() {
		return this.maxAnalyzedOffset;
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

		if (ApiTypeHelper.isDefined(this.fields)) {
			generator.writeKey("fields");
			generator.writeStartObject();
			for (Map.Entry<String, HighlightField> item0 : this.fields.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.type != null) {
			generator.writeKey("type");
			this.type.serialize(generator, mapper);

		}
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
		if (this.encoder != null) {
			generator.writeKey("encoder");
			this.encoder.serialize(generator, mapper);
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
		if (this.highlightQuery != null) {
			generator.writeKey("highlight_query");
			this.highlightQuery.serialize(generator, mapper);

		}
		if (this.maxAnalyzedOffset != null) {
			generator.writeKey("max_analyzed_offset");
			generator.write(this.maxAnalyzedOffset);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Highlight}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Highlight> {
		private Map<String, HighlightField> fields;

		@Nullable
		private HighlighterType type;

		@Nullable
		private String boundaryChars;

		@Nullable
		private Integer boundaryMaxScan;

		@Nullable
		private BoundaryScanner boundaryScanner;

		@Nullable
		private String boundaryScannerLocale;

		@Nullable
		private HighlighterEncoder encoder;

		@Nullable
		private HighlighterFragmenter fragmenter;

		@Nullable
		private Integer fragmentOffset;

		@Nullable
		private Integer fragmentSize;

		@Nullable
		private Integer maxFragmentLength;

		@Nullable
		private Integer noMatchSize;

		@Nullable
		private Integer numberOfFragments;

		@Nullable
		private HighlighterOrder order;

		@Nullable
		private List<String> postTags;

		@Nullable
		private List<String> preTags;

		@Nullable
		private Boolean requireFieldMatch;

		@Nullable
		private HighlighterTagsSchema tagsSchema;

		@Nullable
		private Query highlightQuery;

		@Nullable
		private String maxAnalyzedOffset;

		/**
		 * Required - API name: {@code fields}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>fields</code>.
		 */
		public final Builder fields(Map<String, HighlightField> map) {
			this.fields = _mapPutAll(this.fields, map);
			return this;
		}

		/**
		 * Required - API name: {@code fields}
		 * <p>
		 * Adds an entry to <code>fields</code>.
		 */
		public final Builder fields(String key, HighlightField value) {
			this.fields = _mapPut(this.fields, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code fields}
		 * <p>
		 * Adds an entry to <code>fields</code> using a builder lambda.
		 */
		public final Builder fields(String key, Function<HighlightField.Builder, ObjectBuilder<HighlightField>> fn) {
			return fields(key, fn.apply(new HighlightField.Builder()).build());
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
		 * API name: {@code encoder}
		 */
		public final Builder encoder(@Nullable HighlighterEncoder value) {
			this.encoder = value;
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
		 * API name: {@code max_analyzed_offset}
		 */
		public final Builder maxAnalyzedOffset(@Nullable String value) {
			this.maxAnalyzedOffset = value;
			return this;
		}

		/**
		 * Builds a {@link Highlight}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Highlight build() {
			_checkSingleUse();

			return new Highlight(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Highlight}
	 */
	public static final JsonpDeserializer<Highlight> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Highlight::setupHighlightDeserializer);

	protected static void setupHighlightDeserializer(ObjectDeserializer<Highlight.Builder> op) {

		op.add(Builder::fields, JsonpDeserializer.stringMapDeserializer(HighlightField._DESERIALIZER), "fields");
		op.add(Builder::type, HighlighterType._DESERIALIZER, "type");
		op.add(Builder::boundaryChars, JsonpDeserializer.stringDeserializer(), "boundary_chars");
		op.add(Builder::boundaryMaxScan, JsonpDeserializer.integerDeserializer(), "boundary_max_scan");
		op.add(Builder::boundaryScanner, BoundaryScanner._DESERIALIZER, "boundary_scanner");
		op.add(Builder::boundaryScannerLocale, JsonpDeserializer.stringDeserializer(), "boundary_scanner_locale");
		op.add(Builder::encoder, HighlighterEncoder._DESERIALIZER, "encoder");
		op.add(Builder::fragmenter, HighlighterFragmenter._DESERIALIZER, "fragmenter");
		op.add(Builder::fragmentOffset, JsonpDeserializer.integerDeserializer(), "fragment_offset");
		op.add(Builder::fragmentSize, JsonpDeserializer.integerDeserializer(), "fragment_size");
		op.add(Builder::maxFragmentLength, JsonpDeserializer.integerDeserializer(), "max_fragment_length");
		op.add(Builder::noMatchSize, JsonpDeserializer.integerDeserializer(), "no_match_size");
		op.add(Builder::numberOfFragments, JsonpDeserializer.integerDeserializer(), "number_of_fragments");
		op.add(Builder::order, HighlighterOrder._DESERIALIZER, "order");
		op.add(Builder::postTags, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"post_tags");
		op.add(Builder::preTags, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"pre_tags");
		op.add(Builder::requireFieldMatch, JsonpDeserializer.booleanDeserializer(), "require_field_match");
		op.add(Builder::tagsSchema, HighlighterTagsSchema._DESERIALIZER, "tags_schema");
		op.add(Builder::highlightQuery, Query._DESERIALIZER, "highlight_query");
		op.add(Builder::maxAnalyzedOffset, JsonpDeserializer.stringDeserializer(), "max_analyzed_offset");

	}

}
