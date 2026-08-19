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
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessOverviewReader;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.db.writer.ProcessOverviewWriter;
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
  private JobRegistryWriter jobRegistryWriter;
  private JobDispatcher jobDispatcher;

  @BeforeEach
  void setup() {
    handler = embeddedOptimizeExtension.getBean(ProcessDefinitionDeletionJobHandler.class);
    processDefinitionReader = embeddedOptimizeExtension.getBean(ProcessDefinitionReader.class);
    processOverviewReader = embeddedOptimizeExtension.getBean(ProcessOverviewReader.class);
    processOverviewWriter = embeddedOptimizeExtension.getBean(ProcessOverviewWriter.class);
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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    handler.handle(job(definitionIdV1));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    handler.handle(job(definitionId));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getProcessDefinition(definitionId)).isEmpty();
    assertThat(processOverviewReader.getProcessOverviewByKey(bpmnProcessId)).isPresent();
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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    assertThat(getProcessDefinition(definitionId)).isEmpty();
  }

  @Test
  void shouldBeIdempotentWhenReInvokedAgainstAlreadyDeletedData() {
    // given
    final String bpmnProcessId = "definition-deletion-idempotency-test-" + UUID.randomUUID();
    final String definitionId = bpmnProcessId + ":1:" + UUID.randomUUID();
    persistProcessInstances(List.of(instanceFor(bpmnProcessId, definitionId, "1")));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    handler.handle(job(definitionId));
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
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
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    final JobRegistryEntryDto queued =
        jobRegistryWriter.createJobEntry(
            JobType.DELETE, EntityType.PROCESS_DEFINITION, definitionId);

    // when
    jobDispatcher.dispatchNextBatch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

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
    return ProcessDefinitionOptimizeDto.builder()
        .id(definitionId)
        .key(bpmnProcessId)
        .version(version)
        .name(bpmnProcessId)
        .dataSource(new ZeebeDataSourceDto("test-source", 1))
        .tenantId(ZEEBE_DEFAULT_TENANT_ID)
        .bpmn20Xml("<definitions/>")
        .build();
  }
}
