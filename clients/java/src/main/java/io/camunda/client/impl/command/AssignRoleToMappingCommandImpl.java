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
import io.camunda.client.api.command.AssignRoleToMappingCommandStep1;
import io.camunda.client.api.command.AssignRoleToMappingCommandStep1.AssignRoleToMappingCommandStep2;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.AssignRoleToMappingResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class AssignRoleToMappingCommandImpl
    implements AssignRoleToMappingCommandStep1, AssignRoleToMappingCommandStep2 {

  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private String roleId;
  private String mappingId;

  public AssignRoleToMappingCommandImpl(final HttpClient httpClient) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignRoleToMappingCommandStep2 roleId(final String roleId) {
    this.roleId = roleId;
    return this;
  }

  @Override
  public AssignRoleToMappingCommandStep2 mappingId(final String mappingId) {
    this.mappingId = mappingId;
    return this;
  }

  @Override
  public FinalCommandStep<AssignRoleToMappingResponse> requestTimeout(final Duration timeout) {
    httpRequestConfig.setResponseTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignRoleToMappingResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    ArgumentUtil.ensureNotNullNorEmpty("mappingRuleId", mappingId);
    final HttpCamundaFuture<AssignRoleToMappingResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/roles/" + roleId + "/mapping-rules/" + mappingId,
        null, // No request body needed
        httpRequestConfig.build(),
        result);
    return result;
  }
}
