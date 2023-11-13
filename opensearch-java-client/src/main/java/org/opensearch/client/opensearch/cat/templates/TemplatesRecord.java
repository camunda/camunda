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

package org.opensearch.client.opensearch.cat.templates;

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

// typedef: cat.templates.TemplatesRecord


@JsonpDeserializable
public class TemplatesRecord implements JsonpSerializable {
	@Nullable
	private final String name;

	@Nullable
	private final String indexPatterns;

	@Nullable
	private final String order;

	@Nullable
	private final String version;

	@Nullable
	private final String composedOf;

	// ---------------------------------------------------------------------------------------------

	private TemplatesRecord(Builder builder) {

		this.name = builder.name;
		this.indexPatterns = builder.indexPatterns;
		this.order = builder.order;
		this.version = builder.version;
		this.composedOf = builder.composedOf;

	}

	public static TemplatesRecord of(Function<Builder, ObjectBuilder<TemplatesRecord>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * template name
	 * <p>
	 * API name: {@code name}
	 */
	@Nullable
	public final String name() {
		return this.name;
	}

	/**
	 * template index patterns
	 * <p>
	 * API name: {@code index_patterns}
	 */
	@Nullable
	public final String indexPatterns() {
		return this.indexPatterns;
	}

	/**
	 * template application order/priority number
	 * <p>
	 * API name: {@code order}
	 */
	@Nullable
	public final String order() {
		return this.order;
	}

	/**
	 * version
	 * <p>
	 * API name: {@code version}
	 */
	@Nullable
	public final String version() {
		return this.version;
	}

	/**
	 * component templates comprising index template
	 * <p>
	 * API name: {@code composed_of}
	 */
	@Nullable
	public final String composedOf() {
		return this.composedOf;
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

		if (this.name != null) {
			generator.writeKey("name");
			generator.write(this.name);

		}
		if (this.indexPatterns != null) {
			generator.writeKey("index_patterns");
			generator.write(this.indexPatterns);

		}
		if (this.order != null) {
			generator.writeKey("order");
			generator.write(this.order);

		}
		if (this.version != null) {
			generator.writeKey("version");
			generator.write(this.version);

		}
		if (this.composedOf != null) {
			generator.writeKey("composed_of");
			generator.write(this.composedOf);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link TemplatesRecord}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<TemplatesRecord> {
		@Nullable
		private String name;

		@Nullable
		private String indexPatterns;

		@Nullable
		private String order;

		@Nullable
		private String version;

		@Nullable
		private String composedOf;

		/**
		 * template name
		 * <p>
		 * API name: {@code name}
		 */
		public final Builder name(@Nullable String value) {
			this.name = value;
			return this;
		}

		/**
		 * template index patterns
		 * <p>
		 * API name: {@code index_patterns}
		 */
		public final Builder indexPatterns(@Nullable String value) {
			this.indexPatterns = value;
			return this;
		}

		/**
		 * template application order/priority number
		 * <p>
		 * API name: {@code order}
		 */
		public final Builder order(@Nullable String value) {
			this.order = value;
			return this;
		}

		/**
		 * version
		 * <p>
		 * API name: {@code version}
		 */
		public final Builder version(@Nullable String value) {
			this.version = value;
			return this;
		}

		/**
		 * component templates comprising index template
		 * <p>
		 * API name: {@code composed_of}
		 */
		public final Builder composedOf(@Nullable String value) {
			this.composedOf = value;
			return this;
		}

		/**
		 * Builds a {@link TemplatesRecord}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public TemplatesRecord build() {
			_checkSingleUse();

			return new TemplatesRecord(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link TemplatesRecord}
	 */
	public static final JsonpDeserializer<TemplatesRecord> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			TemplatesRecord::setupTemplatesRecordDeserializer);

	protected static void setupTemplatesRecordDeserializer(ObjectDeserializer<TemplatesRecord.Builder> op) {

		op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name", "n");
		op.add(Builder::indexPatterns, JsonpDeserializer.stringDeserializer(), "index_patterns", "t");
		op.add(Builder::order, JsonpDeserializer.stringDeserializer(), "order", "o", "p");
		op.add(Builder::version, JsonpDeserializer.stringDeserializer(), "version", "v");
		op.add(Builder::composedOf, JsonpDeserializer.stringDeserializer(), "composed_of", "c");

	}

}
