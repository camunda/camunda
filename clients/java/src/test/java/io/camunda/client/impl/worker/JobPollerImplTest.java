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
package io.camunda.client.impl.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.awaitility.Awaitility;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public final class JobPollerImplTest extends ClientTest {

  private Consumer<ActivatedJob> jobConsumer;
  private IntConsumer doneCallback;
  private Consumer<Throwable> errorCallback;

  @Before
  public void setup() {
    jobConsumer = Mockito.spy(Consumer.class);
    doneCallback = Mockito.spy(IntConsumer.class);
    errorCallback = Mockito.spy(Consumer.class);
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(123);
    final JobPoller jobPoller = getJobPoller(requestTimeout);
    final Duration deadlineOffset = Duration.ofSeconds(10);

    // when
    jobPoller.poll(123, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    rule.verifyRequestTimeout(requestTimeout.plus(deadlineOffset));
  }

  @Test
  public void shouldCallbackWhenPollComplete() {
    // given
    gatewayService.onActivateJobsRequest(TestData.job(), TestData.job());

    // when
    getJobPoller().poll(123, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              verify(jobConsumer, times(2)).accept(any(ActivatedJob.class));
              verify(doneCallback).accept(eq(2));
              verify(errorCallback, never()).accept(any(Throwable.class));
            });
  }

  @Test
  public void shouldCallbackWhenPollFailed() {
    // given
    gatewayService.onActivateJobsRequest(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED));

    // when
    getJobPoller().poll(123, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              verify(jobConsumer, never()).accept(any(ActivatedJob.class));
              verify(doneCallback, never()).accept(any(Integer.class));
              verify(errorCallback).accept(any(StatusRuntimeException.class));
            });
  }

  @Test
  public void shouldUseProvidedTenantFilterWithTenantIds() {
    // given
    gatewayService.onActivateJobsRequest(TestData.job(), TestData.job());
    final List<String> tenantIds = Arrays.asList("tenant-a", "tenant-b");
    final JobPoller poller = getJobPollerWithTenantFilter(tenantIds, TenantFilter.PROVIDED);

    // when
    poller.poll(5, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final GatewayOuterClass.ActivateJobsRequest request = gatewayService.getLastRequest();
              assertThat(request.getTenantFilter())
                  .isEqualTo(GatewayOuterClass.TenantFilter.PROVIDED);
              assertThat(request.getTenantIdsList()).containsExactlyInAnyOrderElementsOf(tenantIds);
            });
  }

  @Test
  public void shouldUseAssignedTenantFilterWithoutTenantIds() {
    // given
    gatewayService.onActivateJobsRequest(TestData.job(), TestData.job());
    final JobPoller poller =
        getJobPollerWithTenantFilter(Collections.emptyList(), TenantFilter.ASSIGNED);

    // when
    poller.poll(5, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final GatewayOuterClass.ActivateJobsRequest request = gatewayService.getLastRequest();
              assertThat(request.getTenantFilter())
                  .isEqualTo(GatewayOuterClass.TenantFilter.ASSIGNED);
              assertThat(request.getTenantIdsList())
                  .describedAs(
                      "Tenant IDs should be empty when ASSIGNED filter is used as they are determined from authorized tenants")
                  .isEmpty();
            });
  }

  @Test
  public void shouldIgnoreTenantIdsWhenUsingAssignedFilter() {
    // given
    gatewayService.onActivateJobsRequest(TestData.job());
    final List<String> tenantIds = Arrays.asList("tenant-a", "tenant-b", "tenant-c");
    final JobPoller poller = getJobPollerWithTenantFilter(tenantIds, TenantFilter.ASSIGNED);

    // when
    poller.poll(3, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final GatewayOuterClass.ActivateJobsRequest request = gatewayService.getLastRequest();
              assertThat(request.getTenantFilter())
                  .isEqualTo(GatewayOuterClass.TenantFilter.ASSIGNED);
              assertThat(request.getTenantIdsList())
                  .describedAs("Provided tenant IDs should be ignored when ASSIGNED filter is used")
                  .isEmpty();
            });
  }

  @Test
  public void shouldDefaultToProvidedTenantFilterWhenNotSpecified() {
    // given
    gatewayService.onActivateJobsRequest(TestData.job());

    // when
    getJobPoller().poll(5, jobConsumer, doneCallback, errorCallback, () -> true);

    // then
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              final GatewayOuterClass.ActivateJobsRequest request = gatewayService.getLastRequest();
              assertThat(request.getTenantFilter())
                  .describedAs("Should default to PROVIDED when not explicitly set")
                  .isEqualTo(GatewayOuterClass.TenantFilter.PROVIDED);
            });
  }

  @Test
  public void shouldConsistentlyUseTenantFilterAcrossMultiplePolls() {
    // given
    gatewayService.onActivateJobsRequest(TestData.job());
    final JobPoller poller =
        getJobPollerWithTenantFilter(Collections.emptyList(), TenantFilter.ASSIGNED);

    // when - poll first time
    poller.poll(2, jobConsumer, doneCallback, errorCallback, () -> true);
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(() -> verify(doneCallback, times(1)).accept(any(Integer.class)));

    final GatewayOuterClass.ActivateJobsRequest firstRequest = gatewayService.getLastRequest();

    // when - poll second time
    gatewayService.onActivateJobsRequest(TestData.job());
    poller.poll(2, jobConsumer, doneCallback, errorCallback, () -> true);

    // then - both requests should use the same tenant filter
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              verify(doneCallback, times(2)).accept(any(Integer.class));
              final GatewayOuterClass.ActivateJobsRequest secondRequest =
                  gatewayService.getLastRequest();
              assertThat(firstRequest.getTenantFilter())
                  .describedAs("First poll should use ASSIGNED filter")
                  .isEqualTo(GatewayOuterClass.TenantFilter.ASSIGNED);
              assertThat(secondRequest.getTenantFilter())
                  .describedAs("Second poll should consistently use ASSIGNED filter")
                  .isEqualTo(GatewayOuterClass.TenantFilter.ASSIGNED);
            });
  }

  private JobPoller getJobPoller() {
    return getJobPoller(Duration.ofSeconds(10));
  }

  private JobPoller getJobPoller(final Duration requestTimeout) {
    return new JobPollerImpl(
        client,
        requestTimeout,
        "testJobType",
        "testWorkerName",
        Duration.ofSeconds(10),
        Collections.emptyList(),
        Collections.singletonList("test-tenant"),
        TenantFilter.PROVIDED,
        10);
  }

  private JobPoller getJobPollerWithTenantFilter(
      final List<String> tenantIds, final TenantFilter tenantFilter) {
    return new JobPollerImpl(
        client,
        Duration.ofSeconds(10),
        "testJobType",
        "testWorkerName",
        Duration.ofSeconds(10),
        Collections.emptyList(),
        tenantIds,
        tenantFilter,
        10);
  }

  private static final class TestData {
    private static GatewayOuterClass.ActivatedJob job() {
      return GatewayOuterClass.ActivatedJob.newBuilder()
          .setKey(12)
          .setType("foo")
          .setProcessInstanceKey(123)
          .setBpmnProcessId("test1")
          .setProcessDefinitionVersion(2)
          .setProcessDefinitionKey(23)
          .setElementId("foo")
          .setElementInstanceKey(23213)
          .setCustomHeaders("{\"version\": \"1\"}")
          .setWorker("worker1")
          .setRetries(34)
          .setDeadline(1231)
          .setVariables("{\"key\": \"val\"}")
          .setTenantId("test-tenant")
          .build();
    }
  }
}
