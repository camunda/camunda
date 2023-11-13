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

import org.opensearch.client.ApiClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.JsonEndpoint;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.util.ObjectBuilder;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Client for the tasks namespace.
 */
public class OpenSearchTasksAsyncClient extends ApiClient<OpenSearchTransport, OpenSearchTasksAsyncClient> {

	public OpenSearchTasksAsyncClient(OpenSearchTransport transport) {
		super(transport, null);
	}

	public OpenSearchTasksAsyncClient(OpenSearchTransport transport,
									  @Nullable TransportOptions transportOptions) {
		super(transport, transportOptions);
	}

	@Override
	public OpenSearchTasksAsyncClient withTransportOptions(@Nullable TransportOptions transportOptions) {
		return new OpenSearchTasksAsyncClient(this.transport, transportOptions);
	}

	// ----- Endpoint: tasks.cancel

	/**
	 * Cancels a task, if it can be cancelled through an API.
	 *
	 *
	 */

	public CompletableFuture<CancelResponse> cancel(CancelRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<CancelRequest, CancelResponse, ErrorResponse> endpoint =
				(JsonEndpoint<CancelRequest, CancelResponse, ErrorResponse>) CancelRequest._ENDPOINT;

		return this.transport.performRequestAsync(request, endpoint, this.transportOptions);
	}

	/**
	 * Cancels a task, if it can be cancelled through an API.
	 *
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link CancelRequest}
	 *
	 */

	public final CompletableFuture<CancelResponse> cancel(
			Function<CancelRequest.Builder, ObjectBuilder<CancelRequest>> fn)
			throws IOException, OpenSearchException {
		return cancel(fn.apply(new CancelRequest.Builder()).build());
	}

	/**
	 * Cancels a task, if it can be cancelled through an API.
	 *
	 *
	 */

	public CompletableFuture<CancelResponse> cancel() throws IOException, OpenSearchException {
		return this.transport.performRequestAsync(new CancelRequest.Builder().build(), CancelRequest._ENDPOINT,
				this.transportOptions);
	}

	// ----- Endpoint: tasks.get

	/**
	 * Returns information about a task.
	 *
	 *
	 */

	public CompletableFuture<GetTasksResponse> get(GetTasksRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<GetTasksRequest, GetTasksResponse, ErrorResponse> endpoint =
				(JsonEndpoint<GetTasksRequest, GetTasksResponse, ErrorResponse>) GetTasksRequest._ENDPOINT;

		return this.transport.performRequestAsync(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns information about a task.
	 *
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link GetTasksRequest}
	 *
	 */

	public final CompletableFuture<GetTasksResponse> get(
			Function<GetTasksRequest.Builder, ObjectBuilder<GetTasksRequest>> fn)
			throws IOException, OpenSearchException {
		return get(fn.apply(new GetTasksRequest.Builder()).build());
	}

	// ----- Endpoint: tasks.list

	/**
	 * Returns a list of tasks.
	 *
	 *
	 */

	public CompletableFuture<ListResponse> list(ListRequest request) throws IOException, OpenSearchException {
		@SuppressWarnings("unchecked")
		JsonEndpoint<ListRequest, ListResponse, ErrorResponse> endpoint =
                (JsonEndpoint<ListRequest, ListResponse, ErrorResponse>) ListRequest._ENDPOINT;

		return this.transport.performRequestAsync(request, endpoint, this.transportOptions);
	}

	/**
	 * Returns a list of tasks.
	 *
	 * @param fn
	 *            a function that initializes a builder to create the
	 *            {@link ListRequest}
	 *
	 */

	public final CompletableFuture<ListResponse> list(Function<ListRequest.Builder, ObjectBuilder<ListRequest>> fn)
			throws IOException, OpenSearchException {
		return list(fn.apply(new ListRequest.Builder()).build());
	}

	/**
	 * Returns a list of tasks.
	 *
	 *
	 */

	public CompletableFuture<ListResponse> list() throws IOException, OpenSearchException {
		return this.transport.performRequestAsync(new ListRequest.Builder().build(), ListRequest._ENDPOINT,
				this.transportOptions);
	}

}
