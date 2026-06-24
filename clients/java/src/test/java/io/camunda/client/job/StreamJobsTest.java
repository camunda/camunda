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
package io.camunda.client.job;

import static io.camunda.client.util.JsonUtil.fromJsonAsMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.command.StreamJobsCommandStep1.StreamJobsCommandStep3;
import io.camunda.client.api.command.enums.TenantFilter;
import io.camunda.client.api.response.StreamJobsResponse;
import io.camunda.client.util.ClientTest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Test;
import org.mockito.Mockito;

public final class StreamJobsTest extends ClientTest {
  @Test
  public void shouldStreamJobs() {
    // given
    final List<io.camunda.client.api.response.ActivatedJob> receivedJobs = new ArrayList<>();
    final ActivatedJob activatedJob1 =
        ActivatedJob.newBuilder()
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
            .setTenantId("test-tenant-1")
            .build();
    final ActivatedJob activatedJob2 =
        ActivatedJob.newBuilder()
            .setKey(42)
            .setType("foo")
            .setProcessInstanceKey(333)
            .setBpmnProcessId("test3")
            .setProcessDefinitionVersion(23)
            .setProcessDefinitionKey(11)
            .setElementId("bar")
            .setElementInstanceKey(111)
            .setCustomHeaders("{\"key\": \"value\"}")
            .setWorker("worker1")
            .setRetries(334)
            .setDeadline(3131)
            .setVariables("{\"bar\": 3}")
            .setTenantId("test-tenant-2")
            .build();
    gatewayService.onStreamJobsRequest(activatedJob1, activatedJob2);

    // when
    final StreamJobsResponse response =
        client
            .newStreamJobsCommand()
            .jobType("foo")
            .consumer(receivedJobs::add)
            .timeout(Duration.ofMillis(1000))
            .workerName("worker1")
            .tenantIds("test-tenant-1", "test-tenant-2")
            .send()
            .join();

    // then - we can more easily compare the results of both jobs by serializing them to JSON
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();

    assertThat(request.getType()).isEqualTo("foo");
    assertThat(request.getTimeout()).isEqualTo(1000);
    assertThat(request.getWorker()).isEqualTo("worker1");

    assertThat(response).isNotNull();
    assertThat(receivedJobs).hasSize(2);

    io.camunda.client.api.response.ActivatedJob job = receivedJobs.get(0);
    assertThat(job.getKey()).isEqualTo(activatedJob1.getKey());
    assertThat(job.getType()).isEqualTo(activatedJob1.getType());
    assertThat(job.getBpmnProcessId()).isEqualTo(activatedJob1.getBpmnProcessId());
    assertThat(job.getElementId()).isEqualTo(activatedJob1.getElementId());
    assertThat(job.getElementInstanceKey()).isEqualTo(activatedJob1.getElementInstanceKey());
    assertThat(job.getProcessDefinitionVersion())
        .isEqualTo(activatedJob1.getProcessDefinitionVersion());
    assertThat(job.getProcessDefinitionKey()).isEqualTo(activatedJob1.getProcessDefinitionKey());
    assertThat(job.getProcessInstanceKey()).isEqualTo(activatedJob1.getProcessInstanceKey());
    // rootProcessInstanceKey is only returned for REST API
    assertThat(job.getRootProcessInstanceKey()).isNull();
    assertThat(job.getCustomHeaders()).isEqualTo(fromJsonAsMap(activatedJob1.getCustomHeaders()));
    assertThat(job.getWorker()).isEqualTo(activatedJob1.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob1.getRetries());
    assertThat(job.getDeadline()).isEqualTo(activatedJob1.getDeadline());
    assertThat(job.getVariables()).isEqualTo(activatedJob1.getVariables());
    assertThat(job.getTenantId()).isEqualTo(activatedJob1.getTenantId());

    job = receivedJobs.get(1);
    assertThat(job.getKey()).isEqualTo(activatedJob2.getKey());
    assertThat(job.getType()).isEqualTo(activatedJob2.getType());
    assertThat(job.getBpmnProcessId()).isEqualTo(activatedJob2.getBpmnProcessId());
    assertThat(job.getElementId()).isEqualTo(activatedJob2.getElementId());
    assertThat(job.getElementInstanceKey()).isEqualTo(activatedJob2.getElementInstanceKey());
    assertThat(job.getProcessDefinitionVersion())
        .isEqualTo(activatedJob2.getProcessDefinitionVersion());
    assertThat(job.getProcessDefinitionKey()).isEqualTo(activatedJob2.getProcessDefinitionKey());
    assertThat(job.getProcessInstanceKey()).isEqualTo(activatedJob2.getProcessInstanceKey());
    // rootProcessInstanceKey is only returned for REST API
    assertThat(job.getRootProcessInstanceKey()).isNull();
    assertThat(job.getCustomHeaders()).isEqualTo(fromJsonAsMap(activatedJob2.getCustomHeaders()));
    assertThat(job.getWorker()).isEqualTo(activatedJob2.getWorker());
    assertThat(job.getRetries()).isEqualTo(activatedJob2.getRetries());
    assertThat(job.getDeadline()).isEqualTo(activatedJob2.getDeadline());
    assertThat(job.getVariables()).isEqualTo(activatedJob2.getVariables());
    assertThat(job.getTenantId()).isEqualTo(activatedJob2.getTenantId());
  }

