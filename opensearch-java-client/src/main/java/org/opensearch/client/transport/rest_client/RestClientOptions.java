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

/*
 * Licensed to Elasticsearch B.V. under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.client.transport.rest_client;

import org.opensearch.client.transport.TransportOptions;
import org.opensearch.client.transport.Version;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.WarningsHandler;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.opensearch.client.transport.TransportHeaders.ACCEPT;
import static org.opensearch.client.transport.TransportHeaders.USER_AGENT;

public class RestClientOptions implements TransportOptions {

    private final RequestOptions options;

    static RestClientOptions of(TransportOptions options) {
        if (options instanceof RestClientOptions) {
            return (RestClientOptions)options;

        } else {
            final Builder builder = new Builder(RequestOptions.DEFAULT.toBuilder());
            options.headers().forEach(h -> builder.addHeader(h.getKey(), h.getValue()));
            options.queryParameters().forEach(builder::setParameter);
            builder.onWarnings(options.onWarnings());
            return builder.build();
        }
    }

    public RestClientOptions(RequestOptions options) {
        this.options = options;
    }

    public static RestClientOptions.Builder builder() {
        return new Builder(RequestOptions.DEFAULT.toBuilder());
    }

    /**
     * Get the wrapped Rest Client request options
     */
    public RequestOptions restClientRequestOptions() {
        return this.options;
    }

    @Override
    public Collection<Map.Entry<String, String>> headers() {
        return options.getHeaders().stream()
            .map(h -> new AbstractMap.SimpleImmutableEntry<>(h.getName(), h.getValue()))
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> queryParameters() {
        //        TODO - param not available
//        return options.getParameters();
        return null;
    }

    /**
     * Called if there are warnings to determine if those warnings should fail the request.
     */
    @Override
    public Function<List<String>, Boolean> onWarnings() {
        final WarningsHandler handler = options.getWarningsHandler();
        if (handler == null) {
            return null;
        }

        return warnings -> options.getWarningsHandler().warningsShouldFailRequest(warnings);
    }

    @Override
    public Builder toBuilder() {
        return new Builder(options.toBuilder());
    }

    public static class Builder implements TransportOptions.Builder {

        private RequestOptions.Builder builder;

        public Builder(RequestOptions.Builder builder) {
            this.builder = builder;
        }

        /**
         * Get the wrapped Rest Client request options builder.
         */
        public RequestOptions.Builder restClientRequestOptionsBuilder() {
            return this.builder;
        }

        @Override
        public TransportOptions.Builder addHeader(String name, String value) {
            if (name.equalsIgnoreCase(USER_AGENT)) {
                // We must filter out our own user-agent from the options or they'll end up as multiple values for the header
                RequestOptions options = builder.build();
                builder = RequestOptions.DEFAULT.toBuilder();
//                options.getParameters().forEach((k, v) -> builder.addParameter(k, v));
                options.getHeaders().forEach(h -> {
                    if (!h.getName().equalsIgnoreCase(USER_AGENT)) {
                        builder.addHeader(h.getName(), h.getValue());
                    }
                });
                builder.setWarningsHandler(options.getWarningsHandler());
                if (options.getHttpAsyncResponseConsumerFactory() != null) {
                    builder.setHttpAsyncResponseConsumerFactory(options.getHttpAsyncResponseConsumerFactory());
                }
            }
            builder.addHeader(name, value);
            return this;
        }

        @Override
        public TransportOptions.Builder setParameter(String name, String value) {
            //TODO - param not available
//            builder.addParameter(name, value);
            return this;
        }

        /**
         * Called if there are warnings to determine if those warnings should fail the request.
         */
        @Override
        public TransportOptions.Builder onWarnings(Function<List<String>, Boolean> listener) {
            if (listener == null) {
                builder.setWarningsHandler(null);
            } else {
                builder.setWarningsHandler(w -> {
                    if (w != null && !w.isEmpty()) {
                        return listener.apply(w);
                    } else {
                        return false;
                    }
                });
            }

            return this;
        }

        @Override
        public RestClientOptions build() {
            return new RestClientOptions(builder.build());
        }
    }

    static RestClientOptions initialOptions() {
        String ua = String.format(
            Locale.ROOT,
            "opensearch-java/%s (Java/%s)",
            Version.VERSION == null ? "Unknown" : Version.VERSION.toString(),
            System.getProperty("java.version")
        );

        return new RestClientOptions(
            RequestOptions.DEFAULT.toBuilder()
                .addHeader(USER_AGENT, ua)
                .addHeader(ACCEPT, RestClientTransport.JsonContentType.toString())
                .build()
        );
    }
}
