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
import io.camunda.client.api.command.JobChangeset;
import io.camunda.client.api.command.UpdateJobCommandStep1;
import io.camunda.client.api.command.UpdateJobCommandStep1.UpdateJobCommandStep2;
import io.camunda.client.api.response.UpdateJobResponse;
import io.camunda.client.impl.RequestMapper;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.protocol.rest.JobUpdateRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class JobUpdateCommandImpl implements UpdateJobCommandStep1, UpdateJobCommandStep2 {

  private final JobUpdateRequest httpRequestObject;
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long jobKey;
  private final JsonMapper jsonMapper;

  public JobUpdateCommandImpl(
      final long jobKey, final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    httpRequestConfig = httpClient.newRequestConfig();
    httpRequestObject = new JobUpdateRequest();
    this.jobKey = jobKey;
    this.jsonMapper = jsonMapper;
  }

  @Override
  public FinalCommandStep<UpdateJobResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<UpdateJobResponse> send() {
    final HttpCamundaFuture<UpdateJobResponse> result = new HttpCamundaFuture<>();
    httpClient.patch(
        "/jobs/" + jobKey, jsonMapper.toJson(httpRequestObject), httpRequestConfig.build(), result);
    return result;
  }

  @Override
  public UpdateJobCommandStep2 update(final JobChangeset jobChangeset) {
    httpRequestObject.setChangeset(RequestMapper.toProtocolObject(jobChangeset));
    return this;
  }

  @Override
  public UpdateJobCommandStep2 update(final Integer retries, final Long timeout) {
    getChangesetEnsureInitialized().retries(retries).timeout(timeout);
    return this;
  }

  @Override
  public UpdateJobCommandStep2 updateRetries(final int retries) {
    getChangesetEnsureInitialized().retries(retries);
    return this;
  }

  @Override
  public UpdateJobCommandStep2 updateTimeout(final long timeout) {
    getChangesetEnsureInitialized().timeout(timeout);
    return this;
  }

  @Override
  public UpdateJobCommandStep2 updateTimeout(final Duration timeout) {
    return updateTimeout(timeout.toMillis());
  }

  private io.camunda.client.protocol.rest.JobChangeset getChangesetEnsureInitialized() {

    io.camunda.client.protocol.rest.JobChangeset changeset = httpRequestObject.getChangeset();
    if (changeset == null) {
      changeset = new io.camunda.client.protocol.rest.JobChangeset();
      httpRequestObject.setChangeset(changeset);
    }
    return changeset;
  }
}
