/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.client.impl.fetch;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.fetch.AuthorizationGetRequest;
import io.camunda.client.api.search.response.Authorization;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.SearchResponseMapper;
import io.camunda.client.protocol.rest.AuthorizationResult;

public class AuthorizationGetRequestImpl extends AbstractFetchRequestImpl<Authorization>
    implements AuthorizationGetRequest {

  private final HttpClient httpClient;
  private final long authorizationKey;

  public AuthorizationGetRequestImpl(final HttpClient httpClient, final long authorizationKey) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.authorizationKey = authorizationKey;
  }

  @Override
  public CamundaFuture<Authorization> send() {
    ArgumentUtil.ensureGreaterThan("authorizationKey", authorizationKey, 0);
    return httpClient.get(
        String.format("/authorizations/%d", authorizationKey),
        httpRequestConfig.build(),
        AuthorizationResult.class,
        SearchResponseMapper::toAuthorizationResponse,
        consistencyPolicy);
  }
}
