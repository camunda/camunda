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
import org.opensearch.client.opensearch._types.ShardStatistics;
import org.opensearch.client.util.ApiTypeHelper;
import org.opensearch.client.util.ObjectBuilder;
import org.opensearch.client.util.ObjectBuilderBase;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Function;

// typedef: indices.data_streams_stats.Response

@JsonpDeserializable
public class DataStreamsStatsResponse implements JsonpSerializable {

    private final ShardStatistics shards;

    private final int dataStreamCount;

    private final int backingIndices;

    private final long totalStoreSizeBytes;

    @Nullable
    private final String totalStoreSize;

    private final List<DataStreamStats> dataStreams;

    // ---------------------------------------------------------------------------------------------

    private DataStreamsStatsResponse(Builder builder) {

        this.shards = ApiTypeHelper.requireNonNull(builder.shards, this, "shards");
        this.dataStreamCount = ApiTypeHelper.requireNonNull(builder.dataStreamCount, this, "dataStreamCount");
        this.backingIndices = ApiTypeHelper.requireNonNull(builder.backingIndices, this, "backingIndices");
        this.totalStoreSizeBytes = ApiTypeHelper.requireNonNull(builder.totalStoreSizeBytes, this, "totalStoreSizeBytes");
        this.totalStoreSize = builder.totalStoreSize;
        this.dataStreams = ApiTypeHelper.unmodifiableRequired(builder.dataStreams, this, "dataStreams");

    }

    public static DataStreamsStatsResponse of(Function<Builder, ObjectBuilder<DataStreamsStatsResponse>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Required - API name: {@code _shards}
     */
    public final ShardStatistics shards() {
        return this.shards;
    }

    /**
     * Required - API name: {@code data_stream_count}
     */
    public final int dataStreamCount() {
        return this.dataStreamCount;
    }

    /**
     * Required - API name: {@code backing_indices}
     */
    public final int backingIndices() {
        return this.backingIndices;
    }

    /**
     * Required - API name: {@code total_store_size_bytes}
     */
    public final long totalStoreSizeBytes() {
        return this.totalStoreSizeBytes;
    }

    /**
     * API name: {@code total_store_size}
     */
    @Nullable
    public final String totalStoreSize() {
        return this.totalStoreSize;
    }

    /**
     * API name: {@code data_streams}
     */
    public final List<DataStreamStats> dataStreams() {
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

        generator.writeKey("_shards");
        this.shards.serialize(generator, mapper);

        generator.writeKey("data_stream_count");
        generator.write(this.dataStreamCount);

        generator.writeKey("backing_indices");
        generator.write(this.backingIndices);

        generator.writeKey("total_store_size_bytes");
        generator.write(this.totalStoreSizeBytes);

        if (this.totalStoreSize != null) {
            generator.writeKey("total_store_size");
            generator.write(this.totalStoreSize);

        }

        if (ApiTypeHelper.isDefined(this.dataStreams)) {
            generator.writeKey("data_streams");
            generator.writeStartArray();
            for (DataStreamStats item : this.dataStreams) {
                item.serialize(generator, mapper);
            }
            generator.writeEnd();
        }

    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link DataStreamsStatsResponse}.
     */

    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DataStreamsStatsResponse> {

        private ShardStatistics shards;

        private int dataStreamCount;

        private int backingIndices;

        private long totalStoreSizeBytes;

        @Nullable
        private String totalStoreSize;

        private List<DataStreamStats> dataStreams;

        /**
         * Required - API name: {@code _shards}
         */
        public final Builder shards(ShardStatistics value) {
            this.shards = value;
            return this;
        }

        /**
         * Required - API name: {@code _shards}
         */
        public final Builder shards(Function<ShardStatistics.Builder, ObjectBuilder<ShardStatistics>> fn) {
            return this.shards(fn.apply(new ShardStatistics.Builder()).build());
        }

        /**
         * Required - API name: {@code data_stream_count}
         */
        public final Builder dataStreamCount(int value) {
            this.dataStreamCount = value;
            return this;
        }

        /**
         * Required - API name: {@code backing_indices}
         */
        public final Builder backingIndices(int value) {
            this.backingIndices = value;
            return this;
        }

        /**
         * Required - API name: {@code total_store_size_bytes}
         */
        public final Builder totalStoreSizeBytes(int value) {
            this.totalStoreSizeBytes = value;
            return this;
        }

        /**
         * API name: {@code total_store_size}
         */
        public final Builder totalStoreSize(@Nullable String value) {
            this.totalStoreSize = value;
            return this;
        }

        /**
         * API name: {@code data_streams}
         */
        public final Builder dataStreams(List<DataStreamStats> list) {
            this.dataStreams = _listAddAll(this.dataStreams, list);
            return this;
        }

        /**
         * API name: {@code data_streams}
         */
        public final Builder dataStreams(DataStreamStats value, DataStreamStats... values) {
            this.dataStreams = _listAdd(this.dataStreams, value, values);
            return this;
        }

        /**
         * API name: {@code data_streams}
         */
        public final Builder dataStreams(Function<DataStreamStats.Builder, ObjectBuilder<DataStreamStats>> fn) {
            return dataStreams(fn.apply(new DataStreamStats.Builder()).build());
        }

        /**
         * Builds a {@link DataStreamsStatsResponse}.
         *
         * @throws NullPointerException
         *             if some of the required fields are null.
         */
        public DataStreamsStatsResponse build() {
            _checkSingleUse();

            return new DataStreamsStatsResponse(this);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Json deserializer for {@link DataStreamsStatsResponse}
     */
    public static final JsonpDeserializer<DataStreamsStatsResponse> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
            DataStreamsStatsResponse::setupDataStreamStatsResponseDeserializer);

    protected static void setupDataStreamStatsResponseDeserializer(ObjectDeserializer<Builder> op) {

        op.add(Builder::shards, ShardStatistics._DESERIALIZER, "_shards");
        op.add(Builder::dataStreamCount, JsonpDeserializer.integerDeserializer(), "data_stream_count");
        op.add(Builder::backingIndices, JsonpDeserializer.integerDeserializer(), "backing_indices");
        op.add(Builder::totalStoreSizeBytes, JsonpDeserializer.integerDeserializer(), "total_store_size_bytes");
        op.add(Builder::totalStoreSize, JsonpDeserializer.stringDeserializer(), "total_store_size");
        op.add(Builder::dataStreams, JsonpDeserializer.arrayDeserializer(DataStreamStats._DESERIALIZER), "data_streams");

    }

}
