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
import io.camunda.client.api.command.AssignGroupToRoleCommandStep1;
import io.camunda.client.api.response.AssignGroupToRoleResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class AssignGroupToRoleCommandImpl implements AssignGroupToRoleCommandStep1 {

  private final HttpClient httpClient;
  private final String roleId;
  private final RequestConfig.Builder httpRequestConfig;
  private String groupId;

  public AssignGroupToRoleCommandImpl(final HttpClient httpClient, final String roleId) {
    this.roleId = roleId;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public AssignGroupToRoleCommandStep1 groupId(final String groupId) {
    this.groupId = groupId;
    return this;
  }

  @Override
  public AssignGroupToRoleCommandStep1 requestTimeout(final Duration timeout) {
    httpRequestConfig.setResponseTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<AssignGroupToRoleResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("role", roleId);
    ArgumentUtil.ensureNotNullNorEmpty("groupId", groupId);
    final HttpCamundaFuture<AssignGroupToRoleResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/roles/" + roleId + "/groups/" + groupId,
        null, // No request body needed
        httpRequestConfig.build(),
        result);
    return result;
  }
}
