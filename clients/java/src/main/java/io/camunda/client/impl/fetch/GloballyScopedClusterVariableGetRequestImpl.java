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
import io.camunda.client.api.fetch.GloballyScopedClusterVariableGetRequest;
import io.camunda.client.api.search.response.ClusterVariable;
import io.camunda.client.impl.command.ArgumentUtil;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.search.response.ClusterVariableImpl;
import io.camunda.client.protocol.rest.ClusterVariableResult;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class GloballyScopedClusterVariableGetRequestImpl
    implements GloballyScopedClusterVariableGetRequest {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String name;

  public GloballyScopedClusterVariableGetRequestImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public GloballyScopedClusterVariableGetRequest requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ClusterVariable> send() {
    final HttpCamundaFuture<ClusterVariable> result = new HttpCamundaFuture<>();
    final String path = "/cluster-variables/global/" + name;
    httpClient.get(
        path,
        httpRequestConfig.build(),
        ClusterVariableResult.class,
        ClusterVariableImpl::new,
        result);
    return result;
  }

  @Override
  public GloballyScopedClusterVariableGetRequest withName(final String name) {
    ArgumentUtil.ensureNotNullNorEmpty("name", name);
    this.name = name;
    return this;
  }
}
