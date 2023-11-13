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

package org.opensearch.client.opensearch.cluster;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.BooleanEndpoint;
import org.opensearch.client.transport.endpoints.BooleanResponse;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;

// typedef: cluster.delete_voting_config_exclusions.Request

/**
 * Clears cluster voting config exclusions.
 * 
 */

public class DeleteVotingConfigExclusionsRequest extends RequestBase {
	@Nullable
	private final Boolean waitForRemoval;

	// ---------------------------------------------------------------------------------------------

	private DeleteVotingConfigExclusionsRequest(Builder builder) {

		this.waitForRemoval = builder.waitForRemoval;

	}

	public static DeleteVotingConfigExclusionsRequest of(
			Function<Builder, ObjectBuilder<DeleteVotingConfigExclusionsRequest>> fn) {
		return fn.apply(new Builder()).build();
	}

	/**
	 * Specifies whether to wait for all excluded nodes to be removed from the
	 * cluster before clearing the voting configuration exclusions list. Defaults to
	 * true, meaning that all excluded nodes must be removed from the cluster before
	 * this API takes any action. If set to false then the voting configuration
	 * exclusions list is cleared even if some excluded nodes are still in the
	 * cluster.
	 * <p>
	 * API name: {@code wait_for_removal}
	 */
	@Nullable
	public final Boolean waitForRemoval() {
		return this.waitForRemoval;
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Builder for {@link DeleteVotingConfigExclusionsRequest}.
	 */

	public static class Builder extends ObjectBuilderBase
			implements
				ObjectBuilder<DeleteVotingConfigExclusionsRequest> {
		@Nullable
		private Boolean waitForRemoval;

		/**
		 * Specifies whether to wait for all excluded nodes to be removed from the
		 * cluster before clearing the voting configuration exclusions list. Defaults to
		 * true, meaning that all excluded nodes must be removed from the cluster before
		 * this API takes any action. If set to false then the voting configuration
		 * exclusions list is cleared even if some excluded nodes are still in the
		 * cluster.
		 * <p>
		 * API name: {@code wait_for_removal}
		 */
		public final Builder waitForRemoval(@Nullable Boolean value) {
			this.waitForRemoval = value;
			return this;
		}

		/**
		 * Builds a {@link DeleteVotingConfigExclusionsRequest}.
		 *
		 * @throws NullPointerException
		 *             if some of the required fields are null.
		 */
		public DeleteVotingConfigExclusionsRequest build() {
			_checkSingleUse();

			return new DeleteVotingConfigExclusionsRequest(this);
		}
	}

	// ---------------------------------------------------------------------------------------------

	/**
	 * Endpoint "{@code cluster.delete_voting_config_exclusions}".
	 */
	public static final Endpoint<DeleteVotingConfigExclusionsRequest, BooleanResponse, ErrorResponse> _ENDPOINT = new BooleanEndpoint<>(
			"opensearch/cluster.delete_voting_config_exclusions",

			// Request method
			request -> {
				return "DELETE";

			},

			// Request path
			request -> {
				return "/_cluster/voting_config_exclusions";

			},

			// Request parameters
			request -> {
				Map<String, String> params = new HashMap<>();
				if (request.waitForRemoval != null) {
					params.put("wait_for_removal", String.valueOf(request.waitForRemoval));
				}
				return params;

			}, SimpleEndpoint.emptyMap(), false, null);
}
