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

import org.opensearch.client.opensearch.core.bulk.BulkResponseItem;
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

// typedef: _global.bulk.Response


@JsonpDeserializable
public class BulkResponse implements JsonpSerializable {
	private final boolean errors;

	private final List<BulkResponseItem> items;

	private final long took;

	@Nullable
	private final Long ingestTook;

	// ---------------------------------------------------------------------------------------------

	private BulkResponse(Builder builder) {

		this.errors = ApiTypeHelper.requireNonNull(builder.errors, this, "errors");
		this.items = ApiTypeHelper.unmodifiableRequired(builder.items, this, "items");
		this.took = ApiTypeHelper.requireNonNull(builder.took, this, "took");
		this.ingestTook = builder.ingestTook;

	}

	public static BulkResponse of(Function<Builder, ObjectBuilder<BulkResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code errors}
	 */
	public final boolean errors() {
		return this.errors;
	}

	/**
	 * Required - API name: {@code items}
	 */
	public final List<BulkResponseItem> items() {
		return this.items;
	}

	/**
	 * Required - API name: {@code took}
	 */
	public final long took() {
		return this.took;
	}

	/**
	 * API name: {@code ingest_took}
	 */
	@Nullable
	public final Long ingestTook() {
		return this.ingestTook;
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

		generator.writeKey("errors");
		generator.write(this.errors);

		if (ApiTypeHelper.isDefined(this.items)) {
			generator.writeKey("items");
			generator.writeStartArray();
			for (BulkResponseItem item0 : this.items) {
				item0.serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("took");
		generator.write(this.took);

		if (this.ingestTook != null) {
			generator.writeKey("ingest_took");
			generator.write(this.ingestTook);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link BulkResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<BulkResponse> {
		private Boolean errors;

		private List<BulkResponseItem> items;

		private Long took;

		@Nullable
		private Long ingestTook;

		/**
		 * Required - API name: {@code errors}
		 */
		public final Builder errors(boolean value) {
			this.errors = value;
			return this;
		}

		/**
		 * Required - API name: {@code items}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>items</code>.
		 */
		public final Builder items(List<BulkResponseItem> list) {
			this.items = _listAddAll(this.items, list);
			return this;
		}

		/**
		 * Required - API name: {@code items}
		 * <p>
		 * Adds one or more values to <code>items</code>.
		 */
		public final Builder items(BulkResponseItem value, BulkResponseItem... values) {
			this.items = _listAdd(this.items, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code items}
		 * <p>
		 * Adds a value to <code>items</code> using a builder lambda.
		 */
		public final Builder items(Function<BulkResponseItem.Builder, ObjectBuilder<BulkResponseItem>> fn) {
			return items(fn.apply(new BulkResponseItem.Builder()).build());
		}

		/**
		 * Required - API name: {@code took}
		 */
		public final Builder took(long value) {
			this.took = value;
			return this;
		}

		/**
		 * API name: {@code ingest_took}
		 */
		public final Builder ingestTook(@Nullable Long value) {
			this.ingestTook = value;
			return this;
		}

		/**
		 * Builds a {@link BulkResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public BulkResponse build() {
			_checkSingleUse();

			return new BulkResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link BulkResponse}
	 */
	public static final JsonpDeserializer<BulkResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			BulkResponse::setupBulkResponseDeserializer);

	protected static void setupBulkResponseDeserializer(ObjectDeserializer<BulkResponse.Builder> op) {

		op.add(Builder::errors, JsonpDeserializer.booleanDeserializer(), "errors");
		op.add(Builder::items, JsonpDeserializer.arrayDeserializer(BulkResponseItem._DESERIALIZER), "items");
		op.add(Builder::took, JsonpDeserializer.longDeserializer(), "took");
		op.add(Builder::ingestTook, JsonpDeserializer.longDeserializer(), "ingest_took");

	}

}
