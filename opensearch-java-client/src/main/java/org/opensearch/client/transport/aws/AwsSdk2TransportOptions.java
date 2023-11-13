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

package org.opensearch.client.transport.aws;

import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.transport.TransportOptions;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;

import java.util.List;
import java.util.function.Function;

public interface AwsSdk2TransportOptions extends TransportOptions {

    /**
     * Get the credentials provider to user for signing requests.
     * <p>
     * If this is null, then a default provider will be used -- either a provider specified
     * in a more general {@link AwsSdk2TransportOptions} that applies to the request, or the
     * default credential chain if there is none.
     * </P>
     *
     * @return A credentials provider or null
     */
    AwsCredentialsProvider credentials();

    /**
     * Get the maximum size for uncompressed requests.  Requests larger than this size will
     * be sent with Content-Encoding: gzip.
     * <p>
     * If this is null, then a default will be used -- either a value specified
     * in a more general {@link AwsSdk2TransportOptions} that applies to the request, or a
     * reasonable default if there is none.
     * </P><P>
     * If this is Integer.MAX_VALUE, then requests will not be compressed.  If this is 0, then all non-empty
     * request bodies will be compressed.
     * </P>
     *
     * @return An integer size limit or null
     */
    Integer requestCompressionSize();

    /**
     * Get the response compression enable/disable value.  If this is true, then an
     * Accept-Encoding: gzip header will be sent with the request.  The server will
     * decide whether or not to compress its responses.
     * <p>
     * If this is null, then a default will be used -- either a value specified
     * in a more general {@link AwsSdk2TransportOptions} that applies to the request, or
     * {@link Boolean#TRUE} if there is none.
     * </P>
     *
     * @return response compression enable/disable flag, or null
     */
    Boolean responseCompression();

    /**
     * Get mapper used for serializing and deserializing requests and responses.
     * <p>
     * If this is null, then a default will be used -- either a value specified
     * in a more general {@link AwsSdk2TransportOptions} that applies to the request, or a
     * new {@link org.opensearch.client.json.jackson.JacksonJsonpMapper} or equivalent if
     * there is none.
     * </P>
     *
     * @return A mapper or null
     */
    JsonpMapper mapper();

    AwsSdk2TransportOptions.Builder toBuilder();

    static AwsSdk2TransportOptions.Builder builder() {
        return new BuilderImpl();
    }

    interface Builder extends TransportOptions.Builder {
        Builder addHeader(String name, String value);

        Builder setParameter(String name, String value);

        Builder onWarnings(Function<List<String>, Boolean> listener);

        Builder setCredentials(AwsCredentialsProvider credentials);

        Builder setRequestCompressionSize(Integer size);

        Builder setResponseCompression(Boolean enabled);

        Builder setMapper(JsonpMapper mapper);

        AwsSdk2TransportOptions build();
    }

    class BuilderImpl extends TransportOptions.BuilderImpl implements Builder {

        protected AwsCredentialsProvider credentials;
        protected Integer requestCompressionSize;
        protected Boolean responseCompression;
        protected JsonpMapper mapper;

        public BuilderImpl() {
        }

        public BuilderImpl(AwsSdk2TransportOptions src) {
            super(src);
            credentials = src.credentials();
            requestCompressionSize = src.requestCompressionSize();
            responseCompression = src.responseCompression();
            mapper = src.mapper();
        }

        @Override
        public Builder addHeader(String name, String value) {
            super.addHeader(name, value);
            return this;
        }

        @Override
        public Builder setParameter(String name, String value) {
            super.setParameter(name, value);
            return this;
        }

        @Override
        public Builder onWarnings(Function<List<String>, Boolean> listener) {
            super.onWarnings(listener);
            return this;
        }

        @Override
        public Builder setCredentials(AwsCredentialsProvider credentials) {
            this.credentials = credentials;
            return this;
        }

        @Override
        public Builder setRequestCompressionSize(Integer size) {
            this.requestCompressionSize = size;
            return this;
        }

        @Override
        public Builder setMapper(JsonpMapper mapper) {
            this.mapper = mapper;
            return this;
        }

        @Override
        public Builder setResponseCompression(Boolean enabled) {
            this.responseCompression = enabled;
            return this;
        }

        @Override
        public AwsSdk2TransportOptions build() {
            return new DefaultImpl(this);
        }
    }

    class DefaultImpl extends TransportOptions.DefaultImpl implements AwsSdk2TransportOptions {

        private AwsCredentialsProvider credentials;
        private Integer requestCompressionSize;
        private Boolean responseCompression;
        private JsonpMapper mapper;

        DefaultImpl(AwsSdk2TransportOptions.BuilderImpl builder) {
            super(builder);
            credentials = builder.credentials;
            requestCompressionSize = builder.requestCompressionSize;
            responseCompression = builder.responseCompression;
            mapper = builder.mapper;
        }

        @Override
        public AwsCredentialsProvider credentials() {
            return credentials;
        }

        @Override
        public Integer requestCompressionSize() {
            return requestCompressionSize;
        }

        @Override
        public Boolean responseCompression() {
            return responseCompression;
        }

        @Override
        public JsonpMapper mapper() {
            return mapper;
        }

        @Override
        public AwsSdk2TransportOptions.Builder toBuilder() {
            return new AwsSdk2TransportOptions.BuilderImpl(this);
        }
    }
}
