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
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.RemovePermissionsCommandStep1;
import io.camunda.zeebe.client.api.command.RemovePermissionsCommandStep1.RemovePermissionsCommandStep2;
import io.camunda.zeebe.client.api.command.RemovePermissionsCommandStep1.RemovePermissionsCommandStep3;
import io.camunda.zeebe.client.api.command.RemovePermissionsCommandStep1.RemovePermissionsCommandStep4;
import io.camunda.zeebe.client.api.response.RemovePermissionsResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest.ActionEnum;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class RemovePermissionsCommandImpl
    implements RemovePermissionsCommandStep1,
        RemovePermissionsCommandStep2,
        RemovePermissionsCommandStep3,
        RemovePermissionsCommandStep4 {

  private final PatchAuthorizationCommand delegate;
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final String path;
  private final RequestConfig.Builder httpRequestConfig;

  public RemovePermissionsCommandImpl(
      final long ownerKey, final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    path = "/authorizations/" + ownerKey;
    httpRequestConfig = httpClient.newRequestConfig();
    delegate = new PatchAuthorizationCommand(ActionEnum.REMOVE);
  }

  @Override
  public RemovePermissionsCommandStep2 resourceType(final ResourceTypeEnum resourceType) {
    delegate.resourceType(resourceType);
    return this;
  }

  @Override
  public RemovePermissionsCommandStep3 permission(final PermissionTypeEnum permissionType) {
    delegate.permission(permissionType);
    return this;
  }

  @Override
  public RemovePermissionsCommandStep4 resourceIds(final List<String> resourceIds) {
    delegate.resourceIds(resourceIds);
    return this;
  }

  @Override
  public RemovePermissionsCommandStep4 resourceId(final String resourceId) {
    delegate.resourceId(resourceId);
    return this;
  }

  @Override
  public FinalCommandStep<RemovePermissionsResponse> requestTimeout(final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<RemovePermissionsResponse> send() {
    final HttpZeebeFuture<RemovePermissionsResponse> result = new HttpZeebeFuture<>();
    httpClient.patch(
        path, jsonMapper.toJson(delegate.getRequest()), httpRequestConfig.build(), result);
    return result;
  }
}
