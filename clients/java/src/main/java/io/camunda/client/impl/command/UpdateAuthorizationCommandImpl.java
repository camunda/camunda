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
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1.UpdateAuthorizationCommandStep2;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1.UpdateAuthorizationCommandStep3;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1.UpdateAuthorizationCommandStep4;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1.UpdateAuthorizationCommandStep5;
import io.camunda.client.api.command.UpdateAuthorizationCommandStep1.UpdateAuthorizationCommandStep6;
import io.camunda.client.api.response.UpdateAuthorizationResponse;
import io.camunda.client.api.search.enums.OwnerType;
import io.camunda.client.api.search.enums.PermissionType;
import io.camunda.client.api.search.enums.ResourceType;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.util.EnumUtil;
import io.camunda.client.protocol.rest.AuthorizationRequest;
import io.camunda.client.protocol.rest.OwnerTypeEnum;
import io.camunda.client.protocol.rest.PermissionTypeEnum;
import io.camunda.client.protocol.rest.ResourceTypeEnum;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.config.RequestConfig;

public class UpdateAuthorizationCommandImpl
    implements UpdateAuthorizationCommandStep1,
        UpdateAuthorizationCommandStep2,
        UpdateAuthorizationCommandStep3,
        UpdateAuthorizationCommandStep4,
        UpdateAuthorizationCommandStep5,
        UpdateAuthorizationCommandStep6 {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;
  private final AuthorizationRequest request;
  private final long authorizationKey;

  public UpdateAuthorizationCommandImpl(
      final HttpClient httpClient, final JsonMapper jsonMapper, final long authorizationKey) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AuthorizationRequest();
    this.authorizationKey = authorizationKey;
  }

  @Override
  public UpdateAuthorizationCommandStep2 ownerId(final String ownerId) {
    ArgumentUtil.ensureNotNullNorEmpty("ownerId", ownerId);
    request.setOwnerId(ownerId);
    return this;
  }

  @Override
  public UpdateAuthorizationCommandStep3 ownerType(final OwnerType ownerType) {
    ArgumentUtil.ensureNotNull("ownerType", ownerType);
    request.setOwnerType(EnumUtil.convert(ownerType, OwnerTypeEnum.class));
    return this;
  }

  @Override
  public UpdateAuthorizationCommandStep4 resourceId(final String resourceId) {
    ArgumentUtil.ensureNotNullNorEmpty("resourceId", resourceId);
    request.setResourceId(resourceId);
    return this;
  }

  @Override
  public UpdateAuthorizationCommandStep5 resourceType(final ResourceType resourceType) {
    ArgumentUtil.ensureNotNull("resourceType", resourceType);
    request.setResourceType(EnumUtil.convert(resourceType, ResourceTypeEnum.class));
    return this;
  }

  @Override
  public UpdateAuthorizationCommandStep6 permissionTypes(final PermissionType... permissionTypes) {
    ArgumentUtil.ensureNotNull("permissionTypes", permissionTypes);
    request.setPermissionTypes(
        Arrays.stream(permissionTypes)
            .map(permissionType -> EnumUtil.convert(permissionType, PermissionTypeEnum.class))
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public FinalCommandStep<UpdateAuthorizationResponse> requestTimeout(
      final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateAuthorizationResponse> send() {
    final HttpCamundaFuture<UpdateAuthorizationResponse> result = new HttpCamundaFuture<>();
    httpClient.put(
        "/authorizations/" + authorizationKey,
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        result);
    return result;
  }
}
