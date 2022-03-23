/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.SneakyThrows;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.dto.engine.HistoricIncidentEngineDto;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentDto;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentStatus;
import org.camunda.optimize.dto.optimize.persistence.incident.IncidentType;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer;
import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;
import org.camunda.optimize.service.importing.engine.EngineImportScheduler;
import org.camunda.optimize.service.importing.engine.mediator.CompletedIncidentEngineImportMediator;
import org.camunda.optimize.service.importing.engine.mediator.OpenIncidentEngineImportMediator;
import org.camunda.optimize.service.util.configuration.engine.DefaultTenant;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;
import static javax.ws.rs.HttpMethod.POST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.es.report.process.single.incident.duration.IncidentDataDeployer.IncidentProcessType.ONE_TASK;
import static org.camunda.optimize.service.util.InstanceIndexUtil.getProcessInstanceIndexAliasName;
import static org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtension.DEFAULT_ENGINE_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.util.BpmnModels.DEFAULT_PROCESS_ID;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK;
import static org.camunda.optimize.util.BpmnModels.SERVICE_TASK_ID_1;
import static org.camunda.optimize.util.BpmnModels.getTwoExternalTaskProcess;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.StringBody.subString;

public class IncidentImportIT extends AbstractImportIT {

  @Test
  public void openIncidentsAreImported() {
    // given
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getDefinitionKey()).isEqualTo(DEFAULT_PROCESS_ID);
          assertThat(incident.getDefinitionVersion()).isEqualTo("1");
          assertThat(incident.getTenantId()).isNull();
          assertThat(incident.getId()).isNotNull();
          assertThat(incident.getCreateTime()).isNotNull();
          assertThat(incident.getEndTime()).isNull();
          assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FAILED_EXTERNAL_TASK);
          assertThat(incident.getActivityId()).isEqualTo(SERVICE_TASK_ID_1);
          assertThat(incident.getFailedActivityId()).isNull();
          assertThat(incident.getIncidentMessage()).isNotNull();
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.OPEN);
        });
      });
  }

  @Test
  public void resolvedIncidentsAreImported() {
    // given
    incidentClient.deployAndStartProcessInstanceWithResolvedIncident();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getDefinitionKey()).isEqualTo(DEFAULT_PROCESS_ID);
          assertThat(incident.getDefinitionVersion()).isEqualTo("1");
          assertThat(incident.getTenantId()).isNull();
          assertThat(incident.getId()).isNotNull();
          assertThat(incident.getCreateTime()).isNotNull();
          assertThat(incident.getEndTime()).isNotNull();
          assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FAILED_EXTERNAL_TASK);
          assertThat(incident.getActivityId()).isEqualTo(SERVICE_TASK_ID_1);
          assertThat(incident.getFailedActivityId()).isNull();
          assertThat(incident.getIncidentMessage()).isNotNull();
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.RESOLVED);
        });
      });
  }

  @Test
  public void deletedIncidentsAreImported() {
    // given
    incidentClient.deployAndStartProcessInstanceWithDeletedIncident();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getDefinitionKey()).isEqualTo(DEFAULT_PROCESS_ID);
          assertThat(incident.getDefinitionVersion()).isEqualTo("1");
          assertThat(incident.getTenantId()).isNull();
          assertThat(incident.getId()).isNotNull();
          assertThat(incident.getCreateTime()).isNotNull();
          assertThat(incident.getEndTime()).isNotNull();
          assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FAILED_EXTERNAL_TASK);
          assertThat(incident.getActivityId()).isEqualTo(SERVICE_TASK_ID_1);
          assertThat(incident.getFailedActivityId()).isNull();
          assertThat(incident.getIncidentMessage()).isNotNull();
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.DELETED);
        });
      });
  }

  @Test
  public void incidentsWithDefaultTenantAreImported() {
    // given
    final String defaultTenantName = "jellyfish";
    embeddedOptimizeExtension.getDefaultEngineConfiguration()
      .setDefaultTenant(new DefaultTenant(defaultTenantName, defaultTenantName));
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .extracting(ProcessInstanceDto::getIncidents)
      .satisfies(incidents -> assertThat(incidents)
        .singleElement()
        .satisfies(incident -> assertThat(incident.getTenantId()).isEqualTo(defaultTenantName)));
  }

  @Test
  public void incidentsWithCustomTypeAreImported() {
    // given
    // @formatter:off
    IncidentDataDeployer.dataDeployer(incidentClient)
      .deployProcess(ONE_TASK)
      .startProcessInstance()
        .withOpenIncidentOfCustomType("myCustomIncidentType")
      .executeDeployment();
    // @formatter:on

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .singleElement()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getDefinitionKey()).isEqualTo(DEFAULT_PROCESS_ID);
          assertThat(incident.getDefinitionVersion()).isEqualTo("1");
          assertThat(incident.getTenantId()).isNull();
          assertThat(incident.getId()).isNotNull();
          assertThat(incident.getCreateTime()).isNotNull();
          assertThat(incident.getEndTime()).isNull();
          assertThat(incident.getIncidentType().getId()).isEqualTo("myCustomIncidentType");
          assertThat(incident.getActivityId()).isEqualTo(SERVICE_TASK_ID_1);
          assertThat(incident.getFailedActivityId()).isNull();
          assertThat(incident.getIncidentMessage()).isNotNull();
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.OPEN);
        });
      });
  }

  @Test
  public void incidentTenantDataIsImported() {
    // given
    final String tenantId1 = "tenantId1";
    engineIntegrationExtension.createTenant(tenantId1);
    incidentClient.deployAndStartProcessInstanceWithTenantAndWithOpenIncident(tenantId1);

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getDefinitionKey()).isEqualTo(DEFAULT_PROCESS_ID);
          assertThat(incident.getDefinitionVersion()).isEqualTo("1");
          assertThat(incident.getTenantId()).isEqualTo(tenantId1);
        });
      });
  }

  @Test
  public void importOpenIncidentFirstAndThenResolveIt() {
    // given  one open incident is created
    BpmnModelInstance incidentProcess = getTwoExternalTaskProcess();
    final ProcessInstanceEngineDto processInstanceEngineDto =
      engineIntegrationExtension.deployAndStartProcess(incidentProcess);
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());

    importAllEngineEntitiesFromScratch();

    // when we resolve the open incident and create another incident
    incidentClient.resolveOpenIncidents(processInstanceEngineDto.getId());
    incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());

    importAllEngineEntitiesFromLastIndex();

    // then there should be one complete one open incident
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(2);
        assertThat(processInstanceDto.getIncidents())
          .flatExtracting(IncidentDto::getIncidentStatus)
          .containsExactlyInAnyOrder(IncidentStatus.OPEN, IncidentStatus.RESOLVED);
      });
  }

  @Test
  @SneakyThrows
  public void openIncidentsDontOverwriteResolvedOnes() {
    // given
    final ProcessInstanceEngineDto processInstanceWithIncident =
      incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    manuallyAddAResolvedIncidentToElasticsearch(processInstanceWithIncident);

    // when we import the open incident
    importAllEngineEntitiesFromScratch();

    // then the open incident should not overwrite the existing resolved one
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(1)
      .first()
      .satisfies(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        processInstanceDto.getIncidents().forEach(incident -> {
          assertThat(incident.getEndTime()).isNotNull();
          assertThat(incident.getIncidentType()).isEqualTo(IncidentType.FAILED_EXTERNAL_TASK);
          assertThat(incident.getIncidentStatus()).isEqualTo(IncidentStatus.RESOLVED);
        });
      });
  }

  @Test
  public void multipleProcessInstancesWithIncidents_incidentsAreImportedToCorrectInstance() {
    // given
    final ProcessInstanceEngineDto processInstanceWithIncident =
      incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    incidentClient.startProcessInstanceAndCreateOpenIncident(processInstanceWithIncident.getDefinitionId());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(2)
      .allSatisfy(processInstanceDto -> {
        assertThat(processInstanceDto.getIncidents()).hasSize(1);
        assertThat(processInstanceDto.getIncidents())
          .flatExtracting(IncidentDto::getIncidentStatus)
          .containsExactlyInAnyOrder(IncidentStatus.OPEN);
      });
  }

  @Test
  public void adjustPageSize() {
    // given
    embeddedOptimizeExtension.getConfigurationService().setEngineImportIncidentMaxPageSize(1);
    BpmnModelInstance incidentProcess = getTwoExternalTaskProcess();
    final String definitionId = engineIntegrationExtension.deployProcessAndGetId(incidentProcess);
    incidentClient.startProcessInstanceAndCreateOpenIncident(definitionId);
    incidentClient.startProcessInstanceAndCreateOpenIncident(definitionId);
    incidentClient.startProcessInstanceAndCreateOpenIncident(definitionId);

    // when
    importAllEngineEntitiesFromScratch();
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(getIncidentCount()).isEqualTo(2L);

    // when
    importAllEngineEntitiesFromLastIndex();

    // then
    assertThat(getIncidentCount()).isEqualTo(3L);
  }

  @Test
  public void importOfOpenIncidents_isImportedOnNextSuccessfulAttemptAfterEsFailures() {
    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getIncidentCount()).isZero();

    // when updates to ES fails the first and succeeds the second time
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest processInstanceIndexMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + PROCESS_INSTANCE_INDEX_PREFIX));
    esMockServer
      .when(processInstanceIndexMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    importOpenIncidents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    esMockServer.verify(processInstanceIndexMatcher);

    // then the incident is stored after successful write
    assertThat(getIncidentCount()).isEqualTo(1L);
  }

  @Test
  public void importOfCompletedIncidents_isImportedOnNextSuccessfulAttemptAfterEsFailures() {
    // when
    importAllEngineEntitiesFromScratch();

    // then
    assertThat(getIncidentCount()).isZero();

    // when updates to ES fails the first and succeeds the second time
    incidentClient.deployAndStartProcessInstanceWithResolvedIncident();
    final ClientAndServer esMockServer = useAndGetElasticsearchMockServer();
    final HttpRequest processInstanceIndexMatcher = request()
      .withPath("/_bulk")
      .withMethod(POST)
      .withBody(subString("\"_index\":\"" + embeddedOptimizeExtension.getOptimizeElasticClient()
        .getIndexNameService()
        .getIndexPrefix() + "-" + PROCESS_INSTANCE_INDEX_PREFIX));
    esMockServer
      .when(processInstanceIndexMatcher, Times.once())
      .error(HttpError.error().withDropConnection(true));
    importResolvedIncidents();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    esMockServer.verify(processInstanceIndexMatcher);

    // then the incident is stored after successful write
    assertThat(getIncidentCount()).isEqualTo(1L);
  }

  @Test
  public void incidentWithoutProcessInstanceAssociationIsSkipped() {
    // given an incident is created which is not associated with a process instance
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    // the missing process instance association can happen in two cases:
    // 1. the engine history cleanup job is failing
    // 2. the process has a timer start event that fails after continuing with
    // the execution.
    // Both cases require a job executor which we don't have in our test setup. Therefore,
    // the process instance ID is removed manually to simulate those cases.
    engineDatabaseExtension.removeProcessInstanceIdFromAllHistoricIncidents();
    // and given an incident with a process instance id
    final ProcessInstanceEngineDto processInstanceEngineDto =
      incidentClient.deployAndStartProcessInstanceWithOpenIncident();

    final List<HistoricIncidentEngineDto> historicIncidents = engineIntegrationExtension.getHistoricIncidents();
    assertThat(historicIncidents)
      .hasSize(2)
      .extracting(HistoricIncidentEngineDto::getProcessInstanceId)
      .containsExactlyInAnyOrder(null, processInstanceEngineDto.getId());

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    assertThat(storedProcessInstances)
      .hasSize(2)
      .flatExtracting(ProcessInstanceDto::getIncidents)
      .hasSize(1)
      .satisfies(incident -> assertThat(incident)
        .extracting(IncidentDto::getId)
        .containsExactly(getIncidentIdForIncidentWithProcessInstanceId(historicIncidents))
      );
  }

  @Test
  public void incidentWithoutProcessDefinitionKeyCanBeImported() {
    // given
    incidentClient.deployAndStartProcessInstanceWithOpenIncident();
    engineDatabaseExtension.removeProcessDefinitionKeyFromAllHistoricIncidents();

    // when
    importAllEngineEntitiesFromScratch();

    // then
    final List<IncidentDto> incidents = elasticSearchIntegrationTestExtension.getAllProcessInstances()
      .stream()
      .flatMap(inst -> inst.getIncidents().stream())
      .collect(toList());
    assertThat(incidents).hasSize(1);
  }

  private String getIncidentIdForIncidentWithProcessInstanceId(final List<HistoricIncidentEngineDto> historicIncidents) {
    return historicIncidents.stream()
      .filter(i -> i.getProcessInstanceId() != null)
      .findFirst()
      .map(HistoricIncidentEngineDto::getId)
      .orElseThrow(() ->
                     new OptimizeIntegrationTestException("There should be one incident with a process instance id"));
  }

  private long getIncidentCount() {
    final List<ProcessInstanceDto> storedProcessInstances =
      elasticSearchIntegrationTestExtension.getAllProcessInstances();
    return storedProcessInstances.stream().mapToLong(p -> p.getIncidents().size()).sum();
  }

  @SneakyThrows
  private void importOpenIncidents() {
    for (EngineImportScheduler scheduler : embeddedOptimizeExtension.getImportSchedulerManager()
      .getEngineImportSchedulers()) {
      final ImportMediator mediator = scheduler
        .getImportMediators()
        .stream()
        .filter(engineImportMediator -> OpenIncidentEngineImportMediator.class.equals(engineImportMediator.getClass()))
        .findFirst()
        .orElseThrow(() -> new OptimizeIntegrationTestException("Could not find OpenIncidentEngineImportMediator!"));

      mediator.runImport().get(10, TimeUnit.SECONDS);
    }
  }

  @SneakyThrows
  private void importResolvedIncidents() {
    for (EngineImportScheduler scheduler : embeddedOptimizeExtension.getImportSchedulerManager()
      .getEngineImportSchedulers()) {
      final ImportMediator mediator = scheduler
        .getImportMediators()
        .stream()
        .filter(engineImportMediator -> CompletedIncidentEngineImportMediator.class.equals(engineImportMediator.getClass()))
        .findFirst()
        .orElseThrow(() ->
                       new OptimizeIntegrationTestException("Could not find CompletedIncidentEngineImportMediator!"));

      mediator.runImport().get(10, TimeUnit.SECONDS);
    }
  }

  private void manuallyAddAResolvedIncidentToElasticsearch(final ProcessInstanceEngineDto processInstanceWithIncident) {
    final HistoricIncidentEngineDto incidentEngineDto = engineIntegrationExtension.getHistoricIncidents()
      .stream()
      .findFirst()
      .orElseThrow(() -> new OptimizeIntegrationTestException("There should be at least one incident!"));

    final ProcessInstanceDto procInst = ProcessInstanceDto.builder()
      .processDefinitionId(processInstanceWithIncident.getDefinitionId())
      .processDefinitionKey(processInstanceWithIncident.getProcessDefinitionKey())
      .processDefinitionVersion(processInstanceWithIncident.getProcessDefinitionVersion())
      .processInstanceId(processInstanceWithIncident.getId())
      .startDate(OffsetDateTime.now())
      .endDate(OffsetDateTime.now())
      .incidents(Collections.singletonList(new IncidentDto(
        processInstanceWithIncident.getId(),
        processInstanceWithIncident.getProcessDefinitionKey(),
        processInstanceWithIncident.getProcessDefinitionVersion(),
        processInstanceWithIncident.getTenantId(),
        DEFAULT_ENGINE_ALIAS,
        incidentEngineDto.getId(),
        OffsetDateTime.now(),
        OffsetDateTime.now(),
        10L,
        IncidentType.FAILED_EXTERNAL_TASK, SERVICE_TASK, SERVICE_TASK, "Foo bar", IncidentStatus.RESOLVED
      )))
      .build();
    embeddedOptimizeExtension.getElasticSearchSchemaManager()
      .createIndexIfMissing(
        elasticSearchIntegrationTestExtension.getOptimizeElasticClient(),
        new ProcessInstanceIndex(processInstanceWithIncident.getProcessDefinitionKey()),
        Collections.singleton(PROCESS_INSTANCE_MULTI_ALIAS)
      );
    elasticSearchIntegrationTestExtension.addEntryToElasticsearch(
      getProcessInstanceIndexAliasName(processInstanceWithIncident.getProcessDefinitionKey()),
      processInstanceWithIncident.getId(),
      procInst
    );
  }

}
