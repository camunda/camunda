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
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
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
  private ProcessDefinitionDeletionJobHandler handler;

  @BeforeEach
  void init() {
    processDefinitionReader = mock(ProcessDefinitionReader.class);
    processInstanceWriter = mock(ProcessInstanceWriter.class);
    processDefinitionWriter = mock(ProcessDefinitionWriter.class);
    definitionReader = mock(DefinitionReader.class);
    reportService = mock(ReportService.class);
    definitionService = mock(DefinitionService.class);
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
            millis -> {});
  }

  @Test
  void shouldExposeJobTypeAndEntityType() {
    // when / then
    assertThat(handler.getJobType()).isEqualTo(JobType.DELETE);
    assertThat(handler.getEntityType()).isEqualTo(EntityType.PROCESS_DEFINITION);
  }

  @Test
  void shouldDeleteInstancesAndDefinitionWhenDefinitionExists() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));

    // when
    handler.handle(job());

    // then
    verify(processDefinitionWriter).markDefinitionAsDeleted(DEFINITION_ID);
    verify(processInstanceWriter).deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
  }

  @Test
  void shouldMarkDefinitionAsDeletedBeforeDeletingInstancesAndHardDeletingLast() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));

    // when
    handler.handle(job());

    // then
    final var order = inOrder(processDefinitionWriter, processInstanceWriter);
    order.verify(processDefinitionWriter).markDefinitionAsDeleted(DEFINITION_ID);
    order
        .verify(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
    order.verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
  }

  @Test
  void shouldNoOpWhenDefinitionNoLongerExists() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.empty());

    // when
    handler.handle(job());

    // then
    verify(processDefinitionWriter, never()).markDefinitionAsDeleted(anyString());
    verify(processInstanceWriter, never()).deleteInstancesByDefinitionId(anyString(), anyString());
    verify(processDefinitionWriter, never()).deleteDefinition(anyString());
    verify(reportService, never()).clearCachedReportXml(anyString(), any());
    verify(definitionService, never())
        .invalidateProcessDefinitionIfLatest(anyString(), any(), anyString());
  }

  @Test
  void shouldRetryOnRetryableErrorWhenMarkingDefinitionAsDeleted() {
    // given
    when(processDefinitionReader.getProcessDefinition(DEFINITION_ID, false))
        .thenReturn(Optional.of(definition()));
    doThrow(new OptimizeRuntimeException("transient", new SocketTimeoutException("boom")))
        .doNothing()
        .when(processDefinitionWriter)
        .markDefinitionAsDeleted(DEFINITION_ID);

    // when
    handler.handle(job());

    // then
    verify(processDefinitionWriter, times(2)).markDefinitionAsDeleted(DEFINITION_ID);
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
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

    // then
    verify(definitionReader)
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
    final var order = inOrder(definitionReader, processDefinitionWriter);
    order.verify(processDefinitionWriter).markDefinitionAsDeleted(DEFINITION_ID);
    order
        .verify(definitionReader)
        .getDefinitionVersions(DefinitionType.PROCESS, BPMN_PROCESS_ID, Set.of(TENANT_ID));
    order.verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
  }

  @Test
  void shouldRetryOnByQueryVersionConflictAndOnlyDeleteDefinitionAfterSuccessfulRetry() {
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
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
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
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
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
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
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
    verify(processDefinitionWriter, never()).deleteDefinition(anyString());
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
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
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
    verify(processDefinitionWriter, never()).deleteDefinition(anyString());
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
    verify(processDefinitionWriter, never()).deleteDefinition(anyString());
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
    verify(processDefinitionWriter).markDefinitionAsDeleted(DEFINITION_ID);
    verify(processDefinitionWriter, never()).deleteDefinition(anyString());

    // when -- the job is retried: the definition lookup still finds the soft-deleted definition
    // and this time the instance deletion succeeds
    doNothing()
        .when(processInstanceWriter)
        .deleteInstancesByDefinitionId(BPMN_PROCESS_ID, DEFINITION_ID);
    handler.handle(job());

    // then -- the remainder of the cleanup tail, including the final hard-delete, now completes
    verify(reportService).clearCachedReportXml(BPMN_PROCESS_ID, TENANT_ID);
    verify(definitionService)
        .invalidateProcessDefinitionIfLatest(BPMN_PROCESS_ID, TENANT_ID, VERSION);
    verify(processDefinitionWriter).deleteDefinition(DEFINITION_ID);
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
