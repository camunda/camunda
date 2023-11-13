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
public class ListAllPitResponse implements JsonpSerializable {
    private final List<PitRecord> pits;

    private ListAllPitResponse(Builder builder) {
        this.pits = ApiTypeHelper.unmodifiableRequired(builder.pits, this, "pits");
    }
    
    public static ListAllPitResponse of(Function<Builder, ObjectBuilder<ListAllPitResponse>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Required - Response value.
     * <p>
     * API name: {@code _value_body}
     */
    public final List<PitRecord> pits() {
        return this.pits;
    }

    /**
     * Serialize this value to JSON.
     */
    public void serialize(JsonGenerator generator, JsonpMapper mapper) {
        generator.writeStartArray();
        for (PitRecord item0 : this.pits) {
            item0.serialize(generator, mapper);

        }
        generator.writeEnd();

    }

    /**
     * Builder for {@link ListAllPitResponse}.
     */

    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<ListAllPitResponse> {
        private List<PitRecord> pits;

        /**
         * Required - Response value.
         * <p>
         * API name: {@code _value_body}
         * <p>
         * Adds all elements of <code>list</code> to <code>pits</code>.
         */
        public final Builder pits(List<PitRecord> list) {
            this.pits = _listAddAll(this.pits, list);
            return this;
        }

        /**
         * Required - Response value.
         * <p>
         * API name: {@code _value_body}
         * <p>
         * Adds one or more values to <code>pits</code>.
         */
        public final Builder pits(PitRecord value, PitRecord... values) {
            this.pits = _listAdd(this.pits, value, values);
            return this;
        }

        /**
         * Required - Response value.
         * <p>
         * API name: {@code _value_body}
         * <p>
         * Adds a value to <code>pits</code> using a builder lambda.
         */
        public final Builder pits(Function<PitRecord.Builder, ObjectBuilder<PitRecord>> fn) {
            return pits(fn.apply(new PitRecord.Builder()).build());
        }

        /**
         * Builds a {@link ListAllPitResponse}.
         *
         * @throws NullPointerException if some of the required fields are null.
         */
        public ListAllPitResponse build() {
            _checkSingleUse();

            return new ListAllPitResponse(this);
        }
    }

    public static final JsonpDeserializer<ListAllPitResponse> _DESERIALIZER = ObjectBuilderDeserializer
            .lazy(Builder::new, ListAllPitResponse::createListAllPitResponseDeserializer);

    protected static  void createListAllPitResponseDeserializer(
            ObjectDeserializer<ListAllPitResponse.Builder> op) {

        JsonpDeserializer<List<PitRecord>> valueDeserializer = JsonpDeserializer
                .arrayDeserializer(PitRecord._DESERIALIZER);
                
        op.add(Builder::pits, valueDeserializer, "pits");
    }
}
