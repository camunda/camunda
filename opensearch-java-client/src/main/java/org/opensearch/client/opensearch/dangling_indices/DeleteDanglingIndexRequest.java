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

package org.opensearch.client.opensearch.dangling_indices;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.opensearch._types.Time;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: dangling_indices.delete_dangling_index.Request

/**
 * Deletes the specified dangling index
 * 
 */

public class DeleteDanglingIndexRequest extends RequestBase {
	private final boolean acceptDataLoss;

	private final String indexUuid;

	@Deprecated
	@Nullable
	private final Time masterTimeout;

	@Nullable
	private final Time clusterManagerTimeout;

	@Nullable
	private final Time timeout;

	// ---------------------------------------------------------------------------------------------

	private DeleteDanglingIndexRequest(Builder builder) {

		this.acceptDataLoss = ApiTypeHelper.requireNonNull(builder.acceptDataLoss, this, "acceptDataLoss");
		this.indexUuid = ApiTypeHelper.requireNonNull(builder.indexUuid, this, "indexUuid");
		this.masterTimeout = builder.masterTimeout;
		this.clusterManagerTimeout = builder.clusterManagerTimeout;
		this.timeout = builder.timeout;

	}

	public static DeleteDanglingIndexRequest of(Function<Builder, ObjectBuilder<DeleteDanglingIndexRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Required - Must be set to true in order to delete the dangling index
	 * <p>
	 * API name: {@code accept_data_loss}
	 */
	public final boolean acceptDataLoss() {
		return this.acceptDataLoss;
	}

	/**
	 * Required - The UUID of the dangling index
	 * <p>
	 * API name: {@code index_uuid}
	 */
	public final String indexUuid() {
		return this.indexUuid;
	}

	/**
	 * Specify timeout for connection to master
	 * <p>
	 * API name: {@code master_timeout}
	 */
	@Nullable
	public final Time masterTimeout() {
		return this.masterTimeout;
	}

	/**
	 * Specify timeout for connection to cluster-manager
	 * <p>
	 * API name: {@code cluster_manager_timeout}
	 */
	@Nullable
	public final Time clusterManagerTimeout() {
		return this.clusterManagerTimeout;
	}

	/**
	 * Explicit operation timeout
	 * <p>
	 * API name: {@code timeout}
	 */
	@Nullable
	public final Time timeout() {
		return this.timeout;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DeleteDanglingIndexRequest}.
	 */

	public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DeleteDanglingIndexRequest> {
		private Boolean acceptDataLoss;

		private String indexUuid;

		@Deprecated
		@Nullable
		private Time masterTimeout;

		@Nullable
		private Time clusterManagerTimeout;

		@Nullable
		private Time timeout;

		/**
		 * Required - Must be set to true in order to delete the dangling index
		 * <p>
		 * API name: {@code accept_data_loss}
		 */
		public final Builder acceptDataLoss(boolean value) {
			this.acceptDataLoss = value;
			return this;
		}

		/**
		 * Required - The UUID of the dangling index
		 * <p>
		 * API name: {@code index_uuid}
		 */
		public final Builder indexUuid(String value) {
			this.indexUuid = value;
			return this;
		}

		/**
		 * Specify timeout for connection to master
		 * <p>
		 * API name: {@code master_timeout}
		 */
		@Deprecated
		public final Builder masterTimeout(@Nullable Time value) {
			this.masterTimeout = value;
			return this;
		}

		/**
		 * Specify timeout for connection to master
		 * <p>
		 * API name: {@code master_timeout}
		 */
		@Deprecated
		public final Builder masterTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.masterTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Specify timeout for connection to cluster-manager
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(@Nullable Time value) {
			this.clusterManagerTimeout = value;
			return this;
		}

		/**
		 * Specify timeout for connection to cluster-manager
		 * <p>
		 * API name: {@code cluster_manager_timeout}
		 */
		public final Builder clusterManagerTimeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.clusterManagerTimeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Explicit operation timeout
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder timeout(@Nullable Time value) {
			this.timeout = value;
			return this;
		}

		/**
		 * Explicit operation timeout
		 * <p>
		 * API name: {@code timeout}
		 */
		public final Builder timeout(Function<Time.Builder, ObjectBuilder<Time>> fn) {
			return this.timeout(fn.apply(new Time.Builder()).build());
		}

		/**
		 * Builds a {@link DeleteDanglingIndexRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DeleteDanglingIndexRequest build() {
			_checkSingleUse();

			return new DeleteDanglingIndexRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code dangling_indices.delete_dangling_index}".
	 */
	public static final Endpoint<DeleteDanglingIndexRequest, DeleteDanglingIndexResponse, ErrorResponse> _ENDPOINT =
			new SimpleEndpoint<>(

			// Request method
			request -> {
				return "DELETE";

			},

			// Request path
			request -> {
				final int _indexUuid = 1 << 0;

				int propsSet = 0;

				propsSet |= _indexUuid;

				if (propsSet == (_indexUuid)) {
					StringBuilder buf = new StringBuilder();
					buf.append("/_dangling");
					buf.append("/");
					SimpleEndpoint.pathEncode(request.indexUuid, buf);
					return buf.toString();
				}
				throw SimpleEndpoint.noPathTemplateFound("path");

			},

			// Request parameters
			request -> {
				Map<String, String> params = new HashMap<>();
				if (request.masterTimeout != null) {
					params.put("master_timeout", request.masterTimeout._toJsonString());
				}
				if (request.clusterManagerTimeout != null) {
					params.put("cluster_manager_timeout", request.clusterManagerTimeout._toJsonString());
				}
				params.put("accept_data_loss", String.valueOf(request.acceptDataLoss));
				if (request.timeout != null) {
					params.put("timeout", request.timeout._toJsonString());
				}
				return params;

			}, SimpleEndpoint.emptyMap(), false, DeleteDanglingIndexResponse._DESERIALIZER);
}
