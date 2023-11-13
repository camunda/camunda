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
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import javax.annotation.Nullable;
import java.util.function.Function;

// typedef: indices._types.DataStreamTimestampField

@JsonpDeserializable
public class DataStreamTimestampField implements JsonpSerializable {
    @Nullable
    private final String name;

    // ---------------------------------------------------------------------------------------------

    private DataStreamTimestampField(Builder builder) {

        this.name = builder.name;

    }

    public static DataStreamTimestampField of(Function<Builder, ObjectBuilder<DataStreamTimestampField>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * API name: {@code name}
     */
    @Nullable
    public final String name() {
        return this.name;
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

        if (this.name != null) {
            generator.writeKey("name");
            generator.write(this.name);

        }

    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link DataStreamTimestampField}.
     */

    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DataStreamTimestampField> {
        @Nullable
        private String name;

        /**
         * API name: {@code name}
         */
        public final Builder name(@Nullable String value) {
            this.name = value;
            return this;
        }

        /**
         * Builds a {@link DataStreamTimestampField}.
         *
         * @throws NullPointerException
         *             if some of the required fields are null.
         */
        public DataStreamTimestampField build() {
            _checkSingleUse();

            return new DataStreamTimestampField(this);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Json deserializer for {@link DataStreamTimestampField}
     */
    public static final JsonpDeserializer<DataStreamTimestampField> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
            DataStreamTimestampField::setupDataStreamTimestampFieldDeserializer);

    protected static void setupDataStreamTimestampFieldDeserializer(ObjectDeserializer<Builder> op) {

        op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");

    }

}
