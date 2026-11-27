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

import io.camunda.client.CamundaClientConfiguration;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CreateProbeCommandStep1;
import io.camunda.client.api.command.CreateProbeCommandStep1.CreateProbeCommandStep2;
import io.camunda.client.api.command.CreateProbeCommandStep1.CreateProbeCommandStep3;
import io.camunda.client.api.command.FinalCommandStep;
import io.camunda.client.api.response.ProbeResult;
import io.camunda.client.impl.http.HttpCamundaFuture;
import io.camunda.client.impl.http.HttpClient;
import io.camunda.client.impl.response.ProbeResultImpl;
import io.camunda.client.protocol.rest.ProbeCreationRequest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.client5.http.config.RequestConfig;

/**
 * Sends a probe and waits for the worker's answer. Its HTTP request waits at least one second
 * longer than the cluster, so that the client gets the cluster's answer, including {@code 404
 * NO_WORKER} at the deadline, and not a client-side timeout.
 */
public final class CreateProbeCommandImpl
    implements CreateProbeCommandStep1, CreateProbeCommandStep2, CreateProbeCommandStep3 {

  /**
   * The longest the cluster waits for an answer. Without a timeout, or with 0, the cluster waits
   * its own request timeout, which the client does not know, so the client waits this long.
   */
  static final Duration MAX_REQUEST_TIMEOUT = Duration.ofSeconds(30);

  private static final Duration MIN_REQUEST_TIMEOUT_OFFSET = Duration.ofSeconds(1);

  private final ProbeCreationRequest request = new ProbeCreationRequest();
  private final HttpClient httpClient;
  private final JsonMapper jsonMapper;
  private final RequestConfig.Builder httpRequestConfig;
  private final Duration requestTimeoutOffset;

  public CreateProbeCommandImpl(
      final CamundaClientConfiguration config,
      final HttpClient httpClient,
      final JsonMapper jsonMapper) {
    this.httpClient = httpClient;
    this.jsonMapper = jsonMapper;
    httpRequestConfig = httpClient.newRequestConfig();
    final Duration configuredOffset = config.getDefaultRequestTimeoutOffset();
    requestTimeoutOffset =
        configuredOffset.compareTo(MIN_REQUEST_TIMEOUT_OFFSET) < 0
            ? MIN_REQUEST_TIMEOUT_OFFSET
            : configuredOffset;
    setResponseTimeout(MAX_REQUEST_TIMEOUT);
    tenantId(config.getDefaultTenantId());
  }

  @Override
  public CreateProbeCommandStep2 probeId(final String probeId) {
    ArgumentUtil.ensureNotNullNorEmpty("probeId", probeId);
    request.setProbeId(probeId);
    return this;
  }

  @Override
  public CreateProbeCommandStep3 input(final String expression) {
    ArgumentUtil.ensureNotNullNorEmpty("input", expression);
    request.setInput(expression);
    return this;
  }

  @Override
  public CreateProbeCommandStep3 input(final Map<String, Object> input) {
    return input((Object) input);
  }

  @Override
  public CreateProbeCommandStep3 input(final Object input) {
    ArgumentUtil.ensureNotNull("input", input);
    // a JSON object is a FEEL context of literal values, so no value becomes an expression
    request.setInput("=" + jsonMapper.toJson(input));
    return this;
  }

  @Override
  public CreateProbeCommandStep3 tenantId(final String tenantId) {
    request.setTenantId(tenantId);
    return this;
  }

  @Override
  public FinalCommandStep<ProbeResult> requestTimeout(final Duration requestTimeout) {
    ArgumentUtil.ensureNotNull("requestTimeout", requestTimeout);
    request.setRequestTimeout(requestTimeout.toMillis());
    // the cluster rejects a negative timeout; the request still needs a valid one to get that 400
    setResponseTimeout(
        requestTimeout.isZero() || requestTimeout.isNegative()
            ? MAX_REQUEST_TIMEOUT
            : requestTimeout);
    return this;
  }

  @Override
  public CamundaFuture<ProbeResult> send() {
    final HttpCamundaFuture<ProbeResult> result = new HttpCamundaFuture<>();
    httpClient.post(
        "/probes",
        jsonMapper.toJson(request),
        httpRequestConfig.build(),
        io.camunda.client.protocol.rest.ProbeResult.class,
        ProbeResultImpl::new,
        result);
    return result;
  }

  private void setResponseTimeout(final Duration requestTimeout) {
    httpRequestConfig.setResponseTimeout(
        requestTimeout.plus(requestTimeoutOffset).toMillis(), TimeUnit.MILLISECONDS);
  }
}
