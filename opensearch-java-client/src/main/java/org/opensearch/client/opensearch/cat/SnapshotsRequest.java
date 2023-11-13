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

package org.opensearch.client.opensearch.cat;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// typedef: cat.snapshots.Request

/**
 * Returns all snapshots in a specific repository.
 *
 */

public class SnapshotsRequest extends CatRequestBase {
	@Nullable
	private final Boolean ignoreUnavailable;

	private final List<String> repository;

	// ---------------------------------------------------------------------------------------------

	private SnapshotsRequest(Builder builder) {
		super(builder);
		this.ignoreUnavailable = builder.ignoreUnavailable;
		this.repository = ApiTypeHelper.unmodifiable(builder.repository);

	}

	public static SnapshotsRequest of(Function<Builder, ObjectBuilder<SnapshotsRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Set to true to ignore unavailable snapshots
	 * <p>
	 * API name: {@code ignore_unavailable}
	 */
	@Nullable
	public final Boolean ignoreUnavailable() {
		return this.ignoreUnavailable;
	}

	/**
	 * Name of repository from which to fetch the snapshot information
	 * <p>
	 * API name: {@code repository}
	 */
	public final List<String> repository() {
		return this.repository;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link SnapshotsRequest}.
	 */

	public static class Builder extends CatRequestBaseBuilder<SnapshotsRequest.Builder> {
		@Nullable
		private Boolean ignoreUnavailable;

		@Nullable
		private List<String> repository;

		/**
		 * Set to true to ignore unavailable snapshots
		 * <p>
		 * API name: {@code ignore_unavailable}
		 */
		public final Builder ignoreUnavailable(@Nullable Boolean value) {
			this.ignoreUnavailable = value;
			return this;
		}

		/**
		 * Name of repository from which to fetch the snapshot information
		 * <p>
		 * API name: {@code repository}
		 * <p>
		 * Adds all elements of <code>list</code> to <code>repository</code>.
		 */
		public final Builder repository(List<String> list) {
			this.repository = _listAddAll(this.repository, list);
			return this;
		}

		/**
		 * Name of repository from which to fetch the snapshot information
		 * <p>
		 * API name: {@code repository}
		 * <p>
		 * Adds one or more values to <code>repository</code>.
		 */
		public final Builder repository(String value, String... values) {
			this.repository = _listAdd(this.repository, value, values);
			return this;
		}

		/**
		 * Builds a {@link SnapshotsRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public SnapshotsRequest build() {
			_checkSingleUse();

			return new SnapshotsRequest(this);
		}

		@Override
		protected Builder self() {
			return this;
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code cat.snapshots}".
	 */
	public static final Endpoint<SnapshotsRequest, SnapshotsResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(

			// Request method
			request -> {
				return "GET";

			},

			// Request path
			request -> {
				final int _repository = 1 << 0;

				int propsSet = 0;

				if (ApiTypeHelper.isDefined(request.repository()))
					propsSet |= _repository;

				if (propsSet == 0) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_cat");
					buf.append("/snapshots");
					return buf.toString();
				}
				if (propsSet == (_repository)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_cat");
					buf.append("/snapshots");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.repository.stream().map(v -> v).collect(Collectors.joining(",")),
							buf);
					return buf.toString();
				}
				throw SimpleEndpoint.noPathTemplateFound("path");

			},

			// Request parameters
			request -> {
				Map<String, String> params = new HashMap<>(request.queryParameters());
				if (request.ignoreUnavailable != null) {
					params.put("ignore_unavailable", String.valueOf(request.ignoreUnavailable));
				}
				return params;

			}, SimpleEndpoint.emptyMap(), false, SnapshotsResponse._DESERIALIZER);
}
