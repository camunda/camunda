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

package org.opensearch.client.transport.httpclient5;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.http.nio.AsyncResponseConsumer;
import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.Version;

import static org.opensearch.client.transport.TransportHeaders.ACCEPT;
import static org.opensearch.client.transport.TransportHeaders.USER_AGENT;

public class ApacheHttpClient5Options implements TransportOptions {
    /**
     * Default request options.
     */
    public static final ApacheHttpClient5Options DEFAULT = new Builder(
            Collections.emptyList(),
            HttpAsyncResponseConsumerFactory.HeapBufferedResponseConsumerFactory.DEFAULT,
            null,
            null
        ).build();

    private final List<Header> headers;
    private final HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory;
    private final WarningsHandler warningsHandler;
    private final RequestConfig requestConfig;

    private ApacheHttpClient5Options(Builder builder) {
        this.headers = Collections.unmodifiableList(new ArrayList<>(builder.headers));
        this.httpAsyncResponseConsumerFactory = builder.httpAsyncResponseConsumerFactory;
        this.warningsHandler = builder.warningsHandler;
        this.requestConfig = builder.requestConfig;
    }

    public HttpAsyncResponseConsumerFactory getHttpAsyncResponseConsumerFactory() {
        return httpAsyncResponseConsumerFactory;
    }

    public WarningsHandler getWarningsHandler() {
        return warningsHandler;
    }

    public RequestConfig getRequestConfig() {
        return requestConfig;
    }

    @Override
    public Collection<Entry<String, String>> headers() {
        return headers.stream()
            .map(h -> new AbstractMap.SimpleImmutableEntry<>(h.getName(), h.getValue()))
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> queryParameters() {
        return null;
    }

    @Override
    public Function<List<String>, Boolean> onWarnings() {
        if (warningsHandler == null) {
            return null;
        } else {
            return warnings -> warningsHandler.warningsShouldFailRequest(warnings);
        }
    }

    @Override
    public Builder toBuilder() {
        return new Builder(headers, httpAsyncResponseConsumerFactory, warningsHandler, requestConfig);
    }
    
    public static class Builder implements TransportOptions.Builder {
        private final List<Header> headers;
        private HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory;
        private WarningsHandler warningsHandler;
        private RequestConfig requestConfig;
        
        private Builder(Builder builder) {
            this(builder.headers, builder.httpAsyncResponseConsumerFactory, 
                builder.warningsHandler, builder.requestConfig);
        }

        private Builder(
                List<Header> headers,
                HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory,
                WarningsHandler warningsHandler,
                RequestConfig requestConfig
        ) {
            this.headers = new ArrayList<>(headers);
            this.httpAsyncResponseConsumerFactory = httpAsyncResponseConsumerFactory;
            this.warningsHandler = warningsHandler;
            this.requestConfig = requestConfig;
        }

        /**
         * Add the provided header to the request.
         *
         * @param name  the header name
         * @param value the header value
         * @throws NullPointerException if {@code name} or {@code value} is null.
         */
        @Override
        public Builder addHeader(String name, String value) {
            Objects.requireNonNull(name, "header name cannot be null");
            Objects.requireNonNull(value, "header value cannot be null");
            this.headers.add(new ReqHeader(name, value));
            return this;
        }

        @Override
        public TransportOptions.Builder setParameter(String name, String value) {
            return this;
        }

        /**
         * Called if there are warnings to determine if those warnings should fail the request.
         */
        @Override
        public TransportOptions.Builder onWarnings(Function<List<String>, Boolean> listener) {
            if (listener == null) {
                setWarningsHandler(null);
            } else {
                setWarningsHandler(w -> {
                    if (w != null && !w.isEmpty()) {
                        return listener.apply(w);
                    } else {
                        return false;
                    }
                });
            }

            return this;
        }
        
        /**
         * Set the {@link HttpAsyncResponseConsumerFactory} used to create one
         * {@link AsyncResponseConsumer} callback per retry. Controls how the
         * response body gets streamed from a non-blocking HTTP connection on the
         * client side.
         *
         * @param httpAsyncResponseConsumerFactory factory for creating {@link AsyncResponseConsumer}.
         * @throws NullPointerException if {@code httpAsyncResponseConsumerFactory} is null.
         */
        public void setHttpAsyncResponseConsumerFactory(HttpAsyncResponseConsumerFactory httpAsyncResponseConsumerFactory) {
            this.httpAsyncResponseConsumerFactory = Objects.requireNonNull(
                httpAsyncResponseConsumerFactory,
                "httpAsyncResponseConsumerFactory cannot be null"
            );
        }

        /**
         * How this request should handle warnings. If null (the default) then
         * this request will default to the behavior dictacted by
         * `setStrictDeprecationMode`.
         * <p>
         * This can be set to {@link WarningsHandler#PERMISSIVE} if the client
         * should ignore all warnings which is the same behavior as setting
         * strictDeprecationMode to true. It can be set to
         * {@link WarningsHandler#STRICT} if the client should fail if there are
         * any warnings which is the same behavior as settings
         * strictDeprecationMode to false.
         * <p>
         * It can also be set to a custom implementation of
         * {@linkplain WarningsHandler} to permit only certain warnings or to
         * fail the request if the warnings returned don't
         * <strong>exactly</strong> match some set.
         *
         * @param warningsHandler the {@link WarningsHandler} to be used
         */
        public void setWarningsHandler(WarningsHandler warningsHandler) {
            this.warningsHandler = warningsHandler;
        }

        /**
         * set RequestConfig, which can set socketTimeout, connectTimeout
         * and so on by request
         * @param requestConfig http client RequestConfig
         * @return Builder
         */
        public Builder setRequestConfig(RequestConfig requestConfig) {
            this.requestConfig = requestConfig;
            return this;
        }

        @Override
        public ApacheHttpClient5Options build() {
            return new ApacheHttpClient5Options(this);
        }
    }

    static ApacheHttpClient5Options initialOptions() {
        String ua = String.format(
            Locale.ROOT,
            "opensearch-java/%s (Java/%s)",
            Version.VERSION == null ? "Unknown" : Version.VERSION.toString(),
            System.getProperty("java.version")
        );

        return new ApacheHttpClient5Options(
            DEFAULT.toBuilder()
                .addHeader(USER_AGENT, ua)
                .addHeader(ACCEPT, ApacheHttpClient5Transport.JsonContentType.toString())
        );
    }
    
    static ApacheHttpClient5Options of(TransportOptions options) {
        if (options instanceof ApacheHttpClient5Options) {
            return (ApacheHttpClient5Options)options;

        } else {
            final Builder builder = new Builder(DEFAULT.toBuilder());
            options.headers().forEach(h -> builder.addHeader(h.getKey(), h.getValue()));
            options.queryParameters().forEach(builder::setParameter);
            builder.onWarnings(options.onWarnings());
            return builder.build();
        }
    }
    
    /**
     * Custom implementation of {@link BasicHeader} that overrides equals and
     * hashCode so it is easier to test equality of {@link ApacheHttpClient5Options}.
     */
    static final class ReqHeader extends BasicHeader {
        ReqHeader(String name, String value) {
            super(name, value);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other instanceof ReqHeader) {
                Header otherHeader = (Header) other;
                return Objects.equals(getName(), otherHeader.getName()) && Objects.equals(getValue(), otherHeader.getValue());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(getName(), getValue());
        }
    }
}
