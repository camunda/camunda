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

package org.opensearch.client.opensearch.core.msearch;

import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import jakarta.json.stream.JsonGenerator;
import java.util.function.Function;
import java.util.function.Supplier;

// typedef: _global.msearch.MultiSearchItem



public class MultiSearchItem<TDocument> extends SearchResponse<TDocument> {
	private final int status;

	// ---------------------------------------------------------------------------------------------

	private MultiSearchItem(Builder<TDocument> builder) {
		super(builder);

		this.status = ApiTypeHelper.requireNonNull(builder.status, this, "status");

	}

	public static <TDocument> MultiSearchItem<TDocument> of(
			Function<Builder<TDocument>, ObjectBuilder<MultiSearchItem<TDocument>>> fn) {
		return fn.apply(new Builder<>()).build();
	}

	/**
	 * Required - API name: {@code status}
	 */
	public final int status() {
		return this.status;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		generator.writeKey("status");
		generator.write(this.status);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link MultiSearchItem}.
	 */

	public static class Builder<TDocument> extends SearchResponse.AbstractBuilder<TDocument, Builder<TDocument>>
			implements
				ObjectBuilder<MultiSearchItem<TDocument>> {
		private Integer status;

		/**
		 * Required - API name: {@code status}
		 */
		public final Builder<TDocument> status(int value) {
			this.status = value;
			return this;
		}

		@Override
		protected Builder<TDocument> self() {
			return this;
		}

		/**
		 * Builds a {@link MultiSearchItem}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public MultiSearchItem<TDocument> build() {
			_checkSingleUse();

			return new MultiSearchItem<TDocument>(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Create a JSON deserializer for MultiSearchItem
	 */
	public static <TDocument> JsonpDeserializer<MultiSearchItem<TDocument>> createMultiSearchItemDeserializer(
			JsonpDeserializer<TDocument> tDocumentDeserializer) {
		return ObjectBuilderDeserializer.createForObject((Supplier<Builder<TDocument>>) Builder::new,
				op -> MultiSearchItem.setupMultiSearchItemDeserializer(op, tDocumentDeserializer));
	};

	protected static <TDocument> void setupMultiSearchItemDeserializer(
			ObjectDeserializer<MultiSearchItem.Builder<TDocument>> op,
			JsonpDeserializer<TDocument> tDocumentDeserializer) {
		SearchResponse.setupSearchResponseDeserializer(op, tDocumentDeserializer);
		op.add(Builder::status, JsonpDeserializer.integerDeserializer(), "status");

	}

}
