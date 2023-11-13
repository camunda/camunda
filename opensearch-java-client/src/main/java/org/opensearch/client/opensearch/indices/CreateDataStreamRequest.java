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

package org.opensearch.client.opensearch.indices;

import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.RequestBase;
import org.opensearch.client.transport.Endpoint;
import org.opensearch.client.transport.endpoints.SimpleEndpoint;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import java.util.function.Function;

// typedef: indices.create_data_stream.Request

/**
 * Creates a data stream
 */
public class CreateDataStreamRequest extends RequestBase {

    private final String name;

    // ---------------------------------------------------------------------------------------------

    private CreateDataStreamRequest(Builder builder) {
        this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
    }

    public static CreateDataStreamRequest of(Function<Builder, ObjectBuilder<CreateDataStreamRequest>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Required - The name of the data stream
     * <p>
     * API name: {@code name}
     */
    public final String name() {
        return this.name;
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link CreateDataStreamRequest}.
     */
    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<CreateDataStreamRequest> {

        private String name;

        /**
         * Required - The name of the data stream
         * <p>
         * API name: {@code name}
         */
        public final Builder name(String name) {
            this.name = name;
            return this;
        }

        public CreateDataStreamRequest build() {
            _checkSingleUse();

            return new CreateDataStreamRequest(this);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Endpoint "{@code indices.create_data_stream}".
     */
    public static final Endpoint<CreateDataStreamRequest, CreateDataStreamResponse, ErrorResponse> _ENDPOINT = new SimpleEndpoint<>(
            // Request method
            request -> {
                return "PUT";
            },

            // Request path
            request -> {
                final int _name = 1 << 0;
                int propsSet = 0;
                propsSet |= _name;

                if (propsSet == (_name)) {
                    StringBuilder sbd = new StringBuilder();
                    sbd.append("/_data_stream");
                    sbd.append("/");
                    SimpleEndpoint.pathEncode(request.name, sbd);
                    return sbd.toString();
                }
                throw SimpleEndpoint.noPathTemplateFound("path");
            },

            // Request parameters
            SimpleEndpoint.emptyMap(),

            SimpleEndpoint.emptyMap(), false, CreateDataStreamResponse._DESERIALIZER);
}
