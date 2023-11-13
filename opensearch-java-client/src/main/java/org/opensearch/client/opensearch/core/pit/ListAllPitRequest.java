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

package org.opensearch.client.opensearch.core.pit;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;


/**
 * Lists all PITs on the OpenSearch cluster
 * 
 */
public class ListAllPitRequest {
    public ListAllPitRequest() {

    }

    /**
     * Singleton instance for {@link ListAllPitRequest}.
     */
    public static final ListAllPitRequest _INSTANCE = new ListAllPitRequest();

    public static final Endpoint<ListAllPitRequest, ListAllPitResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(
            // Request method
            request -> {
                return "GET";
            },

            // Request Path
            request -> {
                return "/_search/point_in_time/_all";
            }, SimpleEndpoint.emptyMap(), SimpleEndpoint.emptyMap(), false, ListAllPitResponse._DESERIALIZER);
}
