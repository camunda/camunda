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
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.search._types.Suggester


@JsonpDeserializable
public class Suggester implements JsonpSerializable {
	private final Map<String, FieldSuggester> suggesters;

	@Nullable
	private final String text;

	// ---------------------------------------------------------------------------------------------

	private Suggester(Builder builder) {

		this.suggesters = ApiTypeHelper.unmodifiable(builder.suggesters);

		this.text = builder.text;

	}

	public static Suggester of(Function<Builder, ObjectBuilder<Suggester>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * The named suggesters
	 */
	public final Map<String, FieldSuggester> suggesters() {
		return this.suggesters;
	}

	/**
	 * Global suggest text, to avoid repetition when the same text is used in
	 * several suggesters
	 * <p>
	 * API name: {@code text}
	 */
	@Nullable
	public final String text() {
		return this.text;
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

		for (Map.Entry<String, FieldSuggester> item0 : this.suggesters.entrySet()) {
			generator.writeKey(item0.getKey());
			item0.getValue().serialize(generator, mapper);

		}

		if (this.text != null) {
			generator.writeKey("text");
			generator.write(this.text);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Suggester}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Suggester> {
		@Nullable
		private Map<String, FieldSuggester> suggesters = new HashMap<>();

		/**
		 * The named suggesters
		 * <p>
		 * Adds all entries of <code>map</code> to <code>suggesters</code>.
		 */
		public final Builder suggesters(Map<String, FieldSuggester> map) {
			this.suggesters = _mapPutAll(this.suggesters, map);
			return this;
		}

		/**
		 * The named suggesters
		 * <p>
		 * Adds an entry to <code>suggesters</code>.
		 */
		public final Builder suggesters(String key, FieldSuggester value) {
			this.suggesters = _mapPut(this.suggesters, key, value);
			return this;
		}

		/**
		 * The named suggesters
		 * <p>
		 * Adds an entry to <code>suggesters</code> using a builder lambda.
		 */
		public final Builder suggesters(String key,
				Function<FieldSuggester.Builder, ObjectBuilder<FieldSuggester>> fn) {
			return suggesters(key, fn.apply(new FieldSuggester.Builder()).build());
		}

		@Nullable
		private String text;

		/**
		 * Global suggest text, to avoid repetition when the same text is used in
		 * several suggesters
		 * <p>
		 * API name: {@code text}
		 */
		public final Builder text(@Nullable String value) {
			this.text = value;
			return this;
		}

		/**
		 * Builds a {@link Suggester}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Suggester build() {
			_checkSingleUse();

			return new Suggester(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Suggester}
	 */
	public static final JsonpDeserializer<Suggester> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Suggester::setupSuggesterDeserializer);

	protected static void setupSuggesterDeserializer(ObjectDeserializer<Suggester.Builder> op) {

		op.add(Builder::text, JsonpDeserializer.stringDeserializer(), "text");

		op.setUnknownFieldHandler((builder, name, parser, mapper) -> {
			if (builder.suggesters == null) {
				builder.suggesters = new HashMap<>();
			}
			builder.suggesters.put(name, FieldSuggester._DESERIALIZER.deserialize(parser, mapper));
		});

	}

}
