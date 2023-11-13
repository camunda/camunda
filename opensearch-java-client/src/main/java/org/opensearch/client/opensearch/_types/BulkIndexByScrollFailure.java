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

package org.opensearch.client.opensearch._types;

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

// typedef: _types.BulkIndexByScrollFailure

@JsonpDeserializable
public class BulkIndexByScrollFailure implements JsonpSerializable {
	private final ErrorCause cause;

	private final String id;

	private final String index;

	private final int status;

	private final String type;

	// ---------------------------------------------------------------------------------------------

	private BulkIndexByScrollFailure(Builder builder) {

		this.cause = ApiTypeHelper.requireNonNull(builder.cause, this, "cause");
		this.id = ApiTypeHelper.requireNonNull(builder.id, this, "id");
		this.index = ApiTypeHelper.requireNonNull(builder.index, this, "index");
		this.status = ApiTypeHelper.requireNonNull(builder.status, this, "status");
		this.type = ApiTypeHelper.requireNonNull(builder.type, this, "type");

	}

	public static BulkIndexByScrollFailure of(Function<Builder, ObjectBuilder<BulkIndexByScrollFailure>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code cause}
	 */
	public final ErrorCause cause() {
		return this.cause;
	}

	/**
	 * Required - API name: {@code id}
	 */
	public final String id() {
		return this.id;
	}

	/**
	 * Required - API name: {@code index}
	 */
	public final String index() {
		return this.index;
	}

	/**
	 * Required - API name: {@code status}
	 */
	public final int status() {
		return this.status;
	}

	/**
	 * Required - API name: {@code type}
	 */
	public final String type() {
		return this.type;
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

		generator.writeKey("cause");
		this.cause.serialize(generator, mapper);

		generator.writeKey("id");
		generator.write(this.id);

		generator.writeKey("index");
		generator.write(this.index);

		generator.writeKey("status");
		generator.write(this.status);

		generator.writeKey("type");
		generator.write(this.type);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link BulkIndexByScrollFailure}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<BulkIndexByScrollFailure> {
		private ErrorCause cause;

		private String id;

		private String index;

		private Integer status;

		private String type;

		/**
		 * Required - API name: {@code cause}
		 */
		public final Builder cause(ErrorCause value) {
			this.cause = value;
			return this;
		}

		/**
		 * Required - API name: {@code cause}
		 */
		public final Builder cause(Function<ErrorCause.Builder, ObjectBuilder<ErrorCause>> fn) {
			return this.cause(fn.apply(new ErrorCause.Builder()).build());
		}

		/**
		 * Required - API name: {@code id}
		 */
		public final Builder id(String value) {
			this.id = value;
			return this;
		}

		/**
		 * Required - API name: {@code index}
		 */
		public final Builder index(String value) {
			this.index = value;
			return this;
		}

		/**
		 * Required - API name: {@code status}
		 */
		public final Builder status(int value) {
			this.status = value;
			return this;
		}

		/**
		 * Required - API name: {@code type}
		 */
		public final Builder type(String value) {
			this.type = value;
			return this;
		}

		/**
		 * Builds a {@link BulkIndexByScrollFailure}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public BulkIndexByScrollFailure build() {
			_checkSingleUse();

			return new BulkIndexByScrollFailure(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link BulkIndexByScrollFailure}
	 */
	public static final JsonpDeserializer<BulkIndexByScrollFailure> _DESERIALIZER = ObjectBuilderDeserializer
			.lazy(Builder::new, BulkIndexByScrollFailure::setupBulkIndexByScrollFailureDeserializer);

	protected static void setupBulkIndexByScrollFailureDeserializer(
			ObjectDeserializer<BulkIndexByScrollFailure.Builder> op) {

		op.add(Builder::cause, ErrorCause._DESERIALIZER, "cause");
		op.add(Builder::id, JsonpDeserializer.stringDeserializer(), "id");
		op.add(Builder::index, JsonpDeserializer.stringDeserializer(), "index");
		op.add(Builder::status, JsonpDeserializer.integerDeserializer(), "status");
		op.add(Builder::type, JsonpDeserializer.stringDeserializer(), "type");

	}

}
