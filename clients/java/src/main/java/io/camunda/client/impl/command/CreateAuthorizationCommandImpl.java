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
import io.camunda.client.api.command.CreateAuthorizationCommandStep1;
import io.camunda.client.api.command.CreateAuthorizationCommandStep1.CreateAuthorizationCommandStep2;
import io.camunda.client.api.command.CreateAuthorizationCommandStep1.CreateAuthorizationCommandStep3;
import io.camunda.client.api.command.CreateAuthorizationCommandStep1.CreateAuthorizationCommandStep4;
import io.camunda.client.api.command.CreateAuthorizationCommandStep1.CreateAuthorizationCommandStep5;
import io.camunda.client.api.command.CreateAuthorizationCommandStep1.CreateAuthorizationCommandStep6;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.CreateAuthorizationResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.CreateAuthorizationResponseImpl;
import io.camunda.client.protocol.rest.AuthorizationCreateResult;
import io.camunda.client.protocol.rest.AuthorizationRequest;
import io.camunda.client.wrappers.OwnerType;
import io.camunda.client.wrappers.PermissionType;
import io.camunda.client.wrappers.ResourceType;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.hc.client5.http.config.RequestConfig;

public class CreateAuthorizationCommandImpl
    implements CreateAuthorizationCommandStep1,
        CreateAuthorizationCommandStep2,
        CreateAuthorizationCommandStep3,
        CreateAuthorizationCommandStep4,
        CreateAuthorizationCommandStep5,
        CreateAuthorizationCommandStep6 {

  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;
  private final AuthorizationRequest request;

  public CreateAuthorizationCommandImpl(final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    request = new AuthorizationRequest();
  }

  @Override
  public CreateAuthorizationCommandStep2 ownerId(final String ownerId) {
    ArgumentUtil.ensureNotNull("ownerId", ownerId);
    ArgumentUtil.ensureNotEmpty("ownerId", ownerId);
    request.setOwnerId(ownerId);
    return this;
  }

  @Override
  public CreateAuthorizationCommandStep3 ownerType(final OwnerType ownerType) {
    ArgumentUtil.ensureNotNull("ownerType", ownerType);
    request.setOwnerType(OwnerType.toProtocolEnum(ownerType));
    return this;
  }

  @Override
  public CreateAuthorizationCommandStep4 resourceId(final String resourceId) {
    ArgumentUtil.ensureNotNull("resourceId", resourceId);
    ArgumentUtil.ensureNotEmpty("resourceId", resourceId);
    request.setResourceId(resourceId);
    return this;
  }

  @Override
  public CreateAuthorizationCommandStep5 resourceType(final ResourceType resourceType) {
    ArgumentUtil.ensureNotNull("resourceType", resourceType);
    request.setResourceType(ResourceType.toProtocolEnum(resourceType));
    return this;
  }

  @Override
  public CreateAuthorizationCommandStep6 permissionTypes(final PermissionType... permissionTypes) {
    ArgumentUtil.ensureNotNull("permissionTypes", permissionTypes);
    request.setPermissionTypes(
        Arrays.stream(permissionTypes)
            .map(PermissionType::toProtocolEnum)
            .collect(Collectors.toList()));
    return this;
  }

  @Override
  public FinalCommandStep<CreateAuthorizationResponse> requestTimeout(
      final Duration requestTimeout) {
    ArgumentUtil.ensurePositive("requestTimeout", requestTimeout);
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<CreateAuthorizationResponse> send() {
    final HttpCamundaFuture<CreateAuthorizationResponse> result = new HttpCamundaFuture<>();
    final CreateAuthorizationResponseImpl response = new CreateAuthorizationResponseImpl();
    httpClient.post(
        "/authorizations",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        AuthorizationCreateResult.class,
        response::setResponse,
        result);
    return result;
  }
}
