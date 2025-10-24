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
package io.camunda.process.test.impl.client;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.process.test.impl.client.clock.CamundaAddClockRequestDto;
import io.camunda.process.test.impl.client.clock.CamundaClockResponseDto;
import io.camunda.process.test.impl.client.purge.MinimalPlannedOperationsResponseDto;
import io.camunda.process.test.impl.client.purge.MinimalTopologyResponseDto;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.HttpEntities;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public final class CamundaManagementClient {

  private static final String CLOCK_ENDPOINT = "/actuator/clock";
  private static final String CLOCK_ADD_ENDPOINT = "/actuator/clock/add";

  private static final String TOPOLOGY_ENDPOINT = "/v2/topology";
  private static final String CLUSTER_PURGE_ENDPOINT = "/actuator/cluster/purge";

  private final ObjectMapper objectMapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final CloseableHttpClient httpClient = HttpClients.createDefault();

  private final URI camundaManagementApi;
  private final URI camundaRestApi;
  private final String basicAuthCredentials;

  private CamundaManagementClient(final URI camundaManagementApi, final URI camundaRestApi) {
    this(camundaManagementApi, camundaRestApi, null);
  }

  private CamundaManagementClient(
      final URI camundaManagementApi, final URI camundaRestApi, final String basicAuthCredentials) {

    this.camundaManagementApi = camundaManagementApi;
    this.camundaRestApi = camundaRestApi;
    this.basicAuthCredentials = basicAuthCredentials;
  }

  public static CamundaManagementClient createAuthenticatedClient(
      final URI camundaManagementApi, final URI camundaRestApi, final String basicAuthCredentials) {
    return new CamundaManagementClient(camundaManagementApi, camundaRestApi, basicAuthCredentials);
  }

  public static CamundaManagementClient createClient(
      final URI camundaManagementApi, final URI camundaRestApi) {
    return new CamundaManagementClient(camundaManagementApi, camundaRestApi);
  }

  public Instant getCurrentTime() {
    try {
      final HttpGet request = new HttpGet(camundaManagementApi + CLOCK_ENDPOINT);
      final CamundaClockResponseDto clockResponseDto =
          sendRequest(request, CamundaClockResponseDto.class);

      return Instant.parse(clockResponseDto.getInstant());
    } catch (final Exception e) {
      throw new RuntimeException(timerBasedErrorMessage(e), e);
    }
  }

  public void increaseTime(final Duration timeToAdd) {
    final HttpPost request = new HttpPost(camundaManagementApi + CLOCK_ADD_ENDPOINT);

    final CamundaAddClockRequestDto requestDto = new CamundaAddClockRequestDto();
    requestDto.setOffsetMilli(timeToAdd.toMillis());

    try {
      final String requestBody = objectMapper.writeValueAsString(requestDto);
      request.setEntity(HttpEntities.create(requestBody, ContentType.APPLICATION_JSON));

      sendRequest(request);
    } catch (final Exception e) {
      throw new RuntimeException(timerBasedErrorMessage(e), e);
    }
  }

  public void setTime(final Instant timeToSet) {
    final Instant now = getCurrentTime();
    final Duration timeOffset = Duration.between(now, timeToSet);

    increaseTime(timeOffset);
  }

  public void resetTime() {
    final HttpDelete request = new HttpDelete(camundaManagementApi + CLOCK_ENDPOINT);

    try {
      sendRequest(request);
    } catch (final Exception e) {
      throw new RuntimeException(timerBasedErrorMessage(e), e);
    }
  }

  private String timerBasedErrorMessage(final Exception e) {
    return e.getMessage().contains("code: " + HttpStatus.SC_FORBIDDEN)
        ? "Failed to increase the time. Please double-check that the Clock endpoint is enabled by "
            + "setting 'zeebe-clock-controlled' to true."
        : "Failed to increase the time.";
  }

  /**
   * Purges all data from the cluster and all exporters. During this time, no other cluster
   * operations can be attempted and will automatically fail. Since purge is an asynchronous
   * operation, purgeCluster will wait until it receives an update indicating the purge is complete.
   * Default timeout of thirty seconds.
   *
   * @throws RuntimeException if the timeout expired or a request completed with a non-2XX status
   *     code
   */
  public void purgeCluster() {
    purgeCluster(Duration.ofSeconds(30));
  }

  /**
   * Purges all data from the cluster and all exporters. During this time, no other cluster
   * operations can be attempted and will automatically fail. Since purge is an asynchronous
   * operation, purgeCluster will wait until it receives an update indicating the purge is complete.
   *
   * @param timeout time until the purge completes exceptionally
   * @throws RuntimeException if the timeout expired or a request completed with a non-2XX status
   *     code
   */
  public void purgeCluster(final Duration timeout) {
    final MinimalPlannedOperationsResponseDto startPurgeResponse = startPurge();

    try {
      Awaitility.await()
          .pollInterval(Duration.ofMillis(250))
          .atMost(timeout)
          .until(() -> isPurgeComplete(startPurgeResponse.getChangeId()));

    } catch (final ConditionTimeoutException e) {
      throw new RuntimeException("Failed to purge the cluster, timeout expired.", e);

    } catch (final Exception e) {
      throw new RuntimeException("Failed to purge the cluster.", e);
    }
  }

  private MinimalPlannedOperationsResponseDto startPurge() {
    final HttpPost purgeRequest = new HttpPost(camundaManagementApi + CLUSTER_PURGE_ENDPOINT);

    try {
      return sendRequest(purgeRequest, MinimalPlannedOperationsResponseDto.class);
    } catch (final IOException e) {
      throw new RuntimeException("Failed to initiate cluster purge", e);
    }
  }

  private boolean isPurgeComplete(final long changeId) {
    final HttpGet clusterStatusRequest = new HttpGet(camundaRestApi + TOPOLOGY_ENDPOINT);

    try {
      final MinimalTopologyResponseDto minimalTopologyResponse =
          sendRequest(clusterStatusRequest, MinimalTopologyResponseDto.class);

      return minimalTopologyResponse.isTopologyChangeCompleted(changeId);
    } catch (final IOException e) {
      // Ignore silently and wait for next status request; awaitility will abort after timeout
      // expires
      return false;
    }
  }

  private <T> T sendRequest(final ClassicHttpRequest request, final Class<T> clazz)
      throws IOException {
    return objectMapper.readValue(sendRequest(request), clazz);
  }

  private String sendRequest(final ClassicHttpRequest request) throws IOException {
    if (basicAuthCredentials != null) {
      request.setHeader("Authorization", "Basic " + basicAuthCredentials);
    }

    return httpClient.execute(
        request,
        response -> {
          if (isNotSuccessfulStatusCode(response.getCode())) {
            throw new RuntimeException(
                String.format(
                    "Request failed. [code: %d, message: %s]",
                    response.getCode(), HttpClientUtil.getResponseAsString(response)));
          }
          return HttpClientUtil.getResponseAsString(response);
        });
  }

  private boolean isNotSuccessfulStatusCode(final int statusCode) {
    /*
     * Multi-tenancy uses basic auth to secure the camunda gateway. During the purge, the default
     * user is briefly deleted and previously authenticated requests will fail until the user's
     * been recreated. Therefore, we choose to silently ignore 401 errors.
     */
    return statusCode < 200 || (statusCode >= 300 && statusCode != 401);
  }
}
