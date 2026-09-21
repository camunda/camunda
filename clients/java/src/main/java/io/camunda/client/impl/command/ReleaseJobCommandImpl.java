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
import io.camunda.client.api.command.ReleaseJobCommandStep1;
import io.camunda.client.api.command.ReleaseJobCommandStep1.ReleaseJobCommandStep2;
import io.camunda.client.api.response.ReleaseJobResponse;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.ReleaseJobResponseImpl;
import io.camunda.client.protocol.rest.JobReleaseRequest;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

public class ReleaseJobCommandImpl implements ReleaseJobCommandStep1, ReleaseJobCommandStep2 {

  private final JobReleaseRequest httpRequestObject = new JobReleaseRequest();
  private final HttpClient httpClient;
  private final RequestConfig.Builder httpRequestConfig;
  private final long jobKey;
  private final JsonMapper jsonMapper;

  public ReleaseJobCommandImpl(
      final long jobKey, final HttpClient httpClient, final JsonMapper jsonMapper) {
    this.jobKey = jobKey;
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
  }

  @Override
  public ReleaseJobCommandStep2 withJobReservationToken(final String jobReservationToken) {
    ArgumentUtil.ensureNotNullNorEmpty("jobReservationToken", jobReservationToken);
    httpRequestObject.setJobReservationToken(jobReservationToken);
    return this;
  }

  @Override
  public FinalCommandStep<ReleaseJobResponse> requestTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
    return this;
  }

  @Override
  public CamundaFuture<ReleaseJobResponse> send() {
    final HttpCamundaFuture<ReleaseJobResponse> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/jobs/" + jobKey + "/release",
        jsonMapper.toJson(httpRequestObject),
        httpRequestConfig.build(),
        ReleaseJobResponseImpl::new,
        result);
    return result;
  }
}
