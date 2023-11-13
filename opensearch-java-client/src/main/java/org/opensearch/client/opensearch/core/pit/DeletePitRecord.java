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

import java.util.function.Function;

import org.opensearch.client.json.JsonpDeserializable;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.ObjectBuilderDeserializer;
import org.opensearch.client.json.ObjectDeserializer;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import jakarta.json.stream.JsonGenerator;

@JsonpDeserializable
public class DeletePitRecord implements JsonpSerializable {

    private final String pitId;

    private final Boolean successful;

    private DeletePitRecord(Builder builder) {
        this.pitId = builder.pitId;
        this.successful = builder.successful;
    }

    public static DeletePitRecord of(Function<Builder, ObjectBuilder<DeletePitRecord>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * API name: {@code pit_id}
     */
    public final String pitId() {
        return this.pitId;
    }

    /**
     * API name: {@code successful}
     */
    public final Boolean successful() {
        return this.successful;
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

        if (this.pitId != null) {
            generator.writeKey("pit_id");
            generator.write(this.pitId);

        }
        if (this.successful != null) {
            generator.writeKey("successful");
            generator.write(this.successful);

        }
    }

    /**
     * Builder for {@link DeletePitRecord}.
     */
    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DeletePitRecord> {
        private String pitId;

        private Boolean successful;

        /**
         * API name: {@code pit_id}
         */
        public final Builder pitId(String pitId) {
            this.pitId = pitId;
            return this;
        }

        /**
         * API name: {@code successful}
         */
        public final Builder successful(Boolean successful) {
            this.successful = successful;
            return this;
        }

        /**
         * Builds a {@link DeletePitRecord}.
         *
         * @throws NullPointerException if some of the required fields are null.
         */
        public DeletePitRecord build() {
            _checkSingleUse();

            return new DeletePitRecord(this);
        }
    }

    /**
     * Json deserializer for {@link DeletePitRecord}
     */
    public static final JsonpDeserializer<DeletePitRecord> _DESERIALIZER = ObjectBuilderDeserializer.lazy(
            Builder::new,
            DeletePitRecord::setupDeletePitRecordDeserializer);

    protected static void setupDeletePitRecordDeserializer(
            ObjectDeserializer<DeletePitRecord.Builder> op) {

        op.add(Builder::pitId, JsonpDeserializer.stringDeserializer(), "pit_id");
        op.add(Builder::successful, JsonpDeserializer.booleanDeserializer(), "successful");

    }
}
