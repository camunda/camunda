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

package org.opensearch.client.opensearch.nodes;

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
import java.util.Map;
import java.util.function.Function;

// typedef: nodes._types.Ingest


@JsonpDeserializable
public class Ingest implements JsonpSerializable {
	private final Map<String, IngestTotal> pipelines;

	private final IngestTotal total;

	// ---------------------------------------------------------------------------------------------

	private Ingest(Builder builder) {

		this.pipelines = ApiTypeHelper.unmodifiableRequired(builder.pipelines, this, "pipelines");
		this.total = ApiTypeHelper.requireNonNull(builder.total, this, "total");

	}

	public static Ingest of(Function<Builder, ObjectBuilder<Ingest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code pipelines}
	 */
	public final Map<String, IngestTotal> pipelines() {
		return this.pipelines;
	}

	/**
	 * Required - API name: {@code total}
	 */
	public final IngestTotal total() {
		return this.total;
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

		if (ApiTypeHelper.isDefined(this.pipelines)) {
			generator.writeKey("pipelines");
			generator.writeStartObject();
			for (Map.Entry<String, IngestTotal> item0 : this.pipelines.entrySet()) {
				generator.writeKey(item0.getKey());
				item0.getValue().serialize(generator, mapper);

			}
			generator.writeEnd();

		}
		generator.writeKey("total");
		this.total.serialize(generator, mapper);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Ingest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Ingest> {
		private Map<String, IngestTotal> pipelines;

		private IngestTotal total;

		/**
		 * Required - API name: {@code pipelines}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>pipelines</code>.
		 */
		public final Builder pipelines(Map<String, IngestTotal> map) {
			this.pipelines = _mapPutAll(this.pipelines, map);
			return this;
		}

		/**
		 * Required - API name: {@code pipelines}
		 * <p>
		 * Adds an entry to <code>pipelines</code>.
		 */
		public final Builder pipelines(String key, IngestTotal value) {
			this.pipelines = _mapPut(this.pipelines, key, value);
			return this;
		}

		/**
		 * Required - API name: {@code pipelines}
		 * <p>
		 * Adds an entry to <code>pipelines</code> using a builder lambda.
		 */
		public final Builder pipelines(String key, Function<IngestTotal.Builder, ObjectBuilder<IngestTotal>> fn) {
			return pipelines(key, fn.apply(new IngestTotal.Builder()).build());
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(IngestTotal value) {
			this.total = value;
			return this;
		}

		/**
		 * Required - API name: {@code total}
		 */
		public final Builder total(Function<IngestTotal.Builder, ObjectBuilder<IngestTotal>> fn) {
			return this.total(fn.apply(new IngestTotal.Builder()).build());
		}

		/**
		 * Builds a {@link Ingest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Ingest build() {
			_checkSingleUse();

			return new Ingest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Ingest}
	 */
	public static final JsonpDeserializer<Ingest> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Ingest::setupIngestDeserializer);

	protected static void setupIngestDeserializer(ObjectDeserializer<Ingest.Builder> op) {

		op.add(Builder::pipelines, JsonpDeserializer.stringMapDeserializer(IngestTotal._DESERIALIZER), "pipelines");
		op.add(Builder::total, IngestTotal._DESERIALIZER, "total");

	}

}
