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
import io.camunda.zeebe.client.api.command.AddPermissionsCommandStep1;
import io.camunda.zeebe.client.api.command.AddPermissionsCommandStep1.AddPermissionsCommandStep2;
import io.camunda.zeebe.client.api.command.AddPermissionsCommandStep1.AddPermissionsCommandStep3;
import io.camunda.zeebe.client.api.command.AddPermissionsCommandStep1.AddPermissionsCommandStep4;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.AddPermissionsResponse;
import io.camunda.zeebe.client.impl.http.HttpClient;
import io.camunda.zeebe.client.impl.http.HttpZeebeFuture;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest.ActionEnum;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequest.ResourceTypeEnum;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequestPermissionsInner;
import io.camunda.zeebe.client.protocol.rest.AuthorizationPatchRequestPermissionsInner.PermissionTypeEnum;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class AddPermissionsCommandImpl
    implements AddPermissionsCommandStep1,
        AddPermissionsCommandStep2,
        AddPermissionsCommandStep3,
        AddPermissionsCommandStep4 {

  private final String path;
  private final AuthorizationPatchRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private AuthorizationPatchRequestPermissionsInner currentPermission;

  public AddPermissionsCommandImpl(
      final long ownerKey, final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AuthorizationPatchRequest().action(ActionEnum.ADD);
    path = "/authorizations/" + ownerKey;
  }

  @Override
  public AddPermissionsCommandStep2 resourceType(final ResourceTypeEnum resourceType) {
    ArgumentUtil.ensureNotNull("resourceType", resourceType);
    request.resourceType(resourceType);
    return this;
  }

  @Override
  public AddPermissionsCommandStep3 permission(final PermissionTypeEnum permissionType) {
    ArgumentUtil.ensureNotNull("permissionType", permissionType);
    currentPermission = new AuthorizationPatchRequestPermissionsInner();
    currentPermission.permissionType(permissionType);
    request.addPermissionsItem(currentPermission);
    return this;
  }

  @Override
  public AddPermissionsCommandStep4 resourceIds(final List<String> resourceIds) {
    ArgumentUtil.ensureNotNullOrEmpty("resourceIds", resourceIds);
    resourceIds.forEach(this::resourceId);
    return this;
  }

  @Override
  public AddPermissionsCommandStep4 resourceId(final String resourceId) {
    ArgumentUtil.ensureNotNullNorEmpty("resourceId", resourceId);
    currentPermission.addResourceIdsItem(resourceId);
    return this;
  }

  @Override
  public FinalCommandStep<AddPermissionsResponse> requestTimeout(final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public ZeebeFuture<AddPermissionsResponse> send() {
    final HttpZeebeFuture<AddPermissionsResponse> result = new HttpZeebeFuture<>();
    httpClient.patch(path, jsonMapper.toJson(request), httpRequestConfig.build(), result);
    return result;
  }
}
