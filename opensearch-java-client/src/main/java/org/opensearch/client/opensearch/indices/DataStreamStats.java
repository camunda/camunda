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

import javax.annotation.Nullable;
import java.util.function.Function;

// typedef: indices._types.DataStreamStats

@JsonpDeserializable
public class DataStreamStats implements JsonpSerializable {

    private final String dataStream;

    private final int backingIndices;

    private final long storeSizeBytes;

    private final long maximumTimestamp;

    @Nullable
    private final String storeSize;

    // ---------------------------------------------------------------------------------------------

    private DataStreamStats(Builder builder) {

        this.dataStream = ApiTypeHelper.requireNonNull(builder.dataStream, this, "dataStream");
        this.backingIndices = ApiTypeHelper.requireNonNull(builder.backingIndices, this, "backingIndices");
        this.storeSizeBytes = ApiTypeHelper.requireNonNull(builder.storeSizeBytes, this, "storeSizeBytes");
        this.maximumTimestamp = ApiTypeHelper.requireNonNull(builder.maximumTimestamp, this, "maximumTimestamp");
        this.storeSize = builder.storeSize;

    }

    public static DataStreamStats of(Function<Builder, ObjectBuilder<DataStreamStats>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * API name: {@code data_stream}
     */
    public final String dataStream() {
        return this.dataStream;
    }

    /**
     * API name: {@code backing_indices}
     */
    public final int backingIndices() {
        return this.backingIndices;
    }

    /**
     * API name: {@code store_size_bytes}
     */
    public final long storeSizeBytes() {
        return this.storeSizeBytes;
    }

    /**
     * API name: {@code maximum_timestamp}
     */
    public final long maximumTimestamp() {
        return this.maximumTimestamp;
    }

    /**
     * API name: {@code store_size}
     */
    @Nullable
    public final String storeSize() {
        return this.storeSize;
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

        generator.writeKey("data_stream");
        generator.write(this.dataStream);

        generator.writeKey("backing_indices");
        generator.write(this.backingIndices);

        generator.writeKey("store_size_bytes");
        generator.write(this.storeSizeBytes);

        generator.writeKey("maximum_timestamp");
        generator.write(this.maximumTimestamp);

        if (this.storeSize != null) {
            generator.writeKey("store_size");
            generator.write(this.storeSize);

        }

    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link DataStreamStats}.
     */

    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DataStreamStats> {

        private String dataStream;

        private int backingIndices;

        private long storeSizeBytes;

        private long maximumTimestamp;

        @Nullable
        private String storeSize;

        /**
         * API name: {@code data_stream}
         */
        public final Builder dataStream(String value) {
            this.dataStream = value;
            return this;
        }

        /**
         * API name: {@code backing_indices}
         */
        public final Builder backingIndices(int value) {
            this.backingIndices = value;
            return this;
        }

        /**
         * API name: {@code store_size_bytes}
         */
        public final Builder storeSizeBytes(long value) {
            this.storeSizeBytes = value;
            return this;
        }

        /**
         * API name: {@code maximum_timestamp}
         */
        public final Builder maximumTimestamp(long value) {
            this.maximumTimestamp = value;
            return this;
        }

        /**
         * API name: {@code store_size}
         */
        public final Builder storeSize(@Nullable String value) {
            this.storeSize = value;
            return this;
        }

        /**
         * Builds a {@link DataStreamStats}.
         *
         * @throws NullPointerException
         *             if some of the required fields are null.
         */
        public DataStreamStats build() {
            _checkSingleUse();

            return new DataStreamStats(this);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Json deserializer for {@link DataStreamStats}
     */
    public static final JsonpDeserializer<DataStreamStats> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
            DataStreamStats::setupDataStreamStatsDeserializer);

    protected static void setupDataStreamStatsDeserializer(ObjectDeserializer<Builder> op) {

        op.add(Builder::dataStream, JsonpDeserializer.stringDeserializer(), "data_stream");
        op.add(Builder::backingIndices, JsonpDeserializer.integerDeserializer(), "backing_indices");
        op.add(Builder::storeSize, JsonpDeserializer.stringDeserializer(), "store_size");
        op.add(Builder::storeSizeBytes, JsonpDeserializer.longDeserializer(), "store_size_bytes");
        op.add(Builder::maximumTimestamp, JsonpDeserializer.longDeserializer(), "maximum_timestamp");
    }

}
