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
import io.camunda.client.api.command.DeleteRoleCommandStep1;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.DeleteRoleResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.DeleteRoleResponseImpl;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class DeleteRoleCommandImpl implements DeleteRoleCommandStep1 {

  private final String roleId;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public DeleteRoleCommandImpl(final HttpClient httpClient, final String roleId) {
    this.roleId = roleId;
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public FinalCommandStep<DeleteRoleResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<DeleteRoleResponse> send() {
    ArgumentUtil.ensureNotNullNorEmpty("roleId", roleId);
    final HttpCamundaFuture<DeleteRoleResponse> result = new HttpCamundaFuture<>();
    httpClient.delete(
        "/roles/" + roleId, httpRequestConfig.build(), DeleteRoleResponseImpl::new, result);
    return result;
  }
}
