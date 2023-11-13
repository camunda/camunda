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

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.opensearch._types.AcknowledgedResponseBase;
import org.opensearch.client.util.ObjectBuilder;

import java.util.function.Function;

// typedef: indices.create_data_stream.Response

@JsonpDeserializable
public class CreateDataStreamResponse extends AcknowledgedResponseBase {

    // ---------------------------------------------------------------------------------------------

    private CreateDataStreamResponse(Builder builder) {
        super(builder);
    }

    public static CreateDataStreamResponse of(Function<Builder, ObjectBuilder<CreateDataStreamResponse>> fn) {
        return fn.apply(new Builder()).build();
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link CreateDataStreamResponse}
     */
    public static class Builder extends AcknowledgedResponseBase.AbstractBuilder<Builder>
            implements ObjectBuilder<CreateDataStreamResponse> {

        @Override
        protected Builder self() {
            return this;
        }

        /**
         * Builds a {@link CreateDataStreamResponse}.
         *
         * @throws NullPointerException
         *             if any required field is null.
         */
        public CreateDataStreamResponse build() {
            _checkSingleUse();

            return new CreateDataStreamResponse(this);
        }
    }


    // ---------------------------------------------------------------------------------------------

    /**
     * Json deserializer for {@link CreateDataStreamResponse}
     */
    public static final JsonpDeserializer<CreateDataStreamResponse> _DESERIALIZER = ObjectBuilderDeserializer
            .lazy(Builder::new, CreateDataStreamResponse::setupCreateDataStreamResponseDeserializer);

    protected static void setupCreateDataStreamResponseDeserializer(ObjectDeserializer<CreateDataStreamResponse.Builder> op) {
        AcknowledgedResponseBase.setupAcknowledgedResponseBaseDeserializer(op);
    }
}
