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

package org.opensearch.client.transport.endpoints;

import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.transport.JsonEndpoint;

import javax.annotation.Nullable;
import java.util.Map;

public class DelegatingJsonEndpoint<Req, Res, Err> implements JsonEndpoint<Req, Res, Err> {

    protected final JsonEndpoint<Req, Res, Err> endpoint;

    public DelegatingJsonEndpoint(JsonEndpoint<Req, Res, Err> endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String method(Req request) {
        return endpoint.method(request);
    }

    @Override
    public String requestUrl(Req request) {
        return endpoint.requestUrl(request);
    }

    @Override
    public Map<String, String> queryParameters(Req request) {
        return endpoint.queryParameters(request);
    }

    @Override
    public Map<String, String> headers(Req request) {
        return endpoint.headers(request);
    }

    @Override
    public boolean hasRequestBody() {
        return endpoint.hasRequestBody();
    }

    @Override
    @Nullable
    public JsonpDeserializer<Res> responseDeserializer() {
        return endpoint.responseDeserializer();
    }

    @Override
    public boolean isError(int statusCode) {
        return endpoint.isError(statusCode);
    }

    @Override
    @Nullable
    public JsonpDeserializer<Err> errorDeserializer(int statusCode) {
        return endpoint.errorDeserializer(statusCode);
    }
}
