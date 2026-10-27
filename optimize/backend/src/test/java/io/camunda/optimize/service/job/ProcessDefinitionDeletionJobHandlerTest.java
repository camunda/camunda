/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.dto.optimize.rest.DefinitionVersionResponseDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.BusinessValueOverviewWriter;
import io.camunda.optimize.service.db.writer.BusinessValueTargetWriter;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.exceptions.OptimizeBulkFailureException;
import io.camunda.optimize.service.exceptions.OptimizeByQueryFailureException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.report.ReportService;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionDeletionJobHandlerTest {

  private static final String DEFINITION_ID = "definition-1";
  private static final String BPMN_PROCESS_ID = "invoice-process";
  private static final String TENANT_ID = "tenant-1";
  private static final String VERSION = "1";

  private ProcessDefinitionReader processDefinitionReader;
  private ProcessInstanceWriter processInstanceWriter;
  private ProcessDefinitionWriter processDefinitionWriter;
  private DefinitionReader definitionReader;
  private ReportService reportService;
  private DefinitionService definitionService;
  private BusinessValueTargetWriter businessValueTargetWriter;
  private BusinessValueOverviewWriter businessValueOverviewWriter;
  private ProcessDefinitionDeletionJobHandler handler;

  @BeforeEach
  void init() {
    processDefinitionReader = mock(ProcessDefinitionReader.class);
    processInstanceWriter = mock(ProcessInstanceWriter.class);
    processDefinitionWriter = mock(ProcessDefinitionWriter.class);
    definitionReader = mock(DefinitionReader.class);
    reportService = mock(ReportService.class);
    definitionService = mock(DefinitionService.class);
    businessValueTargetWriter = mock(BusinessValueTargetWriter.class);
    businessValueOverviewWriter = mock(BusinessValueOverviewWriter.class);
    // default: after deletion, no other version is left (i.e. this was the last remaining
    // version); individual tests override this when they need to assert the "other versions
    // remain" behavior
    lenient()
        .when(
            definitionReader.getDefinitionVersions(eq(DefinitionType.PROCESS), anyString(), any()))
        .thenReturn(List.of());
    handler =
        new ProcessDefinitionDeletionJobHandler(
            processDefinitionReader,
            processInstanceWriter,
            processDefinitionWriter,
            definitionReader,
            reportService,
            definitionService,
            businessValueTargetWriter,
            businessValueOverviewWriter,
            millis -> {});
  }

  @Test
  void shouldExposeJobTypeAndEntityType() {
    // when / then
    assertThat(handler.getJobType()).isEqualTo(JobType.DELETE);
    assertThat(handler.getEntityType()).isEqualTo(EntityType.PROCESS_DEFINITION);
  }

  @Test
  void shouldSoftDeleteDefinitionAndDeleteInstancesWhenDefinitionExists() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));

    // when
    handler.handle(job());

    // then
    verify(processDefinitionWriter).softDeleteDefinition(DEFINITION_ID);
    verify(processInstanceWriter).deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
  }

  @Test
  void shouldSoftDeleteDefinitionBeforeDeletingInstances() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));

    // when
    handler.handle(job());

    // then
    final var order = inOrder(processDefinitionWriter, processInstanceWriter);
    order.verify(processDefinitionWriter).softDeleteDefinition(DEFINITION_ID);
    order
        .verify(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
  }

  @Test
  void shouldNoOpWhenDefinitionNoLongerExists() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.empty());

    // when
    handler.handle(job());

    // then
    verify(processDefinitionWriter, never()).softDeleteDefinition(anyString());
    verify(processInstanceWriter, never()).deleteInstancesByDefinitionId(anyString(), anyString());
    verify(reportService, never()).clearCachedReportXml(anyString(), any());
    verify(definitionService, never())
        .invalidateProcessDefinitionIfLatest(anyString(), any(), anyString());
  }

  @Test
  void shouldRetryOnRetryableErrorWhenSoftDeletingDefinition() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .doNothing()
        .when(processDefinitionWriter)
        .softDeleteDefinition(DEFINITION_ID);

    // when
    handler.handle(job());

    // then
    verify(processDefinitionWriter, times(2)).softDeleteDefinition(DEFINITION_ID);
  }

  @Test
  void shouldClearCachedXmlAndDelegateCacheInvalidationWhenDeletingTheOnlyRemainingVersion() {
    // given -- post-delete query finds no other version left
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    when(definitionReader.getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Set.of(TENANT_ID)))
        .thenReturn(List.of());

    // when
    handler.handle(job());

    // then
    verify(reportService).clearCachedReportXml(BPMN_PROCESS_ID, TENANT_ID);
    verify(definitionService)
        .invalidateProcessDefinitionIfLatest(BPMN_PROCESS_ID, TENANT_ID, VERSION);
  }

  @Test
  void shouldNotClearCachedXmlWhenOtherVersionsRemainAfterDeletion() {
    // given -- post-delete query still finds a sibling version
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    when(definitionReader.getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Set.of(TENANT_ID)))
        .thenReturn(List.of(new DefinitionVersionResponseDto("2", null)));

    // when
    handler.handle(job());

    // then
    verify(reportService, never()).clearCachedReportXml(anyString(), any());
    verify(definitionService)
        .invalidateProcessDefinitionIfLatest(BPMN_PROCESS_ID, TENANT_ID, VERSION);
  }

  @Test
  void shouldScopeRemainingVersionsLookupToTheDeletedDefinitionsTenantNotAllTenants() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    when(definitionReader.getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Set.of(TENANT_ID)))
        .thenReturn(List.of());

    // when
    handler.handle(job());

    // then
    verify(definitionReader, never())
        .getDefinitionVersions(DefinitionType.PROCESS, BPMN_PROCESS_ID, Set.of());
    verify(reportService).clearCachedReportXml(BPMN_PROCESS_ID, TENANT_ID);
  }

  @Test
  void shouldCheckRemainingVersionsScopedToTheDeletedDefinitionsTenantWhenTenantIsNull() {
    // given -- single-tenant setups store a null tenantId; the scoped lookup must tolerate that
    final ProcessDefinitionOptimizeDto definitionWithNullTenant = definition();
    definitionWithNullTenant.setTenantId(null);
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definitionWithNullTenant));
    when(definitionReader.getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Collections.singleton(null)))
        .thenReturn(List.of());

    // when
    handler.handle(job());

    // then -- atLeastOnce: the cascade re-checks before deleting business-value data, and this
    // test is about the tenant the lookup is scoped to, not how many times it runs
    verify(definitionReader, atLeastOnce())
        .getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Collections.singleton(null));
    verify(reportService).clearCachedReportXml(BPMN_PROCESS_ID, null);
    verify(definitionService).invalidateProcessDefinitionIfLatest(BPMN_PROCESS_ID, null, VERSION);
  }

  @Test
  void shouldCheckRemainingVersionsAfterMarkingThisOneAsDeletedSoItIsNeverCountedAsRemaining() {
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    when(definitionReader.getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Set.of(TENANT_ID)))
        .thenReturn(List.of());

    // when
    handler.handle(job());

    // then
    // atLeastOnce: the cascade re-checks before deleting business-value data, and this test is
    // about the lookup happening after the soft-delete, not how many times it runs
    final var order = inOrder(definitionReader, processDefinitionWriter);
    order.verify(processDefinitionWriter).softDeleteDefinition(DEFINITION_ID);
    order
        .verify(definitionReader, atLeastOnce())
        .getDefinitionVersions(DefinitionType.PROCESS, BPMN_PROCESS_ID, Set.of(TENANT_ID));
  }

  @Test
  void shouldRetryOnByQueryVersionConflictAndEventuallySucceed() {
    // given -- simulates the conflict a concurrent write raises on the underlying delete-by-query
    // task, which the repository layer surfaces as OptimizeByQueryFailureException
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(new OptimizeByQueryFailureException("version conflict"))
        .doNothing()
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);

    // when
    handler.handle(job());

    // then
    verify(processInstanceWriter, times(2))
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
  }

  @Test
  void shouldRetryOnRetryableErrorAndEventuallySucceed() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .doThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .doNothing()
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);

    // when
    handler.handle(job());

    // then
    verify(processInstanceWriter, times(3))
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
  }

  @Test
  void shouldRetryOnRetryableErrorDuringDefinitionLookup() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .thenReturn(Optional.of(definition()));

    // when
    handler.handle(job());

    // then
    verify(processDefinitionReader, times(2)).getProcessDefinition(DEFINITION_ID, false);
    verify(processInstanceWriter).deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
  }

  @Test
  void shouldPropagateOnceRetryableErrorDuringDefinitionLookupExceedsMaxAttempts() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")));

    // when / then
    assertThatThrownBy(() -> handler.handle(job())).isInstanceOf(OptimizeRuntimeException.class);
    verify(processDefinitionReader, times(3)).getProcessDefinition(DEFINITION_ID, false);
    verify(processInstanceWriter, never()).deleteInstancesByDefinitionId(anyString(), anyString());
  }

  @Test
  void shouldRetryOnRetryableErrorNestedTwoLevelsDeep() {
    // given -- the repository layer can wrap a retryable error inside another
    // OptimizeRuntimeException (e.g. an async delete-by-query task failure)
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(
            new OptimizeRuntimeException(
                "outer", new OptimizeRuntimeException("inner", new SocketTimeoutException("boom"))))
        .doNothing()
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);

    // when
    handler.handle(job());

    // then
    verify(processInstanceWriter, times(2))
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
  }

  @Test
  void shouldPropagateOnceRetryableErrorExceedsMaxAttempts() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);

    // when / then
    assertThatThrownBy(() -> handler.handle(job())).isInstanceOf(OptimizeRuntimeException.class);
    verify(reportService, never()).clearCachedReportXml(anyString(), any());
  }

  @Test
  void shouldNotRetryNonRetryableError() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(new IllegalStateException("not retryable"))
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);

    // when / then
    assertThatThrownBy(() -> handler.handle(job())).isInstanceOf(IllegalStateException.class);
    verify(processInstanceWriter, times(1))
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
  }

  @Test
  void shouldResumeCleanupOnANewHandleCallAfterAPriorTerminalFailure() {
    // given -- the first attempt fails terminally right after the definition was marked deleted, so
    // nothing else in the cleanup tail ran
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(new IllegalStateException("not retryable"))
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
    assertThatThrownBy(() -> handler.handle(job())).isInstanceOf(IllegalStateException.class);
    verify(processDefinitionWriter).softDeleteDefinition(DEFINITION_ID);

    // when -- the job is retried: the definition lookup still finds the soft-deleted definition
    // and this time the instance deletion succeeds
    doNothing()
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
    handler.handle(job());

    // then -- the remainder of the cleanup tail now completes
    verify(reportService).clearCachedReportXml(BPMN_PROCESS_ID, TENANT_ID);
    verify(definitionService)
        .invalidateProcessDefinitionIfLatest(BPMN_PROCESS_ID, TENANT_ID, VERSION);
  }

  /**
   * A target and its overview rows are keyed on (tenantId, processDefinitionKey) and carry no
   * version, so they may only go once the last version for this tenant is gone — otherwise deleting
   * v1 of a process still running v2 would silently discard the user's target.
   */
  @Test
  void shouldDeleteBusinessValueDataWhenDeletingTheOnlyRemainingVersion() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));

    // when
    handler.handle(job());

    // then both indices are cleaned for this (tenant, definition) pair
    verify(businessValueTargetWriter).deleteForDefinition(TENANT_ID, BPMN_PROCESS_ID);
    verify(businessValueOverviewWriter).deleteForDefinition(TENANT_ID, BPMN_PROCESS_ID);
  }

  @Test
  void shouldNotDeleteBusinessValueDataWhenOtherVersionsRemainAfterDeletion() {
    // given another version of the same process survives for this tenant
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    when(definitionReader.getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Collections.singleton(TENANT_ID)))
        .thenReturn(List.of(new DefinitionVersionResponseDto("2", "2")));

    // when
    handler.handle(job());

    // then the target the user set on the still-live process is untouched
    verify(businessValueTargetWriter, never()).deleteForDefinition(anyString(), anyString());
    verify(businessValueOverviewWriter, never()).deleteForDefinition(anyString(), anyString());
  }

  /**
   * Single-tenant setups store a null tenantId. The writers own the decision that there is nothing
   * to delete in that case; asserted here that the handler passes it through untouched rather than
   * guarding or failing, so the rest of the cascade still completes.
   */
  @Test
  void shouldDelegateBusinessValueDeletionWithANullTenantWithoutFailingTheJob() {
    // given
    final ProcessDefinitionOptimizeDto definitionWithNullTenant = definition();
    definitionWithNullTenant.setTenantId(null);
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definitionWithNullTenant));
    when(definitionReader.getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Collections.singleton(null)))
        .thenReturn(List.of());

    // when / then the cascade completes
    handler.handle(job());

    verify(businessValueTargetWriter).deleteForDefinition(null, BPMN_PROCESS_ID);
    verify(businessValueOverviewWriter).deleteForDefinition(null, BPMN_PROCESS_ID);
    verify(reportService).clearCachedReportXml(BPMN_PROCESS_ID, null);
  }

  /**
   * A bulk delete reports per-item failures without any transport exception, so the plain
   * OptimizeRuntimeException the clients raise is not retryable and would fail the job on the first
   * attempt. The realistic cause is transient back-pressure (HTTP 429 under load), which is exactly
   * what the retries exist for — hence the dedicated exception type.
   */
  @Test
  void shouldRetryABulkFailureOnTheBusinessValueDeleteAndEventuallySucceed() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(new OptimizeBulkFailureException("rejected execution"))
        .doNothing()
        .when(businessValueTargetWriter)
        .deleteForDefinition(TENANT_ID, BPMN_PROCESS_ID);

    // when
    handler.handle(job());

    // then
    verify(businessValueTargetWriter, times(2)).deleteForDefinition(TENANT_ID, BPMN_PROCESS_ID);
    verify(businessValueOverviewWriter).deleteForDefinition(TENANT_ID, BPMN_PROCESS_ID);
  }

  /**
   * Business-value data is keyed on (tenantId, processDefinitionKey) and carries no version, so a
   * version imported while this job was running would have its target deleted by a decision taken
   * before it existed. A target is hand-entered and cannot be recomputed, so the check is repeated
   * immediately before the deletes.
   */
  @Test
  void shouldNotDeleteBusinessValueDataWhenANewVersionAppearsMidJob() {
    // given no other version when the cascade decides, but one by the time it reaches the
    // business-value step
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    when(definitionReader.getDefinitionVersions(
            DefinitionType.PROCESS, BPMN_PROCESS_ID, Collections.singleton(TENANT_ID)))
        .thenReturn(List.of(), List.of(new DefinitionVersionResponseDto("2", "2")));

    // when
    handler.handle(job());

    // then the newly imported version keeps its target
    verify(businessValueTargetWriter, never()).deleteForDefinition(anyString(), anyString());
    verify(businessValueOverviewWriter, never()).deleteForDefinition(anyString(), anyString());
    // and the rest of the cascade still ran
    verify(reportService).clearCachedReportXml(BPMN_PROCESS_ID, TENANT_ID);
  }

  private JobRegistryEntryDto job() {
    return new JobRegistryEntryDto(JobType.DELETE, EntityType.PROCESS_DEFINITION, DEFINITION_ID);
  }

  private ProcessDefinitionOptimizeDto definition() {
    final ProcessDefinitionOptimizeDto definition = new ProcessDefinitionOptimizeDto();
    definition.setId(DEFINITION_ID);
    definition.setKey(BPMN_PROCESS_ID);
    definition.setVersion("1");
    definition.setTenantId(TENANT_ID);
    return definition;
  }
}
