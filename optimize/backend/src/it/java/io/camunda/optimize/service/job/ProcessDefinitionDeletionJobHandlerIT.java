/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.job;

import static io.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static io.camunda.optimize.service.util.importing.ZeebeConstants.ZEEBE_DEFAULT_TENANT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.optimize.AbstractBrokerlessZeebeCCSMIT;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.dto.optimize.query.report.ReportDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessOverviewReader;
import io.camunda.optimize.service.db.reader.ReportReader;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.db.writer.ProcessOverviewWriter;
import io.camunda.optimize.service.db.writer.ReportWriter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProcessDefinitionDeletionJobHandlerIT extends AbstractBrokerlessZeebeCCSMIT {

  private ProcessDefinitionDeletionJobHandler handler;
  private ProcessDefinitionReader processDefinitionReader;
  private ProcessOverviewReader processOverviewReader;
  private ProcessOverviewWriter processOverviewWriter;
  private ReportReader reportReader;
  private ReportWriter reportWriter;
  private DefinitionService definitionService;
  private JobRegistryWriter jobRegistryWriter;
  private JobDispatcher jobDispatcher;

  @BeforeEach
  void setup() {
    handler = embeddedOptimizeExtension.getBean(ProcessDefinitionDeletionJobHandler.class);
    processDefinitionReader = embeddedOptimizeExtension.getBean(ProcessDefinitionReader.class);
    processOverviewReader = embeddedOptimizeExtension.getBean(ProcessOverviewReader.class);
    processOverviewWriter = embeddedOptimizeExtension.getBean(ProcessOverviewWriter.class);
    reportReader = embeddedOptimizeExtension.getBean(ReportReader.class);
    reportWriter = embeddedOptimizeExtension.getBean(ReportWriter.class);
    definitionService = embeddedOptimizeExtension.getBean(DefinitionService.class);
    jobRegistryWriter = embeddedOptimizeExtension.getBean(JobRegistryWriter.class);
    jobDispatcher = embeddedOptimizeExtension.getBean(JobDispatcher.class);
  }

  @Test
  void shouldDeleteAllInstancesOfTargetedVersionAndLeaveOtherVersionsUntouched() {
    // given
    final String bpmnProcessId = "definition-deletion-test-" + UUID.randomUUID();
    final String definitionIdV1 = bpmnProcessId + ":1:" + UUID.randomUUID();
    final String definitionIdV2 = bpmnProcessId + ":2:" + UUID.randomUUID();

    // multiple instances per version, so deletion is verified as a genuine delete-by-query over
    // all matching docs rather than a single-document coincidence
    final List<ProcessInstanceDto> instancesV1 =
        List.of(
            instanceFor(bpmnProcessId, definitionIdV1, "1"),
            instanceFor(bpmnProcessId, definitionIdV1, "1"),
            instanceFor(bpmnProcessId, definitionIdV1, "1"));
    final List<ProcessInstanceDto> instancesV2 =
        List.of(
            instanceFor(bpmnProcessId, definitionIdV2, "2"),
            instanceFor(bpmnProcessId, definitionIdV2, "2"));
    persistProcessInstances(Stream.concat(instancesV1.stream(), instancesV2.stream()).toList());

    processOverviewWriter.updateProcessOwnerIfNotSet(bpmnProcessId, "owner-1");
    refreshAllIndices();

    // when
    handler.handle(job(definitionIdV1));
    refreshAllIndices();

    // then
    assertThat(getProcessDefinition(definitionIdV1)).isEmpty();
    assertThat(getProcessDefinition(definitionIdV2)).isPresent();

    final List<ProcessInstanceDto> remainingInstances =
        databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
            getProcessInstanceIndexAliasName(bpmnProcessId), ProcessInstanceDto.class);
    assertThat(remainingInstances)
        .extracting(ProcessInstanceDto::getProcessInstanceId)
        .containsExactlyInAnyOrderElementsOf(
            instancesV2.stream().map(ProcessInstanceDto::getProcessInstanceId).toList());

    // ProcessOverview for the bpmnProcessId is untouched even though this was one of its versions
    assertThat(processOverviewReader.getProcessOverviewByKey(bpmnProcessId)).isPresent();
  }

  @Test
  void shouldLeaveOverviewUntouchedWhenDeletingTheOnlyRemainingVersion() {
    // given -- a single version of bpmnProcessId, so this deletion removes its last version
    final String bpmnProcessId = "definition-deletion-last-version-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessInstances(List.of(instanceFor(bpmnProcessId, definitionId, "1")));
    processOverviewWriter.updateProcessOwnerIfNotSet(bpmnProcessId, "owner-1");
    refreshAllIndices();

    // when
    handler.handle(job(definitionId));
    refreshAllIndices();

    // then
    assertThat(getProcessDefinition(definitionId)).isEmpty();
    assertThat(processOverviewReader.getProcessOverviewByKey(bpmnProcessId)).isPresent();
  }

  @Test
  void shouldClearCachedXmlOnReportsWhenLastVersionDeleted() {
    // given -- a single version of bpmnProcessId, so this deletion removes its last version
    final String bpmnProcessId = "definition-deletion-clear-xml-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessInstances(List.of(instanceFor(bpmnProcessId, definitionId, "1")));
    final String reportId = createSingleProcessReportWithCachedXml(bpmnProcessId);
    refreshAllIndices();

    // when
    handler.handle(job(definitionId));
    refreshAllIndices();

    // then
    assertThat(getProcessDefinition(definitionId)).isEmpty();
    assertThat(getCachedXml(reportId)).isNull();
  }

  @Test
  void shouldLeaveCachedXmlUntouchedWhenOtherVersionsRemain() {
    // given
    final String bpmnProcessId = "definition-deletion-keep-xml-test-" + UUID.randomUUID();
    final String definitionIdV1 = bpmnProcessId + ":1:" + UUID.randomUUID();
    final String definitionIdV2 = bpmnProcessId + ":2:" + UUID.randomUUID();
    persistProcessInstances(
        List.of(
            instanceFor(bpmnProcessId, definitionIdV1, "1"),
            instanceFor(bpmnProcessId, definitionIdV2, "2")));
    final String reportId = createSingleProcessReportWithCachedXml(bpmnProcessId);
    refreshAllIndices();

    // when -- only one of the two versions is deleted, so bpmnProcessId still has a version left
    handler.handle(job(definitionIdV1));
    refreshAllIndices();

    // then
    assertThat(getCachedXml(reportId)).isEqualTo("<definitions>cached</definitions>");
  }

  @Test
  void
      shouldClearCachedXmlWhenLastVersionForThisTenantIsDeletedEvenIfOtherTenantsStillHaveVersions() {
    // given the same bpmnProcessId has one version under tenant A and one under tenant B
    final String bpmnProcessId = "definition-deletion-multi-tenant-test-" + UUID.randomUUID();
    final String definitionIdTenantA = bpmnProcessId + ":1:tenant-a:" + UUID.randomUUID();
    final String definitionIdTenantB = bpmnProcessId + ":1:tenant-b:" + UUID.randomUUID();
    persistProcessDefinitions(
        List.of(
            definitionFor(bpmnProcessId, definitionIdTenantA, "1", ZEEBE_DEFAULT_TENANT_ID),
            definitionFor(bpmnProcessId, definitionIdTenantB, "1", "tenant-b")));

    // the report is scoped to tenant A specifically, i.e. the tenant whose data is about to
    // become entirely gone, even though the bpmnProcessId still lives on under tenant B
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey(bpmnProcessId);
    reportData.setTenantIds(List.of(ZEEBE_DEFAULT_TENANT_ID));
    reportData.getConfiguration().setXml("<definitions>cached</definitions>");
    final String reportId =
        reportWriter
            .createNewSingleProcessReport("demo", reportData, "Test Report", null, null)
            .getId();
    refreshAllIndices();

    // when -- only tenant A's (only) version is deleted
    handler.handle(job(definitionIdTenantA));
    refreshAllIndices();

    // then
    assertThat(getProcessDefinition(definitionIdTenantA)).isEmpty();
    assertThat(getProcessDefinition(definitionIdTenantB)).isPresent();
    assertThat(getCachedXml(reportId)).isNull();
  }

  @Test
  void shouldClearCachedXmlOnReportSharedAcrossTenantsWhenOnlyOneOfItsTenantsIsDeleted() {
    // given a report spanning both tenants, but only tenant A's (only) version is deleted
    final String bpmnProcessId = "definition-deletion-shared-report-test-" + UUID.randomUUID();
    final String definitionIdTenantA = bpmnProcessId + ":1:tenant-a:" + UUID.randomUUID();
    final String definitionIdTenantB = bpmnProcessId + ":1:tenant-b:" + UUID.randomUUID();
    persistProcessDefinitions(
        List.of(
            definitionFor(bpmnProcessId, definitionIdTenantA, "1", ZEEBE_DEFAULT_TENANT_ID),
            definitionFor(bpmnProcessId, definitionIdTenantB, "1", "tenant-b")));

    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey(bpmnProcessId);
    reportData.setTenantIds(List.of(ZEEBE_DEFAULT_TENANT_ID, "tenant-b"));
    reportData.getConfiguration().setXml("<definitions>cached</definitions>");
    final String reportId =
        reportWriter
            .createNewSingleProcessReport("demo", reportData, "Test Report", null, null)
            .getId();
    refreshAllIndices();

    // when -- only tenant A's (only) version is deleted; tenant B's version is untouched
    handler.handle(job(definitionIdTenantA));
    refreshAllIndices();

    // then
    assertThat(getProcessDefinition(definitionIdTenantA)).isEmpty();
    assertThat(getProcessDefinition(definitionIdTenantB)).isPresent();
    assertThat(getCachedXml(reportId)).isNull();
  }

  @Test
  void shouldClearCachedXmlOnComparisonReportWhereKeyIsFirstDefinition() {
    // given -- a comparison report referencing bpmnProcessId as its first of two definitions
    final String bpmnProcessId = "definition-deletion-comparison-first-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessInstances(List.of(instanceFor(bpmnProcessId, definitionId, "1")));

    final ProcessReportDataDto comparisonData = new ProcessReportDataDto();
    comparisonData.setProcessDefinitionKey(bpmnProcessId);
    comparisonData.getDefinitions().add(new ReportDataDefinitionDto("some-other-process"));
    comparisonData.getConfiguration().setXml("<definitions>cached</definitions>");
    final String reportId =
        reportWriter
            .createNewSingleProcessReport("demo", comparisonData, "Comparison Report", null, null)
            .getId();
    refreshAllIndices();

    // when
    handler.handle(job(definitionId));
    refreshAllIndices();

    // then
    assertThat(getProcessDefinition(definitionId)).isEmpty();
    assertThat(getCachedXml(reportId)).isNull();
  }

  @Test
  void shouldLeaveCachedXmlUntouchedOnComparisonReportWhereKeyIsNotFirstDefinition() {
    // given -- a comparison report referencing bpmnProcessId only as its second definition
    final String bpmnProcessId = "definition-deletion-not-first-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessInstances(List.of(instanceFor(bpmnProcessId, definitionId, "1")));

    final ProcessReportDataDto comparisonData = new ProcessReportDataDto();
    comparisonData.setProcessDefinitionKey("some-other-process");
    comparisonData.getDefinitions().add(new ReportDataDefinitionDto(bpmnProcessId));
    comparisonData.getConfiguration().setXml("<definitions>cached</definitions>");
    final String reportId =
        reportWriter
            .createNewSingleProcessReport("demo", comparisonData, "Comparison Report", null, null)
            .getId();
    refreshAllIndices();

    // when
    handler.handle(job(definitionId));
    refreshAllIndices();

    // then
    assertThat(getProcessDefinition(definitionId)).isEmpty();
    assertThat(getCachedXml(reportId)).isEqualTo("<definitions>cached</definitions>");
  }

  @Test
  void shouldEvictDefinitionFromCacheAfterDeletion() {
    // given
    final String bpmnProcessId = "definition-deletion-cache-evict-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessDefinitions(List.of(definitionFor(bpmnProcessId, definitionId, "1")));
    refreshAllIndices();
    // populate the cache before deletion
    definitionService.getCachedTenantToLatestDefinitionMap(DefinitionType.PROCESS, bpmnProcessId);

    // when
    handler.handle(job(definitionId));
    refreshAllIndices();

    // then -- a fresh cache fetch no longer returns the deleted definition
    assertThat(
            definitionService.getCachedTenantToLatestDefinitionMap(
                DefinitionType.PROCESS, bpmnProcessId))
        .isEmpty();
  }

  @Test
  void shouldNotThrowWhenNoInstancesWereEverImportedForTheDefinition() {
    // given -- the process-instance index for this bpmnProcessId was never created, since no
    // instance was ever persisted for it; exercises the "index doesn't exist" branch distinct
    // from "index exists but is now empty"
    final String bpmnProcessId = "definition-deletion-no-instances-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessDefinitions(List.of(definitionFor(bpmnProcessId, definitionId, "1")));

    // when / then
    assertThatCode(() -> handler.handle(job(definitionId))).doesNotThrowAnyException();
    refreshAllIndices();
    assertThat(getProcessDefinition(definitionId)).isEmpty();
  }

  @Test
  void shouldBeIdempotentWhenReInvokedAgainstAlreadyDeletedData() {
    // given
    final String bpmnProcessId = "definition-deletion-idempotency-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessInstances(List.of(instanceFor(bpmnProcessId, definitionId, "1")));
    refreshAllIndices();

    handler.handle(job(definitionId));
    refreshAllIndices();
    assertThat(getProcessDefinition(definitionId)).isEmpty();

    // when / then -- re-invoking against already-deleted data is a no-op, not an exception
    assertThatCode(() -> handler.handle(job(definitionId))).doesNotThrowAnyException();
  }

  @Test
  void shouldCompleteJobViaDispatcher() {
    // given
    final String bpmnProcessId = "definition-deletion-dispatch-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessInstances(List.of(instanceFor(bpmnProcessId, definitionId, "1")));
    refreshAllIndices();

    final JobRegistryEntryDto queued =
        jobRegistryWriter.createJobEntry(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, definitionId);

    // when
    jobDispatcher.dispatchNextBatch();
    refreshAllIndices();

    // then
    assertThat(getProcessDefinition(definitionId)).isEmpty();
    final JobRegistryReader jobRegistryReader =
        embeddedOptimizeExtension.getBean(JobRegistryReader.class);
    final var found =
        jobRegistryReader.findLastByJobTypeAndEntityId(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, definitionId);
    assertThat(found).isPresent();
    assertThat(found.get().getId()).isEqualTo(queued.getId());
    assertThat(found.get().getStatus()).isEqualTo(JobStatus.COMPLETED);
  }

  private Optional<ProcessDefinitionOptimizeDto> getProcessDefinition(final String definitionId) {
    return processDefinitionReader.getProcessDefinition(definitionId, false);
  }

  private String createSingleProcessReportWithCachedXml(final String bpmnProcessId) {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    reportData.setProcessDefinitionKey(bpmnProcessId);
    reportData.getConfiguration().setXml("<definitions>cached</definitions>");
    return reportWriter
        .createNewSingleProcessReport("demo", reportData, "Test Report", null, null)
        .getId();
  }

  private String getCachedXml(final String reportId) {
    final ReportDefinitionDto<?> report = reportReader.getReport(reportId).orElseThrow();
    return ((ProcessReportDataDto) report.getData()).getConfiguration().getXml();
  }

  private JobRegistryEntryDto job(final String definitionId) {
    return new JobRegistryEntryDto(JobType.DELETE, EntityType.PROCESS_DEFINITION, definitionId);
  }

  private ProcessInstanceDto instanceFor(
      final String bpmnProcessId, final String definitionId, final String version) {
    return completedInstance(bpmnProcessId)
        .processInstanceId(UUID.randomUUID().toString())
        .processDefinitionId(definitionId)
        .processDefinitionVersion(version)
        .build();
  }

  private ProcessDefinitionOptimizeDto definitionFor(
      final String bpmnProcessId, final String definitionId, final String version) {
    return definitionFor(bpmnProcessId, definitionId, version, ZEEBE_DEFAULT_TENANT_ID);
  }

  private ProcessDefinitionOptimizeDto definitionFor(
      final String bpmnProcessId,
      final String definitionId,
      final String version,
      final String tenantId) {
    return ProcessDefinitionOptimizeDto.builder()
        .id(definitionId)
        .key(bpmnProcessId)
        .version(version)
        .name(bpmnProcessId)
        .dataSource(new ZeebeDataSourceDto("test-source", 1))
        .tenantId(tenantId)
        .bpmn20Xml("<definitions/>")
        .build();
  }

  private static void refreshAllIndices() {
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }
}
