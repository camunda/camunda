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

import java.util.function.Function;

// typedef: indices._types.DataStreamIndexInfo
@JsonpDeserializable
public class DataStreamIndexInfo implements JsonpSerializable {

    private final String indexName;

    private final String indexUuid;

    // ---------------------------------------------------------------------------------------------

    private DataStreamIndexInfo(Builder builder) {

        this.indexName = ApiTypeHelper.requireNonNull(builder.indexName, this, "indexName");
        this.indexUuid = ApiTypeHelper.requireNonNull(builder.indexUuid, this, "indexUuid");

    }

    public static DataStreamIndexInfo of(Function<Builder, ObjectBuilder<DataStreamIndexInfo>> fn) {
        return fn.apply(new Builder()).build();
    }

    /**
     * Required - Index name
     * <p>
     * API name: {@code index_name}
     */
    public final String indexName() {
        return this.indexName;
    }

    /**
     * Required - Index uuid
     * <p>
     * API name: {@code index_uuid}
     */
    public final String indexUuid() {
        return this.indexUuid;
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

        generator.writeKey("index_name");
        generator.write(this.indexName);

        generator.writeKey("index_uuid");
        generator.write(this.indexUuid);

    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Builder for {@link DataStreamIndexInfo}.
     */

    public static class Builder extends ObjectBuilderBase implements ObjectBuilder<DataStreamIndexInfo> {
        private String indexName;

        private String indexUuid;

        /**
         * Required - Index name
         * <p>
         * API name: {@code index_name}
         */
        public final Builder indexName(String value) {
            this.indexName = value;
            return this;
        }

        /**
         * Required - Index uuid
         * <p>
         * API name: {@code index_uuid}
         */
        public final Builder indexUuid(String value) {
            this.indexUuid = value;
            return this;
        }

        /**
         * Builds a {@link DataStreamIndexInfo}.
         *
         * @throws NullPointerException
         *             if some of the required fields are null.
         */
        public DataStreamIndexInfo build() {
            _checkSingleUse();

            return new DataStreamIndexInfo(this);
        }
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * Json deserializer for {@link DataStreamIndexInfo}
     */
    public static final JsonpDeserializer<DataStreamIndexInfo> _DESERIALIZER = ObjectBuilderDeserializer.lazy(Builder::new,
            DataStreamIndexInfo::setupDataStreamIndexInfoDeserializer);

    protected static void setupDataStreamIndexInfoDeserializer(ObjectDeserializer<Builder> op) {

        op.add(Builder::indexName, JsonpDeserializer.stringDeserializer(), "index_name");
        op.add(Builder::indexUuid, JsonpDeserializer.stringDeserializer(), "index_uuid");
    }

}
