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
import io.camunda.client.api.fetch.VariableGetRequest;
import io.camunda.client.api.search.response.Variable;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.VariableImpl;
import io.camunda.client.protocol.rest.VariableResult;

public class VariableGetRequestImpl extends AbstractFetchRequestImpl<Variable>
    implements VariableGetRequest {

  private final HttpClient httpClient;
  private final long variableKey;

  public VariableGetRequestImpl(final HttpClient httpClient, final long variableKey) {
    super(httpClient.newRequestConfig());
    this.httpClient = httpClient;
    this.variableKey = variableKey;
  }

  @Override
  public CamundaFuture<Variable> send() {
    return httpClient.get(
        String.format("/variables/%d", variableKey),
        httpRequestConfig.build(),
        VariableResult.class,
        VariableImpl::new,
        consistencyPolicy);
  }
}
