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

import jakarta.json.stream.JsonGenerator;
import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import java.util.List;
import java.util.function.Function;

// typedef: indices.get_data_stream.Response

@JsonpDeserializable
public class GetDataStreamResponse implements JsonpSerializable {

    private final List<DataStreamInfo> dataStreams;

    // ---------------------------------------------------------------------------------------------

    private GetDataStreamResponse(Builder builder) {

        this.dataStreams = ApiTypeHelper.unmodifiableRequired(builder.dataStreams, this, "dataStreams");

    }

    public static GetDataStreamResponse of(Function<Builder, ObjectBuilder<GetDataStreamResponse>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * API name: {@code data_streams}
     */
    public final List<DataStreamInfo> dataStreams() {
        return this.dataStreams;
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

        if (ApiTypeHelper.isDefined(this.dataStreams)) {
            generator.writeKey("data_streams");
            generator.writeStartArray();
            for (DataStreamInfo item : this.dataStreams) {
                item.serialize(generator, mapper);
            }
            generator.writeEnd();
        }

    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link GetDataStreamResponse}.
     */

    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<GetDataStreamResponse> {
        private List<DataStreamInfo> dataStreams;

        /**
         * API name: {@code data_streams}
         */
        public final Builder dataStreams(List<DataStreamInfo> list) {
            this.dataStreams = _listAddAll(this.dataStreams, list);
            return this;
        }

        /**
         * Builds a {@link GetDataStreamResponse}.
         *
         * @throws NullPointerException
         *             if some of the required fields are null.
         */
        public GetDataStreamResponse build() {
            _checkSingleUse();

            return new GetDataStreamResponse(this);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Json deserializer for {@link GetDataStreamResponse}
     */
    public static final JsonpDeserializer<GetDataStreamResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
            GetDataStreamResponse::setupGetDataStreamResponseDeserializer);

    protected static void setupGetDataStreamResponseDeserializer(ObjectDeserializer<Builder> op) {

        op.add(Builder::dataStreams, JsonpDeserializer.arrayDeserializer(DataStreamInfo._DESERIALIZER), "data_streams");

    }

}
