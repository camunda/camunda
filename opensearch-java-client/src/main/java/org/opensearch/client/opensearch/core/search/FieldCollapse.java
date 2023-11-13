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
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: _global.search._types.FieldCollapse

@JsonpDeserializable
public class FieldCollapse implements JsonpSerializable {
	private final String field;

	private final List<InnerHits> innerHits;

	@Nullable
	private final Integer maxConcurrentGroupSearches;

	// ---------------------------------------------------------------------------------------------

	private FieldCollapse(Builder builder) {

		this.field = ApiTypeHelper.requireNonNull(builder.field, this, "field");
		this.innerHits = ApiTypeHelper.unmodifiable(builder.innerHits);
		this.maxConcurrentGroupSearches = builder.maxConcurrentGroupSearches;

	}

	public static FieldCollapse of(Function<Builder, ObjectBuilder<FieldCollapse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code field}
	 */
	public final String field() {
		return this.field;
	}

	/**
	 * API name: {@code inner_hits}
	 */
	public final List<InnerHits> innerHits() {
		return this.innerHits;
	}

	/**
	 * API name: {@code max_concurrent_group_searches}
	 */
	@Nullable
	public final Integer maxConcurrentGroupSearches() {
		return this.maxConcurrentGroupSearches;
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

		generator.writeKey("field");
		generator.write(this.field);

		if (ApiTypeHelper.isDefined(this.innerHits)) {
			generator.writeKey("inner_hits");
			generator.writeStartArray();
			for (InnerHits item0 : this.innerHits) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		if (this.maxConcurrentGroupSearches != null) {
			generator.writeKey("max_concurrent_group_searches");
			generator.write(this.maxConcurrentGroupSearches);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link FieldCollapse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<FieldCollapse> {
		private String field;

		@Nullable
		private List<InnerHits> innerHits;

		@Nullable
		private Integer maxConcurrentGroupSearches;

		/**
		 * Required - API name: {@code field}
		 */
		public final Builder field(String value) {
			this.field = value;
			return this;
		}

		/**
		 * API name: {@code inner_hits}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>innerHits</code>.
		 */
		public final Builder innerHits(List<InnerHits> list) {
			this.innerHits = _listAddAll(this.innerHits, list);
			return this;
		}

		/**
		 * API name: {@code inner_hits}
		 * <p>
		 * Adds one or more values to <code>innerHits</code>.
		 */
		public final Builder innerHits(InnerHits value, InnerHits... values) {
			this.innerHits = _listAdd(this.innerHits, value, values);
			return this;
		}

		/**
		 * API name: {@code inner_hits}
		 * <p>
		 * Adds a value to <code>innerHits</code> using a builder lambda.
		 */
		public final Builder innerHits(Function<InnerHits.Builder, ObjectBuilder<InnerHits>> fn) {
			return innerHits(fn.apply(new InnerHits.Builder()).build());
		}

		/**
		 * API name: {@code max_concurrent_group_searches}
		 */
		public final Builder maxConcurrentGroupSearches(@Nullable Integer value) {
			this.maxConcurrentGroupSearches = value;
			return this;
		}

		/**
		 * Builds a {@link FieldCollapse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public FieldCollapse build() {
			_checkSingleUse();

			return new FieldCollapse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link FieldCollapse}
	 */
	public static final JsonpDeserializer<FieldCollapse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			FieldCollapse::setupFieldCollapseDeserializer);

	protected static void setupFieldCollapseDeserializer(ObjectDeserializer<FieldCollapse.Builder> op) {

		op.add(Builder::field, JsonpDeserializer.stringDeserializer(), "field");
		op.add(Builder::innerHits, JsonpDeserializer.arrayDeserializer(InnerHits._DESERIALIZER), "inner_hits");
		op.add(Builder::maxConcurrentGroupSearches, JsonpDeserializer.integerDeserializer(),
				"max_concurrent_group_searches");

	}

}
