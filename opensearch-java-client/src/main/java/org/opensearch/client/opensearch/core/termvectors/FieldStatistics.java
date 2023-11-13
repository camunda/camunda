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

package org.opensearch.client.opensearch.core.termvectors;

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
import java.util.function.Function;

// typedef: _global.termvectors.FieldStatistics


@JsonpDeserializable
public class FieldStatistics implements JsonpSerializable {
	private final int docCount;

	private final long sumDocFreq;

	private final long sumTtf;

	// ---------------------------------------------------------------------------------------------

	private FieldStatistics(Builder builder) {

		this.docCount = ApiTypeHelper.requireNonNull(builder.docCount, this, "docCount");
		this.sumDocFreq = ApiTypeHelper.requireNonNull(builder.sumDocFreq, this, "sumDocFreq");
		this.sumTtf = ApiTypeHelper.requireNonNull(builder.sumTtf, this, "sumTtf");

	}

	public static FieldStatistics of(Function<Builder, ObjectBuilder<FieldStatistics>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code doc_count}
	 */
	public final int docCount() {
		return this.docCount;
	}

	/**
	 * Required - API name: {@code sum_doc_freq}
	 */
	public final long sumDocFreq() {
		return this.sumDocFreq;
	}

	/**
	 * Required - API name: {@code sum_ttf}
	 */
	public final long sumTtf() {
		return this.sumTtf;
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

		generator.writeKey("doc_count");
		generator.write(this.docCount);

		generator.writeKey("sum_doc_freq");
		generator.write(this.sumDocFreq);

		generator.writeKey("sum_ttf");
		generator.write(this.sumTtf);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link FieldStatistics}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<FieldStatistics> {
		private Integer docCount;

		private Long sumDocFreq;

		private Long sumTtf;

		/**
		 * Required - API name: {@code doc_count}
		 */
		public final Builder docCount(int value) {
			this.docCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code sum_doc_freq}
		 */
		public final Builder sumDocFreq(long value) {
			this.sumDocFreq = value;
			return this;
		}

		/**
		 * Required - API name: {@code sum_ttf}
		 */
		public final Builder sumTtf(long value) {
			this.sumTtf = value;
			return this;
		}

		/**
		 * Builds a {@link FieldStatistics}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public FieldStatistics build() {
			_checkSingleUse();

			return new FieldStatistics(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link FieldStatistics}
	 */
	public static final JsonpDeserializer<FieldStatistics> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			FieldStatistics::setupFieldStatisticsDeserializer);

	protected static void setupFieldStatisticsDeserializer(ObjectDeserializer<FieldStatistics.Builder> op) {

		op.add(Builder::docCount, JsonpDeserializer.integerDeserializer(), "doc_count");
		op.add(Builder::sumDocFreq, JsonpDeserializer.longDeserializer(), "sum_doc_freq");
		op.add(Builder::sumTtf, JsonpDeserializer.longDeserializer(), "sum_ttf");

	}

}
