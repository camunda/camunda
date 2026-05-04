/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.rest;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.gateway.mapping.http.RequestMapper;
import io.camunda.gateway.protocol.model.AdvancedStringFilter;
import io.camunda.gateway.protocol.model.DeleteResourceRequest;
import io.camunda.gateway.protocol.model.JobActivationRequest;
import io.camunda.gateway.protocol.model.MigrateProcessInstanceMappingInstruction;
import io.camunda.gateway.protocol.model.ProcessInstanceFilter;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationPlan;
import io.camunda.gateway.protocol.model.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationBatchOperationRequest;
import io.camunda.gateway.protocol.model.ProcessInstanceModificationMoveBatchOperationInstruction;
import io.camunda.gateway.protocol.model.TenantFilterEnum;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrateBatchOperationRequest;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceModifyBatchOperationRequest;
import io.camunda.zeebe.protocol.record.value.TenantFilter;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

class RequestMapperTest {

  @Test
  void shouldMapToProcessInstanceMigrationBatchOperationRequest() {
    // given
    final var mappingInstruction =
        MigrateProcessInstanceMappingInstruction.Builder.create()
            .sourceElementId("source1")
            .targetElementId("target1")
            .build();
    final var migrationPlan =
        ProcessInstanceMigrationBatchOperationPlan.Builder.create()
            .targetProcessDefinitionKey("123")
            .mappingInstructions(List.of(mappingInstruction))
            .build();
    final var filter =
        ProcessInstanceFilter.Builder.create()
            .processDefinitionId(AdvancedStringFilter.Builder.create().$like("process").build())
            .build();
    final var batchOperationInstruction =
        ProcessInstanceMigrationBatchOperationRequest.Builder.create()
            .filter(filter)
            .migrationPlan(migrationPlan)
            .build();

    // when
    final Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest> result =
        RequestMapper.toProcessInstanceMigrationBatchOperationRequest(batchOperationInstruction);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.targetProcessDefinitionKey()).isEqualTo(123L);
    assertThat(request.mappingInstructions())
        .hasSize(1)
        .first()
        .satisfies(
            instruction -> {
              assertThat(instruction.getSourceElementId()).isEqualTo("source1");
              assertThat(instruction.getTargetElementId()).isEqualTo("target1");
            });
  }

  @Test
  void shouldReturnProblemDetailForInvalidInput() {
    // given
    // Use empty strings to trigger the "are required" validation (staged builder enforces non-null,
    // but the validator checks isEmpty() to detect missing values)
    final var mappingInstruction =
        MigrateProcessInstanceMappingInstruction.Builder.create()
            .sourceElementId("")
            .targetElementId("")
            .build();
    final var migrationPlan =
        ProcessInstanceMigrationBatchOperationPlan.Builder.create()
            .targetProcessDefinitionKey("123")
            .mappingInstructions(List.of(mappingInstruction))
            .build();
    final var filter = ProcessInstanceFilter.empty();
    final var batchOperationRequest =
        ProcessInstanceMigrationBatchOperationRequest.Builder.create()
            .filter(filter)
            .migrationPlan(migrationPlan)
            .build();

    // when
    final Either<ProblemDetail, ProcessInstanceMigrateBatchOperationRequest> result =
        RequestMapper.toProcessInstanceMigrationBatchOperationRequest(batchOperationRequest);

    // then
    assertThat(result.isLeft()).isTrue();
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getStatus()).isEqualTo(400); // Bad Request
    assertThat(problemDetail.getDetail()).contains("are required");
  }

  @Test
  void shouldMapProcessInstanceModifyBatchOperationRequest() {
    // given
    final var moveInstruction =
        ProcessInstanceModificationMoveBatchOperationInstruction.Builder.create()
            .sourceElementId("source1")
            .targetElementId("target1")
            .build();
    final var filter =
        ProcessInstanceFilter.Builder.create()
            .processDefinitionId(AdvancedStringFilter.Builder.create().$like("process").build())
            .build();
    final var modificationRequest =
        ProcessInstanceModificationBatchOperationRequest.Builder.create()
            .filter(filter)
            .moveInstructions(List.of(moveInstruction))
            .build();

    // when
    final Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest> result =
        RequestMapper.toProcessInstanceModifyBatchOperationRequest(modificationRequest);

    // then
    assertThat(result.isRight()).isTrue();
    final var request = result.get();
    assertThat(request.moveInstructions())
        .hasSize(1)
        .first()
        .satisfies(
            instruction -> {
              assertThat(instruction.getSourceElementId()).isEqualTo("source1");
              assertThat(instruction.getTargetElementId()).isEqualTo("target1");
              assertThat(instruction.getAncestorScopeKey()).isEqualTo(-1L);
              assertThat(instruction.isInferAncestorScopeFromSourceHierarchy()).isTrue();
              assertThat(instruction.isUseSourceParentKeyAsAncestorScopeKey()).isFalse();
              assertThat(instruction.getVariableInstructions()).isEmpty();
            });
  }

  @Test
  void shouldNotMapProcessInstanceModifyBatchOperationRequestWhenInvalid() {
    // given
    // Use empty targetElementId to trigger "No targetElementId provided." validation
    final var moveInstruction =
        ProcessInstanceModificationMoveBatchOperationInstruction.Builder.create()
            .sourceElementId("source1")
            .targetElementId("")
            .build();
    final var filter =
        ProcessInstanceFilter.Builder.create()
            .processDefinitionId(AdvancedStringFilter.Builder.create().$like("process").build())
            .build();
    final var modificationRequest =
        ProcessInstanceModificationBatchOperationRequest.Builder.create()
            .filter(filter)
            .moveInstructions(List.of(moveInstruction))
            .build();

    // when
    final Either<ProblemDetail, ProcessInstanceModifyBatchOperationRequest> result =
        RequestMapper.toProcessInstanceModifyBatchOperationRequest(modificationRequest);

    // then
    assertThat(result.isLeft()).isTrue();
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getStatus()).isEqualTo(400);
    assertThat(problemDetail.getDetail()).isEqualTo("No targetElementId provided.");
  }

  @Nested
  class ResourceDeletionRequestMappingTest {

    @Test
    void shouldMapResourceDeletionWithMinimalFields() {
      // given
      final long resourceKey = 12345L;

      // when
      final var result = RequestMapper.toResourceDeletion(resourceKey, null);

      // then
      assertThat(result.isRight()).isTrue();
      final var request = result.get();
      assertThat(request.resourceKey()).isEqualTo(12345L);
      assertThat(request.operationReference()).isNull();
      assertThat(request.deleteHistory()).isFalse();
    }

    @Test
    void shouldMapResourceDeletionWithOperationReference() {
      // given
      final long resourceKey = 67890L;
      final var deleteRequest =
          DeleteResourceRequest.Builder.create().operationReference(999L).build();

      // when
      final var result = RequestMapper.toResourceDeletion(resourceKey, deleteRequest);

      // then
      assertThat(result.isRight()).isTrue();
      final var request = result.get();
      assertThat(request.resourceKey()).isEqualTo(67890L);
      assertThat(request.operationReference()).isEqualTo(999L);
      assertThat(request.deleteHistory()).isFalse();
    }

    @Test
    void shouldMapResourceDeletionWithDeleteHistory() {
      // given
      final long resourceKey = 11111L;
      final var deleteRequest = DeleteResourceRequest.Builder.create().deleteHistory(true).build();

      // when
      final var result = RequestMapper.toResourceDeletion(resourceKey, deleteRequest);

      // then
      assertThat(result.isRight()).isTrue();
      final var request = result.get();
      assertThat(request.resourceKey()).isEqualTo(11111L);
      assertThat(request.operationReference()).isNull();
      assertThat(request.deleteHistory()).isTrue();
    }

    @Test
    void shouldMapResourceDeletionWithAllFields() {
      // given
      final long resourceKey = 22222L;
      final var deleteRequest =
          DeleteResourceRequest.Builder.create()
              .operationReference(555L)
              .deleteHistory(true)
              .build();

      // when
      final var result = RequestMapper.toResourceDeletion(resourceKey, deleteRequest);

      // then
      assertThat(result.isRight()).isTrue();
      final var request = result.get();
      assertThat(request.resourceKey()).isEqualTo(22222L);
      assertThat(request.operationReference()).isEqualTo(555L);
      assertThat(request.deleteHistory()).isTrue();
    }

    @Test
    void shouldMapResourceDeletionWithDeleteHistoryFalse() {
      // given
      final long resourceKey = 33333L;
      final var deleteRequest = DeleteResourceRequest.Builder.create().deleteHistory(false).build();

      // when
      final var result = RequestMapper.toResourceDeletion(resourceKey, deleteRequest);

      // then
      assertThat(result.isRight()).isTrue();
      final var request = result.get();
      assertThat(request.resourceKey()).isEqualTo(33333L);
      assertThat(request.operationReference()).isNull();
      assertThat(request.deleteHistory()).isFalse();
    }

    @Test
    void shouldRejectResourceDeletionWithInvalidOperationReference() {
      // given
      final long resourceKey = 44444L;
      final var deleteRequest =
          DeleteResourceRequest.Builder.create().operationReference(0L).build();

      // when
      final var result = RequestMapper.toResourceDeletion(resourceKey, deleteRequest);

      // then
      assertThat(result.isLeft()).isTrue();
      final var problemDetail = result.getLeft();
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getDetail()).contains("operationReference");
      assertThat(problemDetail.getDetail()).contains("> 0");
    }
  }

  @Nested
  class JobActivationRequestMappingTest {

    @Test
    void shouldMapJobActivationWithProvidedTenantFilterAndTenantIds() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(5000L)
              .maxJobsToActivate(10)
              .tenantIds(List.of("tenant-a", "tenant-b"))
              .build();
      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isRight()).isTrue();
      final var activateJobsRequest = result.get();
      assertThat(activateJobsRequest.type()).isEqualTo("test-job");
      assertThat(activateJobsRequest.maxJobsToActivate()).isEqualTo(10);
      assertThat(activateJobsRequest.timeout()).isEqualTo(5000L);
      assertThat(activateJobsRequest.tenantIds()).containsExactly("tenant-a", "tenant-b");
      assertThat(activateJobsRequest.tenantFilter()).isEqualTo(TenantFilter.PROVIDED);
    }

    @Test
    void shouldMapJobActivationWithAssignedTenantFilterAndNoTenantIds() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(3000L)
              .maxJobsToActivate(5)
              .tenantFilter(TenantFilterEnum.ASSIGNED)
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isRight()).isTrue();
      final var activateJobsRequest = result.get();
      assertThat(activateJobsRequest.type()).isEqualTo("test-job");
      assertThat(activateJobsRequest.tenantIds())
          .describedAs("With ASSIGNED filter, tenant IDs should be empty")
          .isEmpty();
      assertThat(activateJobsRequest.tenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    }

    @Test
    void shouldIgnoreTenantIdsWhenAssignedTenantFilterIsUsed() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(3000L)
              .maxJobsToActivate(5)
              .tenantIds(List.of("tenant-a", "tenant-b"))
              .tenantFilter(TenantFilterEnum.ASSIGNED)
              .build();
      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isRight()).isTrue();
      final var activateJobsRequest = result.get();
      assertThat(activateJobsRequest.tenantIds())
          .describedAs(
              "Provided tenant IDs should be ignored when ASSIGNED filter is used. "
                  + "Tenant IDs will be determined from authorized tenants instead.")
          .isEmpty();
      assertThat(activateJobsRequest.tenantFilter()).isEqualTo(TenantFilter.ASSIGNED);
    }

    @Test
    void shouldRejectProvidedFilterWithoutTenantIdsWhenMultiTenancyEnabled() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(5000L)
              .maxJobsToActivate(10)
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isLeft()).isTrue();
      final var problemDetail = result.getLeft();
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getDetail()).contains("Activate Jobs");
      assertThat(problemDetail.getDetail()).contains("no tenant identifier");
    }

    @Test
    void shouldDefaultToProvidedTenantFilterWhenNotSpecified() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(5000L)
              .maxJobsToActivate(10)
              .tenantIds(List.of("tenant-a"))
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isRight()).isTrue();
      final var activateJobsRequest = result.get();
      assertThat(activateJobsRequest.tenantFilter())
          .describedAs("When no tenant filter is specified, it should default to PROVIDED")
          .isEqualTo(TenantFilter.PROVIDED);
      assertThat(activateJobsRequest.tenantIds()).containsExactly("tenant-a");
    }

    @Test
    void shouldMapJobActivationWithDefaultTenantWhenMultiTenancyDisabled() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(3000L)
              .maxJobsToActivate(5)
              .worker("test-worker")
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, false);

      // then
      assertThat(result.isRight()).isTrue();
      final var activateJobsRequest = result.get();
      assertThat(activateJobsRequest.tenantIds())
          .describedAs(
              "When multi-tenancy is disabled, default tenant ID should be automatically added")
          .containsExactly("<default>");
      assertThat(activateJobsRequest.tenantFilter()).isEqualTo(TenantFilter.PROVIDED);
    }

    @Test
    void shouldRejectNonDefaultTenantIdWhenMultiTenancyDisabled() {
      // given - tenant ID provided when multi-tenancy is disabled
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(5000L)
              .maxJobsToActivate(10)
              .tenantIds(List.of("tenant-a"))
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      final var problemDetail = result.getLeft();
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getDetail()).contains("multi-tenancy is disabled");
    }

    @Test
    void shouldMapJobActivationWithAllFields() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("complex-job")
              .timeout(10000L)
              .maxJobsToActivate(15)
              .worker("worker-123")
              .requestTimeout(30000L)
              .fetchVariable(List.of("var1", "var2"))
              .tenantIds(List.of("tenant-a"))
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isRight()).isTrue();
      final var activateJobsRequest = result.get();
      assertThat(activateJobsRequest.type()).isEqualTo("complex-job");
      assertThat(activateJobsRequest.maxJobsToActivate()).isEqualTo(15);
      assertThat(activateJobsRequest.timeout()).isEqualTo(10000L);
      assertThat(activateJobsRequest.worker()).isEqualTo("worker-123");
      assertThat(activateJobsRequest.requestTimeout()).isEqualTo(30000L);
      assertThat(activateJobsRequest.fetchVariable()).containsExactly("var1", "var2");
      assertThat(activateJobsRequest.tenantIds()).containsExactly("tenant-a");
    }

    @Test
    void shouldRejectJobActivationWithInvalidType() {
      // given - request with empty type
      final var request =
          JobActivationRequest.Builder.create()
              .type("")
              .timeout(5000L)
              .maxJobsToActivate(10)
              .tenantIds(List.of("tenant-a"))
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isLeft()).isTrue();
      final var problemDetail = result.getLeft();
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getDetail()).contains("type");
    }

    @Test
    void shouldRejectJobActivationWithInvalidMaxJobsToActivate() {
      // given - request with invalid maxJobsToActivate
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(5000L)
              .maxJobsToActivate(0)
              .tenantIds(List.of("tenant-a"))
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isLeft()).isTrue();
      final var problemDetail = result.getLeft();
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getDetail()).contains("maxJobsToActivate");
    }

    @Test
    void shouldRejectJobActivationWithInvalidTimeout() {
      // given - request with invalid timeout
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(0L)
              .maxJobsToActivate(10)
              .tenantIds(List.of("tenant-a"))
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isLeft()).isTrue();
      final var problemDetail = result.getLeft();
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getDetail()).contains("timeout");
    }

    @Test
    void shouldMapJobActivationWithMultipleTenantIds() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(5000L)
              .maxJobsToActivate(10)
              .tenantIds(List.of("tenant-a", "tenant-b", "tenant-c"))
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, true);

      // then
      assertThat(result.isRight()).isTrue();
      final var activateJobsRequest = result.get();
      assertThat(activateJobsRequest.tenantIds())
          .containsExactly("tenant-a", "tenant-b", "tenant-c");
    }

    @Test
    void shouldRejectAssignedTenantFilterWhenMultiTenancyDisabled() {
      // given
      final var request =
          JobActivationRequest.Builder.create()
              .type("test-job")
              .timeout(5000L)
              .maxJobsToActivate(10)
              .tenantFilter(TenantFilterEnum.ASSIGNED)
              .build();

      // when
      final var result = RequestMapper.toJobsActivationRequest(request, false);

      // then
      assertThat(result.isLeft()).isTrue();
      final var problemDetail = result.getLeft();
      assertThat(problemDetail.getStatus()).isEqualTo(400);
      assertThat(problemDetail.getDetail())
          .contains("ASSIGNED tenant filter")
          .contains("multi-tenancy is disabled");
    }
  }
}
