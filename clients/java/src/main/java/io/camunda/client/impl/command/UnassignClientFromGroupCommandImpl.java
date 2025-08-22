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
package io.camunda.client.impl.command;

import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.UnassignClientFromGroupCommandStep1;
import io.camunda.client.api.command.UnassignClientFromGroupCommandStep1.UnassignClientFromGroupCommandStep2;
import io.camunda.client.api.response.UnassignClientFromGroupResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.UnassignClientFromGroupResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UnassignClientFromGroupCommandImpl
    implements UnassignClientFromGroupCommandStep1, UnassignClientFromGroupCommandStep2 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String clientId;
  private String groupId;

  public UnassignClientFromGroupCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UnassignClientFromGroupCommandStep2 clientId(final String clientId) {
    this.clientId = clientId;
    return this;
  }

  @Override
  public UnassignClientFromGroupCommandStep2 groupId(final String groupId) {
    this.groupId = groupId;
    return this;
  }

  @Override
  public FinalCommandStep<UnassignClientFromGroupResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UnassignClientFromGroupResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("clientId", clientId);
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);
    final HttpCamundaFuture<UnassignClientFromGroupResponse> result = new HttpCamundaFuture<>();
    httpClient.delete(
        "/groups/" + groupId + "/clients/" + clientId,
        null, // No request body needed
        httpRequestConfig.build(),
        UnassignClientFromGroupResponseImpl::new,
        result);
    return result;
  }
}
