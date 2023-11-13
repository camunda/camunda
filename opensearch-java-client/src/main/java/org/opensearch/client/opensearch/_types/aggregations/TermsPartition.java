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

// typedef: _types.aggregations.TermsPartition


@JsonpDeserializable
public class TermsPartition implements JsonpSerializable {
	private final long numPartitions;

	private final long partition;

	// ---------------------------------------------------------------------------------------------

	private TermsPartition(Builder builder) {

		this.numPartitions = ApiTypeHelper.requireNonNull(builder.numPartitions, this, "numPartitions");
		this.partition = ApiTypeHelper.requireNonNull(builder.partition, this, "partition");

	}

	public static TermsPartition of(Function<Builder, ObjectBuilder<TermsPartition>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code num_partitions}
	 */
	public final long numPartitions() {
		return this.numPartitions;
	}

	/**
	 * Required - API name: {@code partition}
	 */
	public final long partition() {
		return this.partition;
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

		generator.writeKey("num_partitions");
		generator.write(this.numPartitions);

		generator.writeKey("partition");
		generator.write(this.partition);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link TermsPartition}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<TermsPartition> {
		private Long numPartitions;

		private Long partition;

		/**
		 * Required - API name: {@code num_partitions}
		 */
		public final Builder numPartitions(long value) {
			this.numPartitions = value;
			return this;
		}

		/**
		 * Required - API name: {@code partition}
		 */
		public final Builder partition(long value) {
			this.partition = value;
			return this;
		}

		/**
		 * Builds a {@link TermsPartition}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public TermsPartition build() {
			_checkSingleUse();

			return new TermsPartition(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link TermsPartition}
	 */
	public static final JsonpDeserializer<TermsPartition> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			TermsPartition::setupTermsPartitionDeserializer);

	protected static void setupTermsPartitionDeserializer(ObjectDeserializer<TermsPartition.Builder> op) {

		op.add(Builder::numPartitions, JsonpDeserializer.longDeserializer(), "num_partitions");
		op.add(Builder::partition, JsonpDeserializer.longDeserializer(), "partition");

	}

}
