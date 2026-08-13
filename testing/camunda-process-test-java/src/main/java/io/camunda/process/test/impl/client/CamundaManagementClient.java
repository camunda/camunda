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
import io.camunda.process.test.impl.client.purge.ManagementClusterTopologyResponseDto;
import io.camunda.process.test.impl.client.purge.ManagementClusterTopologyResponseDto.ChangeCompletion;
import io.camunda.process.test.impl.client.purge.MinimalPlannedOperationsResponseDto;
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
import org.apache.hc.core5.net.URIBuilder;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

public final class CamundaManagementClient implements CamundaClockClient {

  private static final String CLOCK_ENDPOINT = "/actuator/clock";
  private static final String CLOCK_ADD_ENDPOINT = "/actuator/clock/add";

  private static final String CLUSTER_TOPOLOGY_ENDPOINT = "/actuator/cluster";
  private static final String CLUSTER_PURGE_ENDPOINT = "/actuator/cluster/purge";
  private static final String PHYSICAL_TENANT_PARAMETER = "physicalTenant";

  private final ObjectMapper objectMapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final CloseableHttpClient httpClient = HttpClients.createDefault();

  private final URI camundaManagementApi;

  private CamundaManagementClient(final URI camundaManagementApi) {
    this.camundaManagementApi = camundaManagementApi;
  }

  public static CamundaManagementClient createClient(final URI camundaManagementApi) {
    return new CamundaManagementClient(camundaManagementApi);
  }

  @Override
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

  @Override
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

  @Override
  public void setTime(final Instant timeToSet) {
    final Instant now = getCurrentTime();
    final Duration timeOffset = Duration.between(now, timeToSet);

    increaseTime(timeOffset);
  }

  @Override
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
    final long changeId = startPurge();

    try {
      Awaitility.await()
          .pollInterval(Duration.ofMillis(250))
          .atMost(timeout)
          .until(() -> isPurgeComplete(changeId));

    } catch (final ConditionTimeoutException e) {
      throw new RuntimeException("Failed to purge the cluster, timeout expired.", e);

    } catch (final Exception e) {
      throw new RuntimeException("Failed to purge the cluster.", e);
    }
  }

  private long startPurge() {
    final HttpPost purgeRequest = new HttpPost(camundaManagementApi + CLUSTER_PURGE_ENDPOINT);

    try {
      return sendRequest(purgeRequest, MinimalPlannedOperationsResponseDto.class).getChangeId();
    } catch (final IOException e) {
      throw new RuntimeException("Failed to initiate cluster purge", e);
    }
  }

  private boolean isPurgeComplete(final long changeId) {
    // Use the management cluster topology (no auth required) to check if the purge operation
    // identified by changeId has completed and the cluster is healthy.
    final ManagementClusterTopologyResponseDto topologyResponse = getClusterTopology(null);
    if (topologyResponse == null) {
      return false;
    }

    final ChangeCompletion completion = topologyResponse.getChangeCompletion(changeId);
    if (completion != ChangeCompletion.NOT_REPORTED) {
      return completion == ChangeCompletion.COMPLETED;
    }

    // A topology covering more than one physical tenant reports no cluster-wide lastChange, so the
    // purge cannot be observed from it. Scoping the request to a single physical tenant restores
    // lastChange; which tenant is asked for does not matter, as lastChange reports the cluster-wide
    // change history either way.
    final String physicalTenantId = topologyResponse.getFirstPhysicalTenantId();
    if (physicalTenantId == null) {
      throw new IllegalStateException(
          "Expected the cluster topology to name the physical tenants it covers, so the purge can be "
              + "observed on one of them, but it named none.");
    }

    final ManagementClusterTopologyResponseDto scopedTopologyResponse =
        getClusterTopology(physicalTenantId);
    return scopedTopologyResponse != null
        && scopedTopologyResponse.getChangeCompletion(changeId) == ChangeCompletion.COMPLETED;
  }

  /**
   * Reads the cluster topology, scoped to {@code physicalTenantId} unless it is {@code null}.
   * Returns {@code null} if the request failed, leaving it to the caller to try again; awaitility
   * will abort after the timeout expires.
   */
  private ManagementClusterTopologyResponseDto getClusterTopology(final String physicalTenantId) {
    try {
      final URIBuilder uriBuilder =
          new URIBuilder(camundaManagementApi + CLUSTER_TOPOLOGY_ENDPOINT);
      if (physicalTenantId != null) {
        uriBuilder.addParameter(PHYSICAL_TENANT_PARAMETER, physicalTenantId);
      }

      return sendRequest(
          new HttpGet(uriBuilder.build()), ManagementClusterTopologyResponseDto.class);
    } catch (final Exception e) {
      return null;
    }
  }

  private <T> T sendRequest(final ClassicHttpRequest request, final Class<T> clazz)
      throws IOException {
    return objectMapper.readValue(sendRequest(request), clazz);
  }

  private String sendRequest(final ClassicHttpRequest request) throws IOException {
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
    return statusCode < 200 || statusCode >= 300;
  }
}
