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

import org.opensearch.client.json.DelegatingDeserializer;
import org.opensearch.client.json.JsonpDeserializer;
import org.opensearch.client.json.JsonpMapper;
import org.opensearch.client.transport.JsonEndpoint;
import jakarta.json.stream.JsonParser;

import javax.annotation.Nullable;

/**
 * An endpoint wrapper that adds attributes to the JSON mapper used to deserialize its response.
 */
public class EndpointWithResponseMapperAttr<Req, Res, Err> extends DelegatingJsonEndpoint<Req, Res, Err> {

    private final String attrName;
    private final Object attrValue;

    public EndpointWithResponseMapperAttr(JsonEndpoint<Req, Res, Err> endpoint, String attrName, Object attrValue) {
        super(endpoint);
        this.attrName = attrName;
        this.attrValue = attrValue;
    }

    @Nullable
    @Override
    public JsonpDeserializer<Res> responseDeserializer() {
        return new DelegatingDeserializer.SameType<Res>() {
            @Override
            protected JsonpDeserializer<Res> unwrap() {
                return endpoint.responseDeserializer();
            }

            @Override
            public Res deserialize(JsonParser parser, JsonpMapper mapper) {
                mapper = mapper.withAttribute(attrName, attrValue);
                return super.deserialize(parser, mapper);
            }

            @Override
            public Res deserialize(JsonParser parser, JsonpMapper mapper, JsonParser.Event event) {
                mapper = mapper.withAttribute(attrName, attrValue);
                return super.deserialize(parser, mapper, event);
            }
        };
    }
}
