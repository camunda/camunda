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
import io.camunda.client.api.command.AssignMappingToGroupStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignMappingToGroupResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class AssignMappingToGroupCommandImpl implements AssignMappingToGroupStep1 {

  private final String groupId;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String mappingId;

  public AssignMappingToGroupCommandImpl(final HttpClient httpClient, final String groupId) {
    this.groupId = groupId;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignMappingToGroupStep1 mappingId(final String mappingId) {
    this.mappingId = mappingId;
    return this;
  }

  @Override
  public FinalCommandStep<AssignMappingToGroupResponse> requestTimeout(
      final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignMappingToGroupResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);
    ArgumentUtil.ensureNotNullNorEmpty("mappingId", mappingId);
    final HttpCamundaFuture<AssignMappingToGroupResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/groups/" + groupId + "/mapping-rules/" + mappingId,
        null,
        httpRequestConfig.build(),
        result);
    return result;
  }
}