  @Test
  public void shouldSetTimeoutFromDuration() {
    // given
    final Duration timeout = Duration.ofMinutes(2);

    // when
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .timeout(timeout)
        .send()
        .join();

    // then
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getTimeout()).isEqualTo(timeout.toMillis());
  }

  @Test
  public void shouldSetFetchVariables() {
    // given
    final List<String> fetchVariables = Arrays.asList("foo", "bar", "baz");

    // when
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .fetchVariables(fetchVariables)
        .send()
        .join();

    // then
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getFetchVariableList()).containsExactlyInAnyOrderElementsOf(fetchVariables);
  }

  @Test
  public void shouldSetFetchVariablesAsVargs() {
    // given
    final String[] fetchVariables = new String[] {"foo", "bar", "baz"};

    // when
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .fetchVariables(fetchVariables)
        .send()
        .join();

    // then
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getFetchVariableList()).containsExactlyInAnyOrder(fetchVariables);
  }

  @Test
  public void shouldSetDefaultValues() {
    // when
    client.newStreamJobsCommand().jobType("foo").consumer(ignored -> {}).send().join();

    // then
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getTimeout())
        .isEqualTo(client.getConfiguration().getDefaultJobTimeout().toMillis());
    assertThat(request.getWorker()).isEqualTo(client.getConfiguration().getDefaultJobWorkerName());
    assertThat(request.getFetchVariableList()).isEmpty();
  }

  @Test
  public void shouldRaiseExceptionOnError() {
    // given
    gatewayService.errorOnRequest(
        StreamActivatedJobsRequest.class, () -> new ClientException("Invalid request"));

    // when
    assertThatThrownBy(
            () ->
                client.newStreamJobsCommand().jobType("foo").consumer(ignored -> {}).send().join())
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("Invalid request");
  }

  @Test
  public void shouldSetWorker() {
    // when
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .workerName("testWorker")
        .send()
        .join();

    // then
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();
    assertThat(request.getWorker()).isEqualTo("testWorker");
  }

  @Test
  public void shouldSetRequestTimeout() {
    // given
    final Duration requestTimeout = Duration.ofHours(124);

    // when
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .requestTimeout(requestTimeout)
        .send()
        .join();

    // then
    Mockito.verify(rule.getGatewayStub(), Mockito.times(1))
        .withDeadlineAfter(requestTimeout.toNanos(), TimeUnit.NANOSECONDS);
  }

  @Test
  public void shouldAllowSpecifyingTenantIdsAsList() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .tenantIds(Arrays.asList("tenant1", "tenant2"))
        .send()
        .join();

    // when
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantIdsList()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  public void shouldAllowSpecifyingTenantIdsAsVarArgs() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .tenantIds("tenant1", "tenant2")
        .send()
        .join();

    // when
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantIdsList()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  public void shouldAllowSpecifyingTenantIds() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .tenantId("tenant1")
        .tenantId("tenant2")
        .tenantId("tenant2")
        .send()
        .join();

    // when
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantIdsList()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  // Regression: https://github.com/camunda/camunda/issues/17513
  @Test
  public void shouldNotAccumulateTenantsOnSuccessiveOpen() {
    // given
    final StreamJobsCommandStep3 command =
        client
            .newStreamJobsCommand()
            .jobType("foo")
            .consumer(ignored -> {})
            .tenantId("tenant1")
            .tenantId("tenant2")
            .tenantId("tenant2");

    // when
    command.send().join();
    command.send().join();
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantIdsList()).containsExactlyInAnyOrder("tenant1", "tenant2");
  }

  @Test
  public void shouldSetAssignedTenantFilter() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .tenantFilter(TenantFilter.ASSIGNED)
        .send()
        .join();

    // when
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantFilter()).isEqualTo(GatewayOuterClass.TenantFilter.ASSIGNED);
    assertThat(request.getTenantIdsList()).isEmpty();
  }

  @Test
  public void shouldSetProvidedTenantFilter() {
    // given
    client
        .newStreamJobsCommand()
        .jobType("foo")
        .consumer(ignored -> {})
        .tenantFilter(TenantFilter.PROVIDED)
        .tenantId("foo")
        .send()
        .join();

    // when
    final StreamActivatedJobsRequest request = gatewayService.getLastRequest();

    // then
    assertThat(request.getTenantFilter()).isEqualTo(GatewayOuterClass.TenantFilter.PROVIDED);
    assertThat(request.getTenantIdsList()).containsExactly("foo");
  }
}
