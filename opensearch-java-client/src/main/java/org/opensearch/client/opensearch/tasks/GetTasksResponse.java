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

package org.opensearch.client.opensearch.tasks;

import org.opensearch.client.opensearch._types.ErrorCause;
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
import javax.annotation.Nullable;

// typedef: tasks.get.Response


@JsonpDeserializable
public class GetTasksResponse implements JsonpSerializable {
	private final boolean completed;

	private final Info task;

	@Nullable
	private final Status response;

	@Nullable
	private final ErrorCause error;

	// ---------------------------------------------------------------------------------------------

	private GetTasksResponse(Builder builder) {

		this.completed = ApiTypeHelper.requireNonNull(builder.completed, this, "completed");
		this.task = ApiTypeHelper.requireNonNull(builder.task, this, "task");
		this.response = builder.response;
		this.error = builder.error;

	}

	public static GetTasksResponse of(Function<Builder, ObjectBuilder<GetTasksResponse>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - API name: {@code completed}
	 */
	public final boolean completed() {
		return this.completed;
	}

	/**
	 * Required - API name: {@code task}
	 */
	public final Info task() {
		return this.task;
	}

	/**
	 * API name: {@code response}
	 */
	@Nullable
	public final Status response() {
		return this.response;
	}

	/**
	 * API name: {@code error}
	 */
	@Nullable
	public final ErrorCause error() {
		return this.error;
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

		generator.writeKey("completed");
		generator.write(this.completed);

		generator.writeKey("task");
		this.task.serialize(generator, mapper);

		if (this.response != null) {
			generator.writeKey("response");
			this.response.serialize(generator, mapper);

		}
		if (this.error != null) {
			generator.writeKey("error");
			this.error.serialize(generator, mapper);

		}

	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link GetTasksResponse}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GetTasksResponse> {
		private Boolean completed;

		private Info task;

		@Nullable
		private Status response;

		@Nullable
		private ErrorCause error;

		/**
		 * Required - API name: {@code completed}
		 */
		public final Builder completed(boolean value) {
			this.completed = value;
			return this;
		}

		/**
		 * Required - API name: {@code task}
		 */
		public final Builder task(Info value) {
			this.task = value;
			return this;
		}

		/**
		 * Required - API name: {@code task}
		 */
		public final Builder task(Function<Info.Builder, ObjectBuilder<Info>> fn) {
			return this.task(fn.apply(new Info.Builder()).build());
		}

		/**
		 * API name: {@code response}
		 */
		public final Builder response(@Nullable Status value) {
			this.response = value;
			return this;
		}

		/**
		 * API name: {@code response}
		 */
		public final Builder response(Function<Status.Builder, ObjectBuilder<Status>> fn) {
			return this.response(fn.apply(new Status.Builder()).build());
		}

		/**
		 * API name: {@code error}
		 */
		public final Builder error(@Nullable ErrorCause value) {
			this.error = value;
			return this;
		}

		/**
		 * API name: {@code error}
		 */
		public final Builder error(Function<ErrorCause.Builder, ObjectBuilder<ErrorCause>> fn) {
			return this.error(fn.apply(new ErrorCause.Builder()).build());
		}

		/**
		 * Builds a {@link GetTasksResponse}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public GetTasksResponse build() {
			_checkSingleUse();

			return new GetTasksResponse(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Json deserializer for {@link GetTasksResponse}
	 */
	public static final JsonpDeserializer<GetTasksResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
			GetTasksResponse::setupGetTasksResponseDeserializer);

	protected static void setupGetTasksResponseDeserializer(ObjectDeserializer<GetTasksResponse.Builder> op) {

		op.add(Builder::completed, JsonpDeserializer.booleanDeserializer(), "completed");
		op.add(Builder::task, Info._DESERIALIZER, "task");
		op.add(Builder::response, Status._DESERIALIZER, "response");
		op.add(Builder::error, ErrorCause._DESERIALIZER, "error");

	}

}
