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
import org.opensearch.client.opensearch._types.HealthStatus;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import java.util.List;
import java.util.function.Function;

// typedef: indices._types.DataStreamInfo

@JsonpDeserializable
public class DataStreamInfo implements JsonpSerializable {
    private final String name;

    private final DataStreamTimestampField timestampField;

    private final List<DataStreamIndexInfo> indices;

    private final int generation;

    private final HealthStatus status;

    private final String template;

    // ---------------------------------------------------------------------------------------------

    private DataStreamInfo(Builder builder) {

        this.name = ApiTypeHelper.requireNonNull(builder.name, this, "name");
        this.timestampField = ApiTypeHelper.requireNonNull(builder.timestampField, this, "timestampField");
        this.indices = ApiTypeHelper.unmodifiableRequired(builder.indices, this, "indices");
        this.generation = ApiTypeHelper.requireNonNull(builder.generation, this, "generation");
        this.status = ApiTypeHelper.requireNonNull(builder.status, this, "status");
        this.template = ApiTypeHelper.requireNonNull(builder.template, this, "template");
    }

    public static DataStreamInfo of(Function<Builder, ObjectBuilder<DataStreamInfo>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Required - data stream name
     * <p>
     * API name: {@code name}
     */
    public final String name() {
        return this.name;
    }

    /**
     * Required - data stream timestamp field
     * <p>
     * API name: {@code timestamp_field}
     */
    public final DataStreamTimestampField timestampField() {
        return this.timestampField;
    }

    /**
     * Required - information about data stream's backing indices
     * <p>
     * API name: {@code indices}
     */
    public final List<DataStreamIndexInfo> indices() {
        return this.indices;
    }

    /**
     * Required - generation
     * <p>
     * API name: {@code generation}
     */
    public final int generation() {
        return this.generation;
    }

    /**
     * Required - health status of the data stream
     * <p>
     * API name: {@code status}
     */
    public final HealthStatus status() {
        return this.status;
    }

    /**
     * Required - index template name used to create the data stream's backing indices
     * <p>
     * API name: {@code template}
     */
    public final String template() {
        return this.template;
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

        generator.writeKey("name");
        generator.write(this.name);

        generator.writeKey("timestamp_field");
        this.timestampField.serialize(generator, mapper);

        if (ApiTypeHelper.isDefined(this.indices)) {
            generator.writeKey("indices");
            generator.writeStartArray();
            for (DataStreamIndexInfo item : this.indices) {
                item.serialize(generator, mapper);
            }
            generator.writeEnd();
        }

        generator.writeKey("generation");
        generator.write(this.generation);

        generator.writeKey("status");
        this.status.serialize(generator, mapper);

        generator.writeKey("template");
        generator.write(this.template);
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link DataStreamInfo}.
     */

    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DataStreamInfo> {
        private String name;

        private DataStreamTimestampField timestampField;

        private List<DataStreamIndexInfo> indices;

        private int generation;

        private HealthStatus status;

        private String template;

        /**
         * Required - data stream name
         * <p>
         * API name: {@code name}
         */
        public final Builder name(String value) {
            this.name = value;
            return this;
        }

        /**
         * Required - data stream timestamp field
         * <p>
         * API name: {@code timestamp_field}
         */
        public final Builder timestampField(DataStreamTimestampField value) {
            this.timestampField = value;
            return this;
        }

        /**
         * Required - data stream timestamp field
         * <p>
         * API name: {@code timestamp_field}
         */
        public final Builder timestampField(Function<DataStreamTimestampField.Builder, ObjectBuilder<DataStreamTimestampField>> fn) {
            return this.timestampField(fn.apply(new DataStreamTimestampField.Builder()).build());
        }

        /**
         * Required - information about data stream's backing indices
         * <p>
         * API name: {@code indices}
         */
        public final Builder indices(List<DataStreamIndexInfo> list) {
            this.indices = _listAddAll(this.indices, list);
            return this;
        }

        /**
         * Required - information about data stream's backing indices
         * <p>
         * API name: {@code indices}
         */
        public final Builder indices(DataStreamIndexInfo value, DataStreamIndexInfo... values) {
            this.indices = _listAdd(this.indices, value, values);
            return this;
        }


        /**
         * Required - generation
         * <p>
         * API name: {@code generation}
         */
        public final Builder generation(int value) {
            this.generation = value;
            return this;
        }

        /**
         * Required - health status of the data stream
         * <p>
         * API name: {@code status}
         */
        public final Builder status(HealthStatus value) {
            this.status = value;
            return this;
        }

        /**
         * Required - index template name used to create the data stream's backing indices
         * <p>
         * API name: {@code template}
         */
        public final Builder template(String value) {
            this.template = value;
            return this;
        }

        /**
         * Builds a {@link DataStreamInfo}.
         *
         * @throws NullPointerException
         *             if some of the required fields are null.
         */
        public DataStreamInfo build() {
            _checkSingleUse();

            return new DataStreamInfo(this);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Json deserializer for {@link DataStreamInfo}
     */
    public static final JsonpDeserializer<DataStreamInfo> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
            DataStreamInfo::setupDataStreamInfoDeserializer);

    protected static void setupDataStreamInfoDeserializer(ObjectDeserializer<Builder> op) {

        op.add(Builder::name, JsonpDeserializer.stringDeserializer(), "name");
        op.add(Builder::timestampField, DataStreamTimestampField._DESERIALIZER, "timestamp_field");
        op.add(Builder::indices, JsonpDeserializer.arrayDeserializer(DataStreamIndexInfo._DESERIALIZER), "indices");
        op.add(Builder::generation, JsonpDeserializer.integerDeserializer(), "generation");
        op.add(Builder::status, HealthStatus._DESERIALIZER, "status");
        op.add(Builder::template, JsonpDeserializer.stringDeserializer(), "template");

    }

}
