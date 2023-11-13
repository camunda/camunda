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
import org.opensearch.client.json.UnionDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;
import jakarta.json.stream.JsonGenerator;

import java.util.function.Function;

// typedef: _types.ErrorResponseBase

/**
 * The response returned by Elasticsearch when request execution did not
 * succeed.
 * 
 */
@JsonpDeserializable
public class ErrorResponse implements JsonpSerializable {

	private enum Kind {
		OBJECT,
		STRING
	}
	private final ErrorCause error;

	private final int status;

	// ---------------------------------------------------------------------------------------------

	private ErrorResponse(Builder builder) {

		this.error = ApiTypeHelper.requireNonNull(builder.error, this, "error");
		this.status = ApiTypeHelper.requireNonNull(builder.status, this, "status");

	}

	public static ErrorResponse of(Function<Builder, ObjectBuilder<ErrorResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code error}
	 */
	public final ErrorCause error() {
		return this.error;
	}

	/**
	 * Required - API name: {@code status}
	 */
	public final int status() {
		return this.status;
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

		generator.writeKey("error");
		this.error.serialize(generator, mapper);

		generator.writeKey("status");
		generator.write(this.status);

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link ErrorResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ErrorResponse> {
		private ErrorCause error;

		private Integer status;

		/**
		 * Required - API name: {@code error}
		 */
		public final Builder error(ErrorCause value) {
			this.error = value;
			return this;
		}

		/**
		 * Required - API name: {@code error}
		 */
		public final Builder error(Function<ErrorCause.Builder, ObjectBuilder<ErrorCause>> fn) {
			return this.error(fn.apply(new ErrorCause.Builder()).build());
		}

		/**
		 * Required - API name: {@code status}
		 */
		public final Builder status(int value) {
			this.status = value;
			return this;
		}

		/**
		 * Builds a {@link ErrorResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public ErrorResponse build() {
			_checkSingleUse();

			return new ErrorResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link ErrorResponse}
	 */
	public static final JsonpDeserializer<ErrorResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			ErrorResponse::setupErrorResponseDeserializer);

	protected static void setupErrorResponseDeserializer(ObjectDeserializer<ErrorResponse.Builder> op) {

		op.add(Builder::error, buildErrorCauseDeserializers(), "error");
		op.add(Builder::status, JsonpDeserializer.integerDeserializer(), "status");

	}

	protected static JsonpDeserializer<ErrorCause> buildErrorCauseDeserializers() {
		return new UnionDeserializer.Builder<>(ErrorResponse::getErrorCause, false)
				.addMember(Kind.OBJECT, ErrorCause._DESERIALIZER)
				.addMember(Kind.STRING, JsonpDeserializer.stringDeserializer())
				.build();
	}

	private static ErrorCause getErrorCause(Kind kind, Object errorCause) {
		return Kind.STRING.equals(kind) ?
				ErrorCause.of(builder -> builder.type("string_error").reason((String) errorCause)) :
				(ErrorCause) errorCause;
	}

}
