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

package org.opensearch.client.opensearch.core.bulk;

import javax.annotation.Nullable;

import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.json.JsonpSerializable;
import org.opensearch.client.json.JsonpSerializer;
import org.opensearch.client.json.JsonpUtils;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.util.ObjectBuilder;

import jakarta.json.stream.JsonGenerator;

public class UpdateOperationData<TDocument> implements JsonpSerializable {
    @Nullable
    private final TDocument document;

    @Nullable
    private final Boolean docAsUpsert;
    
    @Nullable
    private final TDocument upsert;
    
    @Nullable
    private final Script script;

    @Nullable
    private final JsonpSerializer<TDocument> tDocumentSerializer;

    private UpdateOperationData(Builder<TDocument> builder) {
        this.document = builder.document;
        this.docAsUpsert = builder.docAsUpsert;
        this.script = builder.script;
        this.upsert = builder.upsert;
        this.tDocumentSerializer = builder.tDocumentSerializer;

    }

    @Override
    public void serialize(JsonGenerator generator, JsonpMapper mapper) {
        generator.writeStartObject();
        serializeInternal(generator, mapper);
        generator.writeEnd();
    }

    protected void serializeInternal(JsonGenerator generator, JsonpMapper mapper) {
        if (this.docAsUpsert != null) {
            generator.writeKey("doc_as_upsert");
            generator.write(this.docAsUpsert);
        }

        if (this.document != null) {
            generator.writeKey("doc");
            JsonpUtils.serialize(document, generator, tDocumentSerializer, mapper);
        }

        if (this.upsert != null) {
            generator.writeKey("upsert");
            JsonpUtils.serialize(upsert, generator, tDocumentSerializer, mapper);
        }

        if (this.script != null) {
            generator.writeKey("script");
            this.script.serialize(generator, mapper);
        }
    }

    /**
     * Builder for {@link UpdateOperationData}.
     */
    public static class Builder<TDocument> extends BulkOperationBase.AbstractBuilder<Builder<TDocument>>
            implements
                ObjectBuilder<UpdateOperationData<TDocument>> {
        
        @Nullable
        private TDocument document;

        @Nullable
        private JsonpSerializer<TDocument> tDocumentSerializer;

        @Nullable
        private Boolean docAsUpsert;

        @Nullable
        private TDocument upsert;


        @Nullable
        private Script script;

        /**
         * API name: {@code document}
         */
        public final Builder<TDocument> document(TDocument value) {
            this.document = value;
            return this;
        }


        /**
         * API name: {@code docAsUpsert}
         */
        public final Builder<TDocument> docAsUpsert(@Nullable Boolean value) {
            this.docAsUpsert = value;
            return this;
        }

        /**
         * API name: {@code upsert}
         */
        public final Builder<TDocument> upsert(@Nullable TDocument value) {
            this.upsert = value;
            return this;
        }

        /**
         * API name: {@code script}
         */
        public final Builder<TDocument> script(@Nullable Script value) {
            this.script = value;
            return this;
        }

        /**
         * Serializer for TDocument. If not set, an attempt will be made to find a
         * serializer from the JSON context.
         */
        public final Builder<TDocument> tDocumentSerializer(@Nullable JsonpSerializer<TDocument> value) {
            this.tDocumentSerializer = value;
            return this;
        }

        @Override
        protected Builder<TDocument> self() {
            return this;
        }

        /**
         * Builds a {@link UpdateOperationData}.
         *
         * @throws NullPointerException
         *             if some of the required fields are null.
         */
        public UpdateOperationData<TDocument> build() {
            _checkSingleUse();

            return new UpdateOperationData<TDocument>(this);
        }
    }
}
