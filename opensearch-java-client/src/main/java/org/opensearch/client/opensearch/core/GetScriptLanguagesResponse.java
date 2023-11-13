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

package org.opensearch.client.opensearch.core;

import org.opensearch.client.opensearch.core.get_script_languages.LanguageContext;
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

// typedef: _global.get_script_languages.Response

@JsonpDeserializable
public class GetScriptLanguagesResponse implements JsonpSerializable {
	private final List<LanguageContext> languageContexts;

	private final List<String> typesAllowed;

	// ---------------------------------------------------------------------------------------------

	private GetScriptLanguagesResponse(Builder builder) {

		this.languageContexts = ApiTypeHelper.unmodifiableRequired(builder.languageContexts, this, "languageContexts");
		this.typesAllowed = ApiTypeHelper.unmodifiableRequired(builder.typesAllowed, this, "typesAllowed");

	}

	public static GetScriptLanguagesResponse of(Function<Builder, ObjectBuilder<GetScriptLanguagesResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code language_contexts}
	 */
	public final List<LanguageContext> languageContexts() {
		return this.languageContexts;
	}

	/**
	 * Required - API name: {@code types_allowed}
	 */
	public final List<String> typesAllowed() {
		return this.typesAllowed;
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

		if (ApiTypeHelper.isDefined(this.languageContexts)) {
			generator.writeKey("language_contexts");
			generator.writeStartArray();
			for (LanguageContext item0 : this.languageContexts) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.typesAllowed)) {
			generator.writeKey("types_allowed");
			generator.writeStartArray();
			for (String item0 : this.typesAllowed) {
				generator.write(item0);

			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GetScriptLanguagesResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GetScriptLanguagesResponse> {
		private List<LanguageContext> languageContexts;

		private List<String> typesAllowed;

		/**
		 * Required - API name: {@code language_contexts}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>languageContexts</code>.
		 */
		public final Builder languageContexts(List<LanguageContext> list) {
			this.languageContexts = _listAddAll(this.languageContexts, list);
			return this;
		}

		/**
		 * Required - API name: {@code language_contexts}
		 * <p>
		 * Adds one or more values to <code>languageContexts</code>.
		 */
		public final Builder languageContexts(LanguageContext value, LanguageContext... values) {
			this.languageContexts = _listAdd(this.languageContexts, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code language_contexts}
		 * <p>
		 * Adds a value to <code>languageContexts</code> using a builder lambda.
		 */
		public final Builder languageContexts(Function<LanguageContext.Builder, ObjectBuilder<LanguageContext>> fn) {
			return languageContexts(fn.apply(new LanguageContext.Builder()).build());
		}

		/**
		 * Required - API name: {@code types_allowed}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>typesAllowed</code>.
		 */
		public final Builder typesAllowed(List<String> list) {
			this.typesAllowed = _listAddAll(this.typesAllowed, list);
			return this;
		}

		/**
		 * Required - API name: {@code types_allowed}
		 * <p>
		 * Adds one or more values to <code>typesAllowed</code>.
		 */
		public final Builder typesAllowed(String value, String... values) {
			this.typesAllowed = _listAdd(this.typesAllowed, value, values);
			return this;
		}

		/**
		 * Builds a {@link GetScriptLanguagesResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GetScriptLanguagesResponse build() {
			_checkSingleUse();

			return new GetScriptLanguagesResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GetScriptLanguagesResponse}
	 */
	public static final JsonpDeserializer<GetScriptLanguagesResponse> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, GetScriptLanguagesResponse::setupGetScriptLanguagesResponseDeserializer);

	protected static void setupGetScriptLanguagesResponseDeserializer(
			ObjectDeserializer<GetScriptLanguagesResponse.Builder> op) {

		op.add(Builder::languageContexts, JsonpDeserializer.arrayDeserializer(LanguageContext._DESERIALIZER),
				"language_contexts");
		op.add(Builder::typesAllowed, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"types_allowed");

	}

}
