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

package org.opensearch.client.opensearch._types.aggregations;

import org.opensearch.client.opensearch._types.SortOrder;
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

// typedef: _types.aggregations.HistogramOrder


@JsonpDeserializable
public class HistogramOrder implements JsonpSerializable {
	@Nullable
	private final SortOrder count;

	@Nullable
	private final SortOrder key;

	// ---------------------------------------------------------------------------------------------

	private HistogramOrder(Builder builder) {

		this.count = builder.count;
		this.key = builder.key;

	}

	public static HistogramOrder of(Function<Builder, ObjectBuilder<HistogramOrder>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * API name: {@code _count}
	 */
	@Nullable
	public final SortOrder count() {
		return this.count;
	}

	/**
	 * API name: {@code _key}
	 */
	@Nullable
	public final SortOrder key() {
		return this.key;
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

		if (this.count != null) {
			generator.writeKey("_count");
			this.count.serialize(generator, mapper);
		}
		if (this.key != null) {
			generator.writeKey("_key");
			this.key.serialize(generator, mapper);
		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link HistogramOrder}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<HistogramOrder> {
		@Nullable
		private SortOrder count;

		@Nullable
		private SortOrder key;

		/**
		 * API name: {@code _count}
		 */
		public final Builder count(@Nullable SortOrder value) {
			this.count = value;
			return this;
		}

		/**
		 * API name: {@code _key}
		 */
		public final Builder key(@Nullable SortOrder value) {
			this.key = value;
			return this;
		}

		/**
		 * Builds a {@link HistogramOrder}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public HistogramOrder build() {
			_checkSingleUse();

			return new HistogramOrder(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link HistogramOrder}
	 */
	public static final JsonpDeserializer<HistogramOrder> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			HistogramOrder::setupHistogramOrderDeserializer);

	protected static void setupHistogramOrderDeserializer(ObjectDeserializer<HistogramOrder.Builder> op) {

		op.add(Builder::count, SortOrder._DESERIALIZER, "_count");
		op.add(Builder::key, SortOrder._DESERIALIZER, "_key");

	}

}
