/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejectionResponse;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.GatewayTest;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestHandler;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsResponse;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.JsonUtil;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Enclosed.class)
public final class ActivateJobsTest {

  @RunWith(Parameterized.class)
  public static class MultiTenancyDisabledTest extends GatewayTest {

    public MultiTenancyDisabledTest(final boolean isLongPollingEnabled) {
      super(
          cfg -> cfg.getLongPolling().setEnabled(isLongPollingEnabled),
          cfg -> cfg.getMultiTenancy().setChecksEnabled(false));
    }

    @Parameters(name = "{index}: longPolling.enabled[{0}]")
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {{true}, {false}});
    }

    @Test
    public void shouldMapRequestAndResponse() {
      // given
      final ActivateJobsStub stub = new ActivateJobsStub();
      stub.registerWith(brokerClient);

      final String jobType = "testJob";
      final String worker = "testWorker";
      final int maxJobsToActivate = 13;
      final Duration timeout = Duration.ofMinutes(12);
      final List<String> fetchVariables = Arrays.asList("foo", "bar", "baz");

      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder()
              .setType(jobType)
              .setWorker(worker)
              .setMaxJobsToActivate(maxJobsToActivate)
              .setTimeout(timeout.toMillis())
              .addAllFetchVariable(fetchVariables)
              .build();

      stub.addAvailableJobs(jobType, maxJobsToActivate);

      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();

      final ActivateJobsResponse response = responses.next();

      assertThat(response.getJobsCount()).isEqualTo(maxJobsToActivate);

      for (int i = 0; i < maxJobsToActivate; i++) {
        final ActivatedJob job = response.getJobs(i);
        assertThat(job.getKey())
            .isEqualTo(Protocol.encodePartitionId(Protocol.START_PARTITION_ID, i));
        assertThat(job.getType()).isEqualTo(jobType);
        assertThat(job.getWorker()).isEqualTo(worker);
        assertThat(job.getRetries()).isEqualTo(stub.getRetries());
        assertThat(job.getDeadline()).isEqualTo(stub.getDeadline());
        assertThat(job.getProcessInstanceKey()).isEqualTo(stub.getProcessInstanceKey());
        assertThat(job.getBpmnProcessId()).isEqualTo(stub.getBpmnProcessId());
        assertThat(job.getProcessDefinitionVersion()).isEqualTo(stub.getProcessDefinitionVersion());
        assertThat(job.getProcessDefinitionKey()).isEqualTo(stub.getProcessDefinitionKey());
        assertThat(job.getElementId()).isEqualTo(stub.getElementId());
        assertThat(job.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
        assertThat(job.getElementInstanceKey()).isEqualTo(stub.getElementInstanceKey());
        JsonUtil.assertEquality(job.getCustomHeaders(), stub.getCustomHeaders());
        JsonUtil.assertEquality(job.getVariables(), stub.getVariables());
      }

      final BrokerActivateJobsRequest brokerRequest = brokerClient.getSingleBrokerRequest();
      final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
      assertThat(brokerRequestValue.getMaxJobsToActivate()).isEqualTo(maxJobsToActivate);
      assertThat(brokerRequestValue.getTypeBuffer()).isEqualTo(wrapString(jobType));
      assertThat(brokerRequestValue.getTimeout()).isEqualTo(timeout.toMillis());
      assertThat(brokerRequestValue.getWorkerBuffer()).isEqualTo(wrapString(worker));
      assertThat(brokerRequestValue.variables())
          .extracting(v -> BufferUtil.bufferAsString(v.getValue()))
          .containsExactlyInAnyOrderElementsOf(fetchVariables);
      assertThat(brokerRequestValue.getTenantIds())
          .containsExactly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    }

    @Test
    public void shouldActivateJobsRoundRobin() {
      // given
      final ActivateJobsStub stub = new ActivateJobsStub();
      stub.registerWith(brokerClient);

      final String type = "test";
      final int maxJobsToActivate = 2;
      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder()
              .setType(type)
              .setMaxJobsToActivate(maxJobsToActivate)
              .build();

      for (int partitionOffset = 0; partitionOffset < 3; partitionOffset++) {
        stub.addAvailableJobs(type, maxJobsToActivate);
        // when
        final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

        // then
        assertThat(responses.hasNext()).isTrue();
        final ActivateJobsResponse response = responses.next();

        for (final ActivatedJob activatedJob : response.getJobsList()) {
          assertThat(Protocol.decodePartitionId(activatedJob.getKey()))
              .isEqualTo(Protocol.START_PARTITION_ID + partitionOffset);
        }
      }
    }

    @Test
    public void shouldSendRejectionWithoutRetrying() {
      // given
      final RejectionType rejectionType = RejectionType.INVALID_ARGUMENT;
      final AtomicInteger callCounter = new AtomicInteger();

      brokerClient.registerHandler(
          BrokerActivateJobsRequest.class,
          (RequestHandler<BrokerRequest<?>, BrokerResponse<?>>)
              request -> {
                callCounter.incrementAndGet();
                return new BrokerRejectionResponse<>(
                    new BrokerRejection(Intent.UNKNOWN, 1, rejectionType, "expected"));
              });
      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder().setType("").setMaxJobsToActivate(1).build();

      // when/then
      assertThatThrownBy(
              () -> {
                final Iterator<ActivateJobsResponse> responseIterator =
                    client.activateJobs(request);
                responseIterator.hasNext();
              })
          .isInstanceOf(StatusRuntimeException.class)
          .extracting(t -> ((StatusRuntimeException) t).getStatus().getCode())
          .isEqualTo(Status.INVALID_ARGUMENT.getCode());
      assertThat(callCounter).hasValue(1);
    }

    @Test
    public void shouldDefaultToProvidedTenantFilterAndDefaultTenantWhenMultiTenancyDisabled() {
      // given
      final ActivateJobsStub stub = new ActivateJobsStub();
      stub.registerWith(brokerClient);

      final String jobType = "testJob";
      final int maxJobsToActivate = 5;

      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder()
              .setType(jobType)
              .setMaxJobsToActivate(maxJobsToActivate)
              .build();

      stub.addAvailableJobs(jobType, maxJobsToActivate);

      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();
      responses.next();

      final BrokerActivateJobsRequest brokerRequest = brokerClient.getSingleBrokerRequest();
      final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
      assertThat(brokerRequestValue.getTenantIds())
          .describedAs(
              "When multi-tenancy is disabled and no tenant IDs are provided, the default tenant ID should be used")
          .containsExactly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      assertThat(brokerRequestValue.getTenantFilter())
          .describedAs(
              "When multi-tenancy is disabled and no tenant filter is provided, it should default to PROVIDED")
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED);
    }
  }

  @RunWith(Parameterized.class)
  public static class MultiTenancyEnabledTest extends GatewayTest {

    public MultiTenancyEnabledTest(final boolean isLongPollingEnabled) {
      super(
          cfg -> cfg.getLongPolling().setEnabled(isLongPollingEnabled),
          cfg -> cfg.getMultiTenancy().setChecksEnabled(true));
    }

    @Parameters(name = "{index}: longPolling.enabled[{0}]")
    public static Iterable<Object[]> data() {
      return Arrays.asList(new Object[][] {{true}, {false}});
    }

    @Test
    public void shouldMapProvidedTenantFilterWithTenantIds() {
      // given
      final ActivateJobsStub stub = new ActivateJobsStub();
      stub.registerWith(brokerClient);

      final String jobType = "testJob";
      final List<String> tenantIds = Arrays.asList("tenant-a", "tenant-b", "tenant-c");
      final int maxJobsToActivate = 5;

      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder()
              .setType(jobType)
              .setMaxJobsToActivate(maxJobsToActivate)
              .addAllTenantIds(tenantIds)
              .setTenantFilter(TenantFilter.PROVIDED)
              .build();

      stub.addAvailableJobs(jobType, maxJobsToActivate);

      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();
      responses.next();

      final BrokerActivateJobsRequest brokerRequest = brokerClient.getSingleBrokerRequest();
      final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
      assertThat(brokerRequestValue.getTenantIds()).containsExactlyElementsOf(tenantIds);
      assertThat(brokerRequestValue.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED);
    }

    @Test
    public void shouldMapAssignedTenantFilterWithoutTenantIds() {
      // given
      final ActivateJobsStub stub = new ActivateJobsStub();
      stub.registerWith(brokerClient);

      final String jobType = "testJob";
      final int maxJobsToActivate = 5;

      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder()
              .setType(jobType)
              .setMaxJobsToActivate(maxJobsToActivate)
              .setTenantFilter(TenantFilter.ASSIGNED)
              .build();

      stub.addAvailableJobs(jobType, maxJobsToActivate);

      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();
      responses.next();

      final BrokerActivateJobsRequest brokerRequest = brokerClient.getSingleBrokerRequest();
      final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
      assertThat(brokerRequestValue.getTenantIds())
          .describedAs(
              "When ASSIGNED filter is used, tenant IDs should be empty as they will be determined from authorized tenants")
          .isEmpty();
      assertThat(brokerRequestValue.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.ASSIGNED);
    }

    @Test
    public void shouldIgnoreTenantIdsWhenAssignedTenantFilterIsUsed() {
      // given
      final ActivateJobsStub stub = new ActivateJobsStub();
      stub.registerWith(brokerClient);

      final String jobType = "testJob";
      final List<String> tenantIds = Arrays.asList("tenant-a", "tenant-b");
      final int maxJobsToActivate = 5;

      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder()
              .setType(jobType)
              .setMaxJobsToActivate(maxJobsToActivate)
              .addAllTenantIds(tenantIds)
              .setTenantFilter(TenantFilter.ASSIGNED)
              .build();

      stub.addAvailableJobs(jobType, maxJobsToActivate);

      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();
      responses.next();

      final BrokerActivateJobsRequest brokerRequest = brokerClient.getSingleBrokerRequest();
      final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
      assertThat(brokerRequestValue.getTenantIds())
          .describedAs(
              "Provided tenant IDs should be ignored when ASSIGNED filter is used. "
                  + "Tenant IDs will be determined from authorized tenants instead.")
          .isEmpty();
      assertThat(brokerRequestValue.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.ASSIGNED);
    }

    @Test
    public void shouldDefaultToProvidedTenantFilterWhenNotSpecified() {
      // given
      final ActivateJobsStub stub = new ActivateJobsStub();
      stub.registerWith(brokerClient);

      final String jobType = "testJob";
      final int maxJobsToActivate = 5;
      final List<String> tenantIds = List.of("tenant-a");

      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder()
              .setType(jobType)
              .setMaxJobsToActivate(maxJobsToActivate)
              .addAllTenantIds(tenantIds)
              .build();

      stub.addAvailableJobs(jobType, maxJobsToActivate);

      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();
      responses.next();

      final BrokerActivateJobsRequest brokerRequest = brokerClient.getSingleBrokerRequest();
      final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
      assertThat(brokerRequestValue.getTenantFilter())
          .describedAs("When no tenant filter is specified, it should default to PROVIDED")
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED);
      assertThat(brokerRequestValue.getTenantIds()).containsExactlyElementsOf(tenantIds);
    }

    @Test
    public void shouldMapProvidedTenantFilterWithSingleTenantId() {
      // given
      final ActivateJobsStub stub = new ActivateJobsStub();
      stub.registerWith(brokerClient);

      final String jobType = "testJob";
      final String tenantId = "tenant-xyz";
      final int maxJobsToActivate = 3;

      final ActivateJobsRequest request =
          ActivateJobsRequest.newBuilder()
              .setType(jobType)
              .setMaxJobsToActivate(maxJobsToActivate)
              .addTenantIds(tenantId)
              .setTenantFilter(TenantFilter.PROVIDED)
              .build();

      stub.addAvailableJobs(jobType, maxJobsToActivate);

      // when
      final Iterator<ActivateJobsResponse> responses = client.activateJobs(request);

      // then
      assertThat(responses.hasNext()).isTrue();
      responses.next();

      final BrokerActivateJobsRequest brokerRequest = brokerClient.getSingleBrokerRequest();
      final JobBatchRecord brokerRequestValue = brokerRequest.getRequestWriter();
      assertThat(brokerRequestValue.getTenantIds()).containsExactly(tenantId);
      assertThat(brokerRequestValue.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED);
    }
  }
}
