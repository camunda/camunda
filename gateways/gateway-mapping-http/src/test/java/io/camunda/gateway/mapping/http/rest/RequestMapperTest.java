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
    final var migrationPlan = new ProcessInstanceMigrationBatchOperationPlan();
    migrationPlan.setTargetProcessDefinitionKey("123");
    final var mappingInstruction = new MigrateProcessInstanceMappingInstruction();
    mappingInstruction.setSourceElementId("source1");
    mappingInstruction.setTargetElementId("target1");
    migrationPlan.setMappingInstructions(List.of(mappingInstruction));

    final var batchOperationInstruction = new ProcessInstanceMigrationBatchOperationRequest();
    batchOperationInstruction.setMigrationPlan(migrationPlan);
    final var filter = new ProcessInstanceFilter();
    filter.setProcessDefinitionId(new AdvancedStringFilter().$like("process"));
    batchOperationInstruction.setFilter(filter);

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
    final var migrationPlan = new ProcessInstanceMigrationBatchOperationPlan();
    migrationPlan.setTargetProcessDefinitionKey("123");
    final var mappingInstruction = new MigrateProcessInstanceMappingInstruction();
    mappingInstruction.setSourceElementId(null);
    mappingInstruction.setTargetElementId(null);
    migrationPlan.setMappingInstructions(List.of(mappingInstruction));

    final var batchOperationRequest = new ProcessInstanceMigrationBatchOperationRequest();
    batchOperationRequest.setMigrationPlan(migrationPlan);
    final var filter = new ProcessInstanceFilter();
    batchOperationRequest.setFilter(filter);

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
        new ProcessInstanceModificationMoveBatchOperationInstruction()
            .sourceElementId("source1")
            .targetElementId("target1");
    final var filter = new ProcessInstanceFilter();
    filter.setProcessDefinitionId(new AdvancedStringFilter().$like("process"));

    final var modificationRequest =
        new ProcessInstanceModificationBatchOperationRequest()
            .addMoveInstructionsItem(moveInstruction);
    modificationRequest.setFilter(filter);

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
    final var moveInstruction =
        new ProcessInstanceModificationMoveBatchOperationInstruction().sourceElementId("source1");
    final var filter = new ProcessInstanceFilter();
    filter.setProcessDefinitionId(new AdvancedStringFilter().$like("process"));

    final var modificationRequest =
        new ProcessInstanceModificationBatchOperationRequest()
            .addMoveInstructionsItem(moveInstruction);
    modificationRequest.setFilter(filter);

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
      final var deleteRequest = new DeleteResourceRequest().operationReference(999L);

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
      final var deleteRequest = new DeleteResourceRequest().deleteHistory(true);

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
          new DeleteResourceRequest().operationReference(555L).deleteHistory(true);

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
      final var deleteRequest = new DeleteResourceRequest().deleteHistory(false);

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
      final var deleteRequest = new DeleteResourceRequest().operationReference(0L);

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(10)
              .timeout(5000L)
              .addTenantIdsItem("tenant-a")
              .addTenantIdsItem("tenant-b");
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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(5)
              .timeout(3000L)
              .tenantFilter(TenantFilterEnum.ASSIGNED);

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(5)
              .timeout(3000L)
              .addTenantIdsItem("tenant-a")
              .addTenantIdsItem("tenant-b")
              .tenantFilter(TenantFilterEnum.ASSIGNED);
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
          new JobActivationRequest().type("test-job").maxJobsToActivate(10).timeout(5000L);

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(10)
              .timeout(5000L)
              .addTenantIdsItem("tenant-a");

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(5)
              .timeout(3000L)
              .worker("test-worker");

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(10)
              .timeout(5000L)
              .addTenantIdsItem("tenant-a");

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
          new JobActivationRequest()
              .type("complex-job")
              .maxJobsToActivate(15)
              .timeout(10000L)
              .worker("worker-123")
              .requestTimeout(30000L)
              .addFetchVariableItem("var1")
              .addFetchVariableItem("var2")
              .addTenantIdsItem("tenant-a");

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
          new JobActivationRequest()
              .type("")
              .maxJobsToActivate(10)
              .timeout(5000L)
              .addTenantIdsItem("tenant-a");

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(0)
              .timeout(5000L)
              .addTenantIdsItem("tenant-a");

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(10)
              .timeout(0L)
              .addTenantIdsItem("tenant-a");

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(10)
              .timeout(5000L)
              .addTenantIdsItem("tenant-a")
              .addTenantIdsItem("tenant-b")
              .addTenantIdsItem("tenant-c");

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
          new JobActivationRequest()
              .type("test-job")
              .maxJobsToActivate(10)
              .timeout(5000L)
              .tenantFilter(TenantFilterEnum.ASSIGNED);

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
