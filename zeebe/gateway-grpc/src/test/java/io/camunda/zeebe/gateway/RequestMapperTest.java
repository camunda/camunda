/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonParseException;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ActivateJobsRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.DeleteResourceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.StreamActivatedJobsRequest;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class RequestMapperTest {

  // missing closing quote in second variable
  private static final String INVALID_VARIABLES =
      "{ \"test\": \"value\", \"error\": \"errorrvalue }";

  // missing closing quote in "denied"
  private static final String INVALID_RESULT =
      """
        {
          "result": {
            "denied: true
          }
        }
      """;

  // BigInteger larger than 2^64-1
  private static final String BIG_INTEGER =
      "{\"mybigintistoolong\": 123456789012345678901234567890}";

  @Test
  public void shouldThrowHelpfulExceptionIfJsonIsInvalid() {
    // when + then
    assertThatThrownBy(() -> RequestMapper.ensureJsonSet(INVALID_VARIABLES))
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid JSON", INVALID_VARIABLES)
        .cause()
        .isInstanceOf(JsonParseException.class);
  }

  @Test
  public void shouldThrowHelpfulExceptionIfJsonIsInvalidForResult() {
    // when + then
    assertThatThrownBy(() -> RequestMapper.ensureJsonSet(INVALID_RESULT))
        .isInstanceOf(JsonParseException.class)
        .hasMessageContaining("Invalid JSON", INVALID_RESULT)
        .cause()
        .isInstanceOf(JsonParseException.class);
  }

  @Test
  public void shouldThrowHelpfulExceptionIfJsonHasBigInteger() {
    // when + then
    assertThatThrownBy(() -> RequestMapper.ensureJsonSet(BIG_INTEGER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MessagePack cannot serialize BigInteger larger than 2^64-1");
  }

  @Nested
  class DeleteResourceRequestMappingTest {

    @Test
    public void shouldMapDeleteResourceRequestWithMinimalFields() {
      // given
      final var grpcRequest = DeleteResourceRequest.newBuilder().setResourceKey(12345L).build();

      // when
      final var brokerRequest = RequestMapper.toDeleteResourceRequest(grpcRequest);

      // then
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getResourceKey()).isEqualTo(12345L);
      assertThat(requestWriter.isDeleteHistory()).isFalse();
    }

    @Test
    public void shouldMapDeleteResourceRequestWithOperationReference() {
      // given
      final var grpcRequest =
          DeleteResourceRequest.newBuilder()
              .setResourceKey(67890L)
              .setOperationReference(999L)
              .build();

      // when
      final var brokerRequest = RequestMapper.toDeleteResourceRequest(grpcRequest);

      // then
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getResourceKey()).isEqualTo(67890L);
      assertThat(requestWriter.isDeleteHistory()).isFalse();
      assertThat(brokerRequest.getOperationReference()).isEqualTo(999L);
    }

    @Test
    public void shouldMapDeleteResourceRequestWithDeleteHistory() {
      // given
      final var grpcRequest =
          DeleteResourceRequest.newBuilder().setResourceKey(11111L).setDeleteHistory(true).build();

      // when
      final var brokerRequest = RequestMapper.toDeleteResourceRequest(grpcRequest);

      // then
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getResourceKey()).isEqualTo(11111L);
      assertThat(requestWriter.isDeleteHistory()).isTrue();
    }

    @Test
    public void shouldMapDeleteResourceRequestWithAllFields() {
      // given
      final var grpcRequest =
          DeleteResourceRequest.newBuilder()
              .setResourceKey(22222L)
              .setOperationReference(555L)
              .setDeleteHistory(true)
              .build();

      // when
      final var brokerRequest = RequestMapper.toDeleteResourceRequest(grpcRequest);

      // then
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getResourceKey()).isEqualTo(22222L);
      assertThat(requestWriter.isDeleteHistory()).isTrue();
      assertThat(brokerRequest.getOperationReference()).isEqualTo(555L);
    }
  }

  @Nested
  class ActivateJobsRequestMappingTest {

    @BeforeEach
    public void setup() {
      // Enable multi-tenancy for most tests
      RequestMapper.setMultiTenancyEnabled(true);
    }

    @AfterEach
    public void tearDown() {
      // Reset to default state
      RequestMapper.setMultiTenancyEnabled(false);
    }

    @Test
    public void shouldMapActivateJobsRequestWithProvidedTenantFilter() {
      // given
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(10)
              .setTimeout(5000L)
              .addTenantIds("tenant-a")
              .addTenantIds("tenant-b")
              .setTenantFilter(
                  io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter.PROVIDED)
              .build();

      // when
      final var brokerRequest = RequestMapper.toActivateJobsRequest(grpcRequest);

      // then
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getType()).isEqualTo("test-job");
      assertThat(requestWriter.getWorker()).isEqualTo("test-worker");
      assertThat(requestWriter.getMaxJobsToActivate()).isEqualTo(10);
      assertThat(requestWriter.getTimeout()).isEqualTo(5000L);
      assertThat(requestWriter.getTenantIds()).containsExactly("tenant-a", "tenant-b");
      assertThat(requestWriter.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED);
    }

    @Test
    public void shouldMapActivateJobsRequestWithAssignedTenantFilter() {
      // given
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(5)
              .setTimeout(3000L)
              .setTenantFilter(
                  io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter.ASSIGNED)
              .build();

      // when
      final var brokerRequest = RequestMapper.toActivateJobsRequest(grpcRequest);

      // then
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getType()).isEqualTo("test-job");
      assertThat(requestWriter.getWorker()).isEqualTo("test-worker");
      assertThat(requestWriter.getMaxJobsToActivate()).isEqualTo(5);
      assertThat(requestWriter.getTimeout()).isEqualTo(3000L);
      assertThat(requestWriter.getTenantIds()).isEmpty();
      assertThat(requestWriter.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.ASSIGNED);
    }

    @Test
    public void shouldIgnoreTenantIdsWhenAssignedTenantFilterIsUsed() {
      // given - request has tenantIds but uses ASSIGNED filter
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(5)
              .addTenantIds("tenant-a")
              .addTenantIds("tenant-b")
              .setTenantFilter(
                  io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter.ASSIGNED)
              .build();

      // when
      final var brokerRequest = RequestMapper.toActivateJobsRequest(grpcRequest);

      // then - tenantIds should be empty when ASSIGNED filter is used
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getTenantIds()).isEmpty();
      assertThat(requestWriter.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.ASSIGNED);
    }

    @Test
    public void shouldDefaultToProvidedTenantFilterWhenNotSpecified() {
      // given - request without explicit tenant filter
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(10)
              .addTenantIds("tenant-a")
              .build();

      // when
      final var brokerRequest = RequestMapper.toActivateJobsRequest(grpcRequest);

      // then - should default to PROVIDED
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED);
      assertThat(requestWriter.getTenantIds()).containsExactly("tenant-a");
    }

    @Test
    public void shouldRejectProvidedFilterWithoutTenantIds() {
      // given - PROVIDED filter but no tenant IDs
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(10)
              .setTenantFilter(
                  io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter.PROVIDED)
              .build();

      // when/then - should throw exception
      assertThatThrownBy(() -> RequestMapper.toActivateJobsRequest(grpcRequest))
          .hasMessageContaining("no tenant identifiers were provided");
    }

    @Test
    public void shouldMapActivateJobsRequestWithFetchVariables() {
      // given
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(10)
              .addTenantIds("tenant-a")
              .addFetchVariable("var1")
              .addFetchVariable("var2")
              .addFetchVariable("var3")
              .build();

      // when
      final var brokerRequest = RequestMapper.toActivateJobsRequest(grpcRequest);

      // then
      final var requestWriter = brokerRequest.getRequestWriter();
      final var variables = requestWriter.variables();
      assertThat(variables).hasSize(3);
      assertThat(variables.iterator())
          .toIterable()
          .extracting(v -> io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString(v.getValue()))
          .containsExactly("var1", "var2", "var3");
    }

    @Test
    public void shouldMapActivateJobsRequestWithMultiTenancyDisabled() {
      // given
      RequestMapper.setMultiTenancyEnabled(false);
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(10)
              .build();

      // when
      final var brokerRequest = RequestMapper.toActivateJobsRequest(grpcRequest);

      // then - should default to default tenant
      final var requestWriter = brokerRequest.getRequestWriter();
      assertThat(requestWriter.getTenantIds())
          .containsExactly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      assertThat(requestWriter.getTenantFilter())
          .isEqualTo(io.camunda.zeebe.protocol.record.value.TenantFilter.PROVIDED);
    }

    @Test
    public void shouldRejectNonDefaultTenantIdWhenMultiTenancyDisabled() {
      // given
      RequestMapper.setMultiTenancyEnabled(false);
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(10)
              .addTenantIds("tenant-a")
              .build();

      // when/then - should throw exception
      assertThatThrownBy(() -> RequestMapper.toActivateJobsRequest(grpcRequest))
          .hasMessageContaining("multi-tenancy is disabled");
    }

    @Test
    public void shouldThrowExceptionForUnrecognizedTenantFilter() {
      // given
      final var grpcRequest =
          ActivateJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setMaxJobsToActivate(10)
              .addTenantIds("tenant-a")
              .setTenantFilterValue(999) // Invalid enum value
              .build();

      // when/then
      assertThatThrownBy(() -> RequestMapper.toActivateJobsRequest(grpcRequest))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unrecognized tenantFilter option")
          .hasMessageContaining("expected one of ASSIGNED or PROVIDED");
    }
  }

  @Nested
  class StreamActivatedJobsRequestMappingTest {

    private static final Map<String, Object> EMPTY_CLAIMS = Map.of();

    @BeforeEach
    public void setup() {
      RequestMapper.setMultiTenancyEnabled(true);
    }

    @AfterEach
    public void tearDown() {
      RequestMapper.setMultiTenancyEnabled(false);
    }

    @Test
    public void shouldMapWithProvidedTenantFilter() {
      // given
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setTimeout(5000L)
              .addTenantIds("tenant-a")
              .addTenantIds("tenant-b")
              .setTenantFilter(
                  io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter.PROVIDED)
              .build();

      // when
      final var properties = RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS);

      // then
      assertThat(BufferUtil.bufferAsString(properties.worker())).isEqualTo("test-worker");
      assertThat(properties.timeout()).isEqualTo(5000L);
      assertThat(properties.tenantIds()).containsExactly("tenant-a", "tenant-b");
      assertThat(properties.tenantFilter()).isEqualTo(TenantFilter.PROVIDED);
    }

    @Test
    public void shouldMapWithAssignedTenantFilter() {
      // given
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setTimeout(3000L)
              .setTenantFilter(
                  io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter.ASSIGNED)
              .build();

      // when
      final var properties = RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS);

      // then
      assertThat(BufferUtil.bufferAsString(properties.worker())).isEqualTo("test-worker");
      assertThat(properties.timeout()).isEqualTo(3000L);
      assertThat(properties.tenantIds()).isEmpty();
      assertThat(properties.tenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    }

    @Test
    public void shouldIgnoreTenantIdsWhenAssignedTenantFilterIsUsed() {
      // given — request has tenantIds but uses ASSIGNED filter
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .addTenantIds("tenant-a")
              .addTenantIds("tenant-b")
              .setTenantFilter(
                  io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter.ASSIGNED)
              .build();

      // when
      final var properties = RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS);

      // then — tenantIds should be empty when ASSIGNED filter is used
      assertThat(properties.tenantIds()).isEmpty();
      assertThat(properties.tenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    }

    @Test
    public void shouldDefaultToProvidedTenantFilterWhenNotSpecified() {
      // given — request without explicit tenant filter
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .addTenantIds("tenant-a")
              .build();

      // when
      final var properties = RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS);

      // then — should default to PROVIDED
      assertThat(properties.tenantFilter()).isEqualTo(TenantFilter.PROVIDED);
      assertThat(properties.tenantIds()).containsExactly("tenant-a");
    }

    @Test
    public void shouldRejectProvidedFilterWithoutTenantIds() {
      // given — PROVIDED filter but no tenant IDs
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .setTenantFilter(
                  io.camunda.zeebe.gateway.protocol.GatewayOuterClass.TenantFilter.PROVIDED)
              .build();

      // when/then
      assertThatThrownBy(() -> RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS))
          .hasMessageContaining("no tenant identifiers were provided");
    }

    @Test
    public void shouldMapWithMultiTenancyDisabled() {
      // given
      RequestMapper.setMultiTenancyEnabled(false);
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .build();

      // when
      final var properties = RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS);

      // then — should default to default tenant
      assertThat(properties.tenantIds()).containsExactly(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
      assertThat(properties.tenantFilter()).isEqualTo(TenantFilter.PROVIDED);
    }

    @Test
    public void shouldRejectNonDefaultTenantIdWhenMultiTenancyDisabled() {
      // given
      RequestMapper.setMultiTenancyEnabled(false);
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .addTenantIds("tenant-a")
              .build();

      // when/then
      assertThatThrownBy(() -> RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS))
          .hasMessageContaining("multi-tenancy is disabled");
    }

    @Test
    public void shouldMapFetchVariables() {
      // given
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .addTenantIds("tenant-a")
              .addFetchVariable("var1")
              .addFetchVariable("var2")
              .build();

      // when
      final var properties = RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS);

      // then
      assertThat(properties.fetchVariables())
          .extracting(BufferUtil::bufferAsString)
          .containsExactly("var1", "var2");
    }

    @Test
    public void shouldPassClaimsThrough() {
      // given
      final var claims = Map.<String, Object>of("authorized_username", "test-user");
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .addTenantIds("tenant-a")
              .build();

      // when
      final var properties = RequestMapper.toJobActivationProperties(request, claims);

      // then
      assertThat(properties.claims()).containsEntry("authorized_username", "test-user");
    }

    @Test
    public void shouldThrowExceptionForUnrecognizedTenantFilter() {
      // given
      final var request =
          StreamActivatedJobsRequest.newBuilder()
              .setType("test-job")
              .setWorker("test-worker")
              .addTenantIds("tenant-a")
              .setTenantFilterValue(999)
              .build();

      // when/then
      assertThatThrownBy(() -> RequestMapper.toJobActivationProperties(request, EMPTY_CLAIMS))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unrecognized tenantFilter option")
          .hasMessageContaining("expected one of ASSIGNED or PROVIDED");
    }
  }
}
