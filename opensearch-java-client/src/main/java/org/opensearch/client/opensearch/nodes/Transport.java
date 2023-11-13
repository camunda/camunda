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
import java.util.function.Function;

// typedef: nodes._types.Transport


@JsonpDeserializable
public class Transport implements JsonpSerializable {
	private final long rxCount;

	private final long rxSizeInBytes;

	private final int serverOpen;

	private final long txCount;

	private final long txSizeInBytes;

	// ---------------------------------------------------------------------------------------------

	private Transport(Builder builder) {

		this.rxCount = ApiTypeHelper.requireNonNull(builder.rxCount, this, "rxCount");
		this.rxSizeInBytes = ApiTypeHelper.requireNonNull(builder.rxSizeInBytes, this, "rxSizeInBytes");
		this.serverOpen = ApiTypeHelper.requireNonNull(builder.serverOpen, this, "serverOpen");
		this.txCount = ApiTypeHelper.requireNonNull(builder.txCount, this, "txCount");
		this.txSizeInBytes = ApiTypeHelper.requireNonNull(builder.txSizeInBytes, this, "txSizeInBytes");

	}

	public static Transport of(Function<Builder, ObjectBuilder<Transport>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code rx_count}
	 */
	public final long rxCount() {
		return this.rxCount;
	}

	/**
	 * Required - API name: {@code rx_size_in_bytes}
	 */
	public final long rxSizeInBytes() {
		return this.rxSizeInBytes;
	}

	/**
	 * Required - API name: {@code server_open}
	 */
	public final int serverOpen() {
		return this.serverOpen;
	}

	/**
	 * Required - API name: {@code tx_count}
	 */
	public final long txCount() {
		return this.txCount;
	}

	/**
	 * Required - API name: {@code tx_size_in_bytes}
	 */
	public final long txSizeInBytes() {
		return this.txSizeInBytes;
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

		generator.writeKey("rx_count");
		generator.write(this.rxCount);

		generator.writeKey("rx_size_in_bytes");
		generator.write(this.rxSizeInBytes);

		generator.writeKey("server_open");
		generator.write(this.serverOpen);

		generator.writeKey("tx_count");
		generator.write(this.txCount);

		generator.writeKey("tx_size_in_bytes");
		generator.write(this.txSizeInBytes);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link Transport}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<Transport> {
		private Long rxCount;

		private Long rxSizeInBytes;

		private Integer serverOpen;

		private Long txCount;

		private Long txSizeInBytes;

		/**
		 * Required - API name: {@code rx_count}
		 */
		public final Builder rxCount(long value) {
			this.rxCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code rx_size_in_bytes}
		 */
		public final Builder rxSizeInBytes(long value) {
			this.rxSizeInBytes = value;
			return this;
		}

		/**
		 * Required - API name: {@code server_open}
		 */
		public final Builder serverOpen(int value) {
			this.serverOpen = value;
			return this;
		}

		/**
		 * Required - API name: {@code tx_count}
		 */
		public final Builder txCount(long value) {
			this.txCount = value;
			return this;
		}

		/**
		 * Required - API name: {@code tx_size_in_bytes}
		 */
		public final Builder txSizeInBytes(long value) {
			this.txSizeInBytes = value;
			return this;
		}

		/**
		 * Builds a {@link Transport}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public Transport build() {
			_checkSingleUse();

			return new Transport(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link Transport}
	 */
	public static final JsonpDeserializer<Transport> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			Transport::setupTransportDeserializer);

	protected static void setupTransportDeserializer(ObjectDeserializer<Transport.Builder> op) {

		op.add(Builder::rxCount, JsonpDeserializer.longDeserializer(), "rx_count");
		op.add(Builder::rxSizeInBytes, JsonpDeserializer.longDeserializer(), "rx_size_in_bytes");
		op.add(Builder::serverOpen, JsonpDeserializer.integerDeserializer(), "server_open");
		op.add(Builder::txCount, JsonpDeserializer.longDeserializer(), "tx_count");
		op.add(Builder::txSizeInBytes, JsonpDeserializer.longDeserializer(), "tx_size_in_bytes");

	}

}
