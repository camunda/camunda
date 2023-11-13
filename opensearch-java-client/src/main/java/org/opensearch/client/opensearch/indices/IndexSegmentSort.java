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

package org.opensearch.client.opensearch.indices;

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

// typedef: indices._types.IndexSegmentSort


@JsonpDeserializable
public class IndexSegmentSort implements JsonpSerializable {
	private final List<String> field;

	private final List<SegmentSortOrder> order;

	private final List<SegmentSortMode> mode;

	private final List<SegmentSortMissing> missing;

	// ---------------------------------------------------------------------------------------------

	private IndexSegmentSort(Builder builder) {

		this.field = ApiTypeHelper.unmodifiableRequired(builder.field, this, "field");
		this.order = ApiTypeHelper.unmodifiableRequired(builder.order, this, "order");
		this.mode = ApiTypeHelper.unmodifiable(builder.mode);
		this.missing = ApiTypeHelper.unmodifiable(builder.missing);

	}

	public static IndexSegmentSort of(Function<Builder, ObjectBuilder<IndexSegmentSort>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code field}
	 */
	public final List<String> field() {
		return this.field;
	}

	/**
	 * Required - API name: {@code order}
	 */
	public final List<SegmentSortOrder> order() {
		return this.order;
	}

	/**
	 * API name: {@code mode}
	 */
	public final List<SegmentSortMode> mode() {
		return this.mode;
	}

	/**
	 * API name: {@code missing}
	 */
	public final List<SegmentSortMissing> missing() {
		return this.missing;
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

		if (ApiTypeHelper.isDefined(this.field)) {
			generator.writeKey("field");
			generator.writeStartArray();
			for (String item0 : this.field) {
				generator.write(item0);

			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.order)) {
			generator.writeKey("order");
			generator.writeStartArray();
			for (SegmentSortOrder item0 : this.order) {
				item0.serialize(generator, mapper);
			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.mode)) {
			generator.writeKey("mode");
			generator.writeStartArray();
			for (SegmentSortMode item0 : this.mode) {
				item0.serialize(generator, mapper);
			}
			generator.writeEnd();

		}
		if (ApiTypeHelper.isDefined(this.missing)) {
			generator.writeKey("missing");
			generator.writeStartArray();
			for (SegmentSortMissing item0 : this.missing) {
				item0.serialize(generator, mapper);
			}
			generator.writeEnd();

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link IndexSegmentSort}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<IndexSegmentSort> {
		private List<String> field;

		private List<SegmentSortOrder> order;

		@Nullable
		private List<SegmentSortMode> mode;

		@Nullable
		private List<SegmentSortMissing> missing;

		/**
		 * Required - API name: {@code field}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>field</code>.
		 */
		public final Builder field(List<String> list) {
			this.field = _listAddAll(this.field, list);
			return this;
		}

		/**
		 * Required - API name: {@code field}
		 * <p>
		 * Adds one or more values to <code>field</code>.
		 */
		public final Builder field(String value, String... values) {
			this.field = _listAdd(this.field, value, values);
			return this;
		}

		/**
		 * Required - API name: {@code order}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>order</code>.
		 */
		public final Builder order(List<SegmentSortOrder> list) {
			this.order = _listAddAll(this.order, list);
			return this;
		}

		/**
		 * Required - API name: {@code order}
		 * <p>
		 * Adds one or more values to <code>order</code>.
		 */
		public final Builder order(SegmentSortOrder value, SegmentSortOrder... values) {
			this.order = _listAdd(this.order, value, values);
			return this;
		}

		/**
		 * API name: {@code mode}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>mode</code>.
		 */
		public final Builder mode(List<SegmentSortMode> list) {
			this.mode = _listAddAll(this.mode, list);
			return this;
		}

		/**
		 * API name: {@code mode}
		 * <p>
		 * Adds one or more values to <code>mode</code>.
		 */
		public final Builder mode(SegmentSortMode value, SegmentSortMode... values) {
			this.mode = _listAdd(this.mode, value, values);
			return this;
		}

		/**
		 * API name: {@code missing}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>missing</code>.
		 */
		public final Builder missing(List<SegmentSortMissing> list) {
			this.missing = _listAddAll(this.missing, list);
			return this;
		}

		/**
		 * API name: {@code missing}
		 * <p>
		 * Adds one or more values to <code>missing</code>.
		 */
		public final Builder missing(SegmentSortMissing value, SegmentSortMissing... values) {
			this.missing = _listAdd(this.missing, value, values);
			return this;
		}

		/**
		 * Builds a {@link IndexSegmentSort}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public IndexSegmentSort build() {
			_checkSingleUse();

			return new IndexSegmentSort(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link IndexSegmentSort}
	 */
	public static final JsonpDeserializer<IndexSegmentSort> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			IndexSegmentSort::setupIndexSegmentSortDeserializer);

	protected static void setupIndexSegmentSortDeserializer(ObjectDeserializer<IndexSegmentSort.Builder> op) {

		op.add(Builder::field, JsonpDeserializer.arrayDeserializer(JsonpDeserializer.stringDeserializer()), "field");
		op.add(Builder::order, JsonpDeserializer.arrayDeserializer(SegmentSortOrder._DESERIALIZER), "order");
		op.add(Builder::mode, JsonpDeserializer.arrayDeserializer(SegmentSortMode._DESERIALIZER), "mode");
		op.add(Builder::missing, JsonpDeserializer.arrayDeserializer(SegmentSortMissing._DESERIALIZER), "missing");

	}

}
