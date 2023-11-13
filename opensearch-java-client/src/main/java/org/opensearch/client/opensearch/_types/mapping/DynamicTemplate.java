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

package org.opensearch.client.opensearch._types.mapping;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _types.mapping.DynamicTemplate


@JsonpDeserializable
public class DynamicTemplate implements JsonpSerializable {
	@Nullable
	private final Property mapping;

	@Nullable
	private final String match;

	@Nullable
	private final String matchMappingType;

	@Nullable
	private final MatchType matchPattern;

	@Nullable
	private final String pathMatch;

	@Nullable
	private final String pathUnmatch;

	@Nullable
	private final String unmatch;

	// ---------------------------------------------------------------------------------------------

	private DynamicTemplate(Builder builder) {

		this.mapping = builder.mapping;
		this.match = builder.match;
		this.matchMappingType = builder.matchMappingType;
		this.matchPattern = builder.matchPattern;
		this.pathMatch = builder.pathMatch;
		this.pathUnmatch = builder.pathUnmatch;
		this.unmatch = builder.unmatch;

	}

	public static DynamicTemplate of(Function<Builder, ObjectBuilder<DynamicTemplate>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code mapping}
	 */
	@Nullable
	public final Property mapping() {
		return this.mapping;
	}

	/**
	 * API name: {@code match}
	 */
	@Nullable
	public final String match() {
		return this.match;
	}

	/**
	 * API name: {@code match_mapping_type}
	 */
	@Nullable
	public final String matchMappingType() {
		return this.matchMappingType;
	}

	/**
	 * API name: {@code match_pattern}
	 */
	@Nullable
	public final MatchType matchPattern() {
		return this.matchPattern;
	}

	/**
	 * API name: {@code path_match}
	 */
	@Nullable
	public final String pathMatch() {
		return this.pathMatch;
	}

	/**
	 * API name: {@code path_unmatch}
	 */
	@Nullable
	public final String pathUnmatch() {
		return this.pathUnmatch;
	}

	/**
	 * API name: {@code unmatch}
	 */
	@Nullable
	public final String unmatch() {
		return this.unmatch;
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

		if (this.mapping != null) {
			generator.writeKey("mapping");
			this.mapping.serialize(generator, mapper);

		}
		if (this.match != null) {
			generator.writeKey("match");
			generator.write(this.match);

		}
		if (this.matchMappingType != null) {
			generator.writeKey("match_mapping_type");
			generator.write(this.matchMappingType);

		}
		if (this.matchPattern != null) {
			generator.writeKey("match_pattern");
			this.matchPattern.serialize(generator, mapper);
		}
		if (this.pathMatch != null) {
			generator.writeKey("path_match");
			generator.write(this.pathMatch);

		}
		if (this.pathUnmatch != null) {
			generator.writeKey("path_unmatch");
			generator.write(this.pathUnmatch);

		}
		if (this.unmatch != null) {
			generator.writeKey("unmatch");
			generator.write(this.unmatch);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DynamicTemplate}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DynamicTemplate> {
		@Nullable
		private Property mapping;

		@Nullable
		private String match;

		@Nullable
		private String matchMappingType;

		@Nullable
		private MatchType matchPattern;

		@Nullable
		private String pathMatch;

		@Nullable
		private String pathUnmatch;

		@Nullable
		private String unmatch;

		/**
		 * API name: {@code mapping}
		 */
		public final Builder mapping(@Nullable Property value) {
			this.mapping = value;
			return this;
		}

		/**
		 * API name: {@code mapping}
		 */
		public final Builder mapping(Function<Property.Builder, ObjectBuilder<Property>> fn) {
			return this.mapping(fn.apply(new Property.Builder()).build());
		}

		/**
		 * API name: {@code match}
		 */
		public final Builder match(@Nullable String value) {
			this.match = value;
			return this;
		}

		/**
		 * API name: {@code match_mapping_type}
		 */
		public final Builder matchMappingType(@Nullable String value) {
			this.matchMappingType = value;
			return this;
		}

		/**
		 * API name: {@code match_pattern}
		 */
		public final Builder matchPattern(@Nullable MatchType value) {
			this.matchPattern = value;
			return this;
		}

		/**
		 * API name: {@code path_match}
		 */
		public final Builder pathMatch(@Nullable String value) {
			this.pathMatch = value;
			return this;
		}

		/**
		 * API name: {@code path_unmatch}
		 */
		public final Builder pathUnmatch(@Nullable String value) {
			this.pathUnmatch = value;
			return this;
		}

		/**
		 * API name: {@code unmatch}
		 */
		public final Builder unmatch(@Nullable String value) {
			this.unmatch = value;
			return this;
		}

		/**
		 * Builds a {@link DynamicTemplate}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DynamicTemplate build() {
			_checkSingleUse();

			return new DynamicTemplate(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link DynamicTemplate}
	 */
	public static final JsonpDeserializer<DynamicTemplate> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			DynamicTemplate::setupDynamicTemplateDeserializer);

	protected static void setupDynamicTemplateDeserializer(ObjectDeserializer<DynamicTemplate.Builder> op) {

		op.add(Builder::mapping, Property._DESERIALIZER, "mapping");
		op.add(Builder::match, JsonpDeserializer.stringDeserializer(), "match");
		op.add(Builder::matchMappingType, JsonpDeserializer.stringDeserializer(), "match_mapping_type");
		op.add(Builder::matchPattern, MatchType._DESERIALIZER, "match_pattern");
		op.add(Builder::pathMatch, JsonpDeserializer.stringDeserializer(), "path_match");
		op.add(Builder::pathUnmatch, JsonpDeserializer.stringDeserializer(), "path_unmatch");
		op.add(Builder::unmatch, JsonpDeserializer.stringDeserializer(), "unmatch");

	}

}
