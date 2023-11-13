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
public class PitRecord implements JsonpSerializable {

    private final String pitId;

    private final Long creationTime;

    private final Long keepAlive;

    private PitRecord(Builder builder) {
        this.pitId = builder.pitId;
        this.creationTime = builder.creationTime;
        this.keepAlive = builder.keepAlive;
    }

    public static PitRecord of(Function<Builder, ObjectBuilder<PitRecord>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * API name: {@code pit_id}
     */
    public final String pitId() {
        return this.pitId;
    }

    /**
     * API name: {@code creation_time}
     */
    public final Long creationTime() {
        return this.creationTime;
    }

    /**
     * API name: {@code keep_alive}
     */
    public final Long keepAlive() {
        return this.keepAlive;
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
        if (this.creationTime != null) {
            generator.writeKey("creation_time");
            generator.write(this.creationTime);

        }
        if (this.keepAlive != null) {
            generator.writeKey("keep_alive");
            generator.write(this.keepAlive);

        }
    }

    /**
     * Builder for {@link PitRecord}.
     */
    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<PitRecord> {
        private String pitId;

        private Long creationTime;

        private Long keepAlive;

        /**
         * API name: {@code pit_id}
         */
        public final Builder pitId(String pitId) {
            this.pitId = pitId;
            return this;
        }

        /**
         * API name: {@code creation_time}
         */
        public final Builder creationTime(Long creationTime) {
            this.creationTime = creationTime;
            return this;
        }

        /**
         * API name: {@code keep_alive}
         */
        public final Builder keepAlive(Long keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /**
         * Builds a {@link PitRecord}.
         *
         * @throws NullPointerException if some of the required fields are null.
         */
        public PitRecord build() {
            _checkSingleUse();

            return new PitRecord(this);
        }
    }

    /**
     * Json deserializer for {@link PitRecord}
     */
    public static final JsonpDeserializer<PitRecord> _DESERIALIZER = ObjectBuilderDeserializer.lazy(
            Builder::new,
            PitRecord::setupPitRecordDeserializer);

    protected static void setupPitRecordDeserializer(
            ObjectDeserializer<PitRecord.Builder> op) {

        op.add(Builder::pitId, JsonpDeserializer.stringDeserializer(), "pit_id");
        op.add(Builder::creationTime, JsonpDeserializer.longDeserializer(), "creation_time");
        op.add(Builder::keepAlive, JsonpDeserializer.longDeserializer(), "keep_alive");

    }
}
