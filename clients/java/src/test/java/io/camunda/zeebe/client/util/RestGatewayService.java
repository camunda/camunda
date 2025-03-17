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
package io.camunda.zeebe.client.util;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import io.camunda.client.protocol.rest.DeploymentResult;
import io.camunda.client.protocol.rest.EvaluateDecisionResult;
import io.camunda.client.protocol.rest.JobActivationResult;
import io.camunda.client.protocol.rest.ProblemDetail;
import io.camunda.client.protocol.rest.TopologyResponse;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import java.util.List;
import java.util.function.Supplier;
import org.assertj.core.api.Assertions;

public class RestGatewayService {

  private static final ZeebeObjectMapper JSON_MAPPER = new ZeebeObjectMapper();

  private final WireMockRuntimeInfo mockInfo;

  protected RestGatewayService(final WireMockRuntimeInfo mockInfo) {
    this.mockInfo = mockInfo;
    /*
     * Register a default response to support. Tests that don't need a specific response
     * registration can simply invoke commands and send requests.
     * Otherwise, Wiremock fails if no stubs are registered but a request is sent.
     */
    mockInfo.getWireMock().register(WireMock.any(WireMock.anyUrl()).willReturn(WireMock.ok()));
  }

  /**
   * Register the given response for job activation requests.
   *
   * @param jobActivationResponse the response to provide upon a job activation request
   */
  public void onActivateJobsRequest(final JobActivationResult jobActivationResponse) {
    mockInfo
        .getWireMock()
        .register(
            WireMock.post(RestGatewayPaths.getJobActivationUrl())
                .willReturn(WireMock.okJson(JSON_MAPPER.toJson(jobActivationResponse))));
  }

  /**
   * Register the given response for topology requests.
   *
   * @param topologyResponse the response to provide upon a topology request
   */
  public void onTopologyRequest(final TopologyResponse topologyResponse) {
    mockInfo
        .getWireMock()
        .register(
            WireMock.get(RestGatewayPaths.getTopologyUrl())
                .willReturn(WireMock.okJson(JSON_MAPPER.toJson(topologyResponse))));
  }

  public void onEvaluateDecisionRequest(final EvaluateDecisionResult response) {
    mockInfo
        .getWireMock()
        .register(
            WireMock.post(RestGatewayPaths.getEvaluateDecisionUrl())
                .willReturn(WireMock.okJson(JSON_MAPPER.toJson(response))));
  }

  public void onDeploymentsRequest(final DeploymentResult response) {
    mockInfo
        .getWireMock()
        .register(
            WireMock.post(RestGatewayPaths.getDeploymentsUrl())
                .willReturn(WireMock.okJson(JSON_MAPPER.toJson(response))));
  }

  /**
   * Fetch the last request that was served and convert it to the request target type.
   *
   * @param requestType the Java type to convert the request to
   * @return the last request
   * @param <T> the request type
   */
  public <T> T getLastRequest(final Class<T> requestType) {
    return JSON_MAPPER.fromJson(getLastRequest().getBodyAsString(), requestType);
  }

  /**
   * Fetch the last request that was served. This is a generic {@link LoggedRequest}, provided by
   * the test framework.
   *
   * @return the last logged request
   */
  public static LoggedRequest getLastRequest() {
    final List<ServeEvent> serveEvents = WireMock.getAllServeEvents();
    if (serveEvents.isEmpty()) {
      Assertions.fail("No request was found");
    }
    return serveEvents.get(serveEvents.size() - 1).getRequest();
  }

  /**
   * Register the given error response for a URL. The client will receive a response with the status
   * provided by the given problem detail upon a request to the URL with any HTTP method. If the
   * problem detail does not contain a status, BAD_REQUEST (HTTP status 400) is used.
   *
   * @param url the URL to register the error response for
   * @param problemDetailSupplier the supplier for the error details the client will receive upon a
   *     request
   */
  public void errorOnRequest(
      final String url, final Supplier<ProblemDetail> problemDetailSupplier) {
    final ProblemDetail problemDetail = problemDetailSupplier.get();
    mockInfo
        .getWireMock()
        .register(
            WireMock.any(WireMock.urlEqualTo(url))
                .willReturn(
                    WireMock.jsonResponse(
                            JSON_MAPPER.toJson(problemDetail),
                            problemDetail.getStatus() == null ? 400 : problemDetail.getStatus())
                        .withHeader("Content-Type", "application/problem+json")));
  }
}
