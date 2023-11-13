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

import java.util.List;
import java.util.function.Function;

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

@JsonpDeserializable
public class DeletePitResponse implements JsonpSerializable {
    private final List<DeletePitRecord> pits;

    private DeletePitResponse(Builder builder) {
        this.pits = ApiTypeHelper.unmodifiableRequired(builder.pits, this, "pits");
    }
    
    public static DeletePitResponse of(Function<Builder, ObjectBuilder<DeletePitResponse>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Required - Response value.
     * <p>
     * API name: {@code pits}
     */
    public final List<DeletePitRecord> pits() {
        return this.pits;
    }

    /**
     * Serialize this value to JSON.
     */
    public void serialize(JsonGenerator generator, JsonpMapper mapper) {
        generator.writeStartArray();
        for (DeletePitRecord item0 : this.pits) {
            item0.serialize(generator, mapper);

        }
        generator.writeEnd();

    }

    /**
     * Builder for {@link DeletePitResponse}.
     */

    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DeletePitResponse> {
        private List<DeletePitRecord> pits;

        /**
         * Required - Response value.
         * <p>
         * API name: {@code pits}
         * <p>
         * Adds all elements of <code>list</code> to <code>pits</code>.
         */
        public final Builder pits(List<DeletePitRecord> list) {
            this.pits = _listAddAll(this.pits, list);
            return this;
        }

        /**
         * Required - Response value.
         * <p>
         * API name: {@code pits}
         * <p>
         * Adds one or more values to <code>pits</code>.
         */
        public final Builder pits(DeletePitRecord value, DeletePitRecord... values) {
            this.pits = _listAdd(this.pits, value, values);
            return this;
        }

        /**
         * Required - Response value.
         * <p>
         * API name: {@code pits}
         * <p>
         * Adds a value to <code>pits</code> using a builder lambda.
         */
        public final Builder pits(Function<DeletePitRecord.Builder, ObjectBuilder<DeletePitRecord>> fn) {
            return pits(fn.apply(new DeletePitRecord.Builder()).build());
        }

        /**
         * Builds a {@link DeletePitResponse}.
         *
         * @throws NullPointerException if some of the required fields are null.
         */
        public DeletePitResponse build() {
            _checkSingleUse();

            return new DeletePitResponse(this);
        }
    }

    public static final JsonpDeserializer<DeletePitResponse> _DESERIALIZER = ObjectBuilderDeserializer
            .lazy(Builder::new, DeletePitResponse::createDeletePitResponseDeserializer);

    protected static void createDeletePitResponseDeserializer(
            ObjectDeserializer<DeletePitResponse.Builder> op) {

        JsonpDeserializer<List<DeletePitRecord>> valueDeserializer = JsonpDeserializer
                .arrayDeserializer(DeletePitRecord._DESERIALIZER);

        op.add(Builder::pits, valueDeserializer, "pits");
    }
}
