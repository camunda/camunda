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

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import jakarta.json.stream.JsonGenerator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

// typedef: _types.aggregations.MatrixAggregation



public abstract class MatrixAggregation extends AggregationBase {
	private final List<String> fields;

	private final Map<String, Double> missing;

	// ---------------------------------------------------------------------------------------------

	protected MatrixAggregation(AbstractBuilder<?> builder) {
		super(builder);

		this.fields = ApiTypeHelper.unmodifiable(builder.fields);
		this.missing = ApiTypeHelper.unmodifiable(builder.missing);

	}

	/**
	 * API name: {@code fields}
	 */
	public final List<String> fields() {
		return this.fields;
	}

	/**
	 * API name: {@code missing}
	 */
	public final Map<String, Double> missing() {
		return this.missing;
	}

	protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {

		super.serializeInternal(generator, mapper);
		if (ApiTypeHelper.isDefined(this.fields)) {
			generator.writeKey("fields");
			generator.writeStartArray();
			for (String item0 : this.fields) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.missing)) {
			generator.writeKey("missing");
			generator.writeStartObject();
			for (Map.Entry<String, Double> item0 : this.missing.entrySet()) {
				generator.writeKey(item0.getKey());
				generator.write(item0.getValue());

			}
			generator.writeEnd();

		}

	}

	protected abstract static class AbstractBuilder<BuilderT extends AbstractBuilder<BuilderT>>
			extends
				AggregationBase.AbstractBuilder<BuilderT> {
		@Nullable
		private List<String> fields;

		@Nullable
		private Map<String, Double> missing;

		/**
		 * API name: {@code fields}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>fields</code>.
		 */
		public final BuilderT fields(List<String> list) {
			this.fields = _listAddAll(this.fields, list);
			return self();
		}

		/**
		 * API name: {@code fields}
		 * <p>
		 * Adds one or more values to <code>fields</code>.
		 */
		public final BuilderT fields(String value, String... values) {
			this.fields = _listAdd(this.fields, value, values);
			return self();
		}

		/**
		 * API name: {@code missing}
		 * <p>
		 * Adds all entries of <code>map</code> to <code>missing</code>.
		 */
		public final BuilderT missing(Map<String, Double> map) {
			this.missing = _mapPutAll(this.missing, map);
			return self();
		}

		/**
		 * API name: {@code missing}
		 * <p>
		 * Adds an entry to <code>missing</code>.
		 */
		public final BuilderT missing(String key, Double value) {
			this.missing = _mapPut(this.missing, key, value);
			return self();
		}

	}

	// ---------------------------------------------------------------------------------------------
	protected static <BuilderT extends AbstractBuilder<BuilderT>> void setupMatrixAggregationDeserializer(
			ObjectDeserializer<BuilderT> op) {
		setupAggregationBaseDeserializer(op);
		op.add(AbstractBuilder::fields, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()),
				"fields");
		op.add(AbstractBuilder::missing,
				JsonpDeserializer.stringMapDeserializer(JsonpDeserializer.doubleDeserializer()), "missing");

	}

}
