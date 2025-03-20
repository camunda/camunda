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
import io.camunda.client.api.command.UpdateGroupCommandStep1;
import io.camunda.client.api.response.UpdateGroupResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.protocol.rest.GroupUpdateRequest;
import io.camunda.client.wrappers.GroupChangeset;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class UpdateGroupCommandImpl implements UpdateGroupCommandStep1 {

  private final long groupKey;
  private final GroupUpdateRequest request;
  private final JsonMapper jsonMapper;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;

  public UpdateGroupCommandImpl(
      final long groupKey, final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.groupKey = groupKey;
    request = new GroupUpdateRequest();
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public UpdateGroupCommandStep1 update(final GroupChangeset groupChangeset) {
    request.setChangeset(GroupChangeset.toProtocolObject(groupChangeset));
    return this;
  }

  @Override
  public UpdateGroupCommandStep1 updateName(final String name) {
    getChangesetEnsureInitialized().setName(name);
    return this;
  }

  @Override
  public FinalCommandStep<UpdateGroupResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateGroupResponse> send() {
    final HttpCamundaFuture<UpdateGroupResponse> result = new HttpCamundaFuture<>();
    httpClient.patch(
        "/groups/" + groupKey, jsonMapper.toJson(request), httpRequestConfig.build(), result);
    return result;
  }

  private io.camunda.client.protocol.rest.GroupChangeset getChangesetEnsureInitialized() {
    io.camunda.client.protocol.rest.GroupChangeset changeset = request.getChangeset();
    if (changeset == null) {
      changeset = new io.camunda.client.protocol.rest.GroupChangeset();
      request.setChangeset(changeset);
    }
    return changeset;
  }
}
