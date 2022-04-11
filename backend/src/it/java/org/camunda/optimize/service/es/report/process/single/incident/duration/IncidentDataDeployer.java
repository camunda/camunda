/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.process.single.incident.duration;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.engine.IncidentClient;
import org.camunda.optimize.test.util.DateCreationFreezer;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.DEFAULT_PROCESS_ID;
import static org.camunda.optimize.util.BpmnModels.getExternalTaskProcess;
import static org.camunda.optimize.util.BpmnModels.getTwoExternalTaskProcess;
import static org.camunda.optimize.util.BpmnModels.getTwoParallelExternalTaskProcess;

public class IncidentDataDeployer {

  public static final String PROCESS_DEFINITION_KEY = DEFAULT_PROCESS_ID;

  private final IncidentClient incidentClient;
  private BpmnModelInstance process;
  private final List<IncidentCreationHandler> incidentCreationHandlers = new ArrayList<>();

  private IncidentDataDeployer(final IncidentClient incidentClient) {
    this.incidentClient = incidentClient;
  }

  public static AddProcessBuilder dataDeployer(final IncidentClient incidentClient) {
    return new AddProcessBuilder(new IncidentDataDeployer(incidentClient));
  }

  public enum IncidentProcessType {
    ONE_TASK,
    TWO_SEQUENTIAL_TASKS,
    TWO_PARALLEL_TASKS
  }


  @RequiredArgsConstructor
  public static class AddProcessBuilder {

    final IncidentDataDeployer incidentDataDeployer;

    private String key = IncidentDataDeployer.PROCESS_DEFINITION_KEY;

    public AddProcessBuilder key(final String key) {
      this.key = key;
      return this;
    }

    public InstanceStarterBuilder deployProcess(final IncidentProcessType processType) {
      incidentDataDeployer.process = processTypeToModelInstance(processType);
      return new InstanceStarterBuilder(incidentDataDeployer);
    }

    private BpmnModelInstance processTypeToModelInstance(final IncidentProcessType incidentProcessType) {
      BpmnModelInstance process;
      switch (incidentProcessType) {
        case ONE_TASK:
          process = getExternalTaskProcess(key);
          break;
        case TWO_SEQUENTIAL_TASKS:
          process = getTwoExternalTaskProcess(key);
          break;
        case TWO_PARALLEL_TASKS:
          process = getTwoParallelExternalTaskProcess(key);
          break;
        default:
          throw new OptimizeIntegrationTestException("Unknown incident process type!");
      }
      return process;
    }
  }

  @AllArgsConstructor
  public static class InstanceStarterBuilder {
    final IncidentDataDeployer incidentDataDeployer;

    public IncidentStatusDeciderBuilder startProcessInstance() {
      return new IncidentStatusDeciderBuilder(incidentDataDeployer);
    }
  }

  @AllArgsConstructor
  public static class IncidentStatusDeciderBuilder {
    final IncidentDataDeployer incidentDataDeployer;

    public IncidentDurationDeploymentBuilder withOpenIncident() {
      OpenIncidentCreationHandler openIncidentCreationHandler =
        new OpenIncidentCreationHandler(incidentDataDeployer.incidentClient);
      return new IncidentDurationDeploymentBuilder(incidentDataDeployer, openIncidentCreationHandler);
    }

    public IncidentDurationDeploymentBuilder withResolvedIncident() {
      ResolvedIncidentCreationHandler incidentCreationHandler =
        new ResolvedIncidentCreationHandler(incidentDataDeployer.incidentClient);
      return new IncidentDurationDeploymentBuilder(incidentDataDeployer, incidentCreationHandler);
    }

    public IncidentDurationDeploymentBuilder withDeletedIncident() {
      DeletedIncidentCreationHandler incidentCreationHandler =
        new DeletedIncidentCreationHandler(incidentDataDeployer.incidentClient);
      return new IncidentDurationDeploymentBuilder(incidentDataDeployer, incidentCreationHandler);
    }

    public IncidentDurationDeploymentBuilder withResolvedAndOpenIncident() {
      ResolvedAndOpenIncidentCreationHandler resolvedAndOpenIncidentCreationHandler =
        new ResolvedAndOpenIncidentCreationHandler(incidentDataDeployer.incidentClient);
      return new IncidentDurationDeploymentBuilder(incidentDataDeployer, resolvedAndOpenIncidentCreationHandler);
    }

    public IncidentDataDeploymentExecutor withoutIncident() {
      NoIncidentCreationHandler incidentCreationHandler =
        new NoIncidentCreationHandler(incidentDataDeployer.incidentClient);
      incidentDataDeployer.incidentCreationHandlers.add(incidentCreationHandler);
      return new IncidentDataDeploymentExecutor(incidentDataDeployer);
    }

    public IncidentDurationDeploymentBuilder withOpenIncidentOfCustomType(final String incidentType) {
      CustomOpenIncidentTypeCreationHandler openIncidentCreationHandler =
        new CustomOpenIncidentTypeCreationHandler(incidentDataDeployer.incidentClient, incidentType);
      return new IncidentDurationDeploymentBuilder(incidentDataDeployer, openIncidentCreationHandler);
    }
  }

  public static class IncidentDurationDeploymentBuilder extends IncidentDataDeploymentExecutor {
    final IncidentCreationHandler incidentCreationHandler;

    public IncidentDurationDeploymentBuilder(final IncidentDataDeployer incidentDataDeployer,
                                             final IncidentCreationHandler incidentCreationHandler) {
      super(incidentDataDeployer);
      incidentDataDeployer.incidentCreationHandlers.add(incidentCreationHandler);
      this.incidentCreationHandler = incidentCreationHandler;
    }

    public IncidentDataDeploymentExecutor withIncidentDurationInSec(final Long durationInSec) {
      incidentCreationHandler.durationInSec = durationInSec;
      return new IncidentDataDeploymentExecutor(incidentDataDeployer);
    }
  }

  @AllArgsConstructor
  public static class IncidentDataDeploymentExecutor {
    final IncidentDataDeployer incidentDataDeployer;

    public IncidentStatusDeciderBuilder startProcessInstance() {
      return new IncidentStatusDeciderBuilder(incidentDataDeployer);
    }

    public List<ProcessInstanceEngineDto> executeDeployment() {
      final IncidentClient incidentClient = incidentDataDeployer.incidentClient;
      final String processId = incidentClient.deployProcessAndReturnId(incidentDataDeployer.process);

      assertThat(incidentDataDeployer.incidentCreationHandlers).hasSizeGreaterThanOrEqualTo(1);
      OffsetDateTime creationDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
      List<ProcessInstanceEngineDto> deployedInstances = new ArrayList<>();
      for (IncidentCreationHandler incidentCreationHandler : incidentDataDeployer.incidentCreationHandlers) {
        final ProcessInstanceEngineDto processInstanceEngineDto =
          incidentCreationHandler.startProcessInstanceAndCreateIncident(processId);
        incidentCreationHandler.adjustIncidentDate(processInstanceEngineDto.getId(), creationDate);
        deployedInstances.add(processInstanceEngineDto);
      }
      return deployedInstances;
    }
  }

  @RequiredArgsConstructor
  private static abstract class IncidentCreationHandler {

    final IncidentClient incidentClient;
    Long durationInSec;

    abstract ProcessInstanceEngineDto startProcessInstanceAndCreateIncident(final String processDefinitionId);

    abstract void adjustIncidentDate(final String processInstanceId, final OffsetDateTime date);
  }

  private static class OpenIncidentCreationHandler extends IncidentCreationHandler {

    public OpenIncidentCreationHandler(final IncidentClient incidentClient) {
      super(incidentClient);
    }

    @Override
    ProcessInstanceEngineDto startProcessInstanceAndCreateIncident(final String processDefinitionId) {
      return incidentClient.startProcessInstanceAndCreateOpenIncident(processDefinitionId);
    }

    @Override
    void adjustIncidentDate(final String processInstanceId, final OffsetDateTime creationDate) {
      if (durationInSec != null) {
        incidentClient.changeIncidentCreationDate(processInstanceId, creationDate);
        DateCreationFreezer.dateFreezer(creationDate.plusSeconds(durationInSec)).freezeDateAndReturn();
      }
    }
  }

  private static class ResolvedIncidentCreationHandler extends IncidentCreationHandler {

    public ResolvedIncidentCreationHandler(final IncidentClient incidentClient) {
      super(incidentClient);
    }

    @Override
    ProcessInstanceEngineDto startProcessInstanceAndCreateIncident(final String processDefinitionId) {
      return incidentClient.startProcessInstanceAndCreateResolvedIncident(processDefinitionId);
    }

    @Override
    void adjustIncidentDate(final String processInstanceId, final OffsetDateTime creationDate) {
      if (durationInSec != null) {
        final OffsetDateTime endDate = creationDate.plusSeconds(durationInSec);
        incidentClient.changeIncidentCreationAndEndDateIfPresent(processInstanceId, creationDate, endDate);
      }
    }
  }

  private static class DeletedIncidentCreationHandler extends IncidentCreationHandler {

    public DeletedIncidentCreationHandler(final IncidentClient incidentClient) {
      super(incidentClient);
    }

    @Override
    ProcessInstanceEngineDto startProcessInstanceAndCreateIncident(final String processDefinitionId) {
      return incidentClient.startProcessInstanceAndCreateDeletedIncident(processDefinitionId);
    }

    @Override
    void adjustIncidentDate(final String processInstanceId, final OffsetDateTime creationDate) {
      if (durationInSec != null) {
        final OffsetDateTime endDate = creationDate.plusSeconds(durationInSec);
        incidentClient.changeIncidentCreationAndEndDateIfPresent(processInstanceId, creationDate, endDate);
      }
    }
  }

  private static class ResolvedAndOpenIncidentCreationHandler extends IncidentCreationHandler {

    public ResolvedAndOpenIncidentCreationHandler(final IncidentClient incidentClient) {
      super(incidentClient);
    }

    @Override
    ProcessInstanceEngineDto startProcessInstanceAndCreateIncident(final String processDefinitionId) {
      final ProcessInstanceEngineDto processInstanceEngineDto =
        incidentClient.startProcessInstanceAndCreateResolvedIncident(processDefinitionId);
      incidentClient.createOpenIncidentForInstancesWithBusinessKey(processInstanceEngineDto.getBusinessKey());
      return processInstanceEngineDto;
    }

    @Override
    void adjustIncidentDate(final String processInstanceId, final OffsetDateTime creationDate) {
      if (durationInSec != null) {
        final OffsetDateTime endDate = creationDate.plusSeconds(durationInSec);
        incidentClient.changeIncidentCreationAndEndDateIfPresent(processInstanceId, creationDate, endDate);
        DateCreationFreezer.dateFreezer(creationDate.plusSeconds(durationInSec)).freezeDateAndReturn();
      }
    }
  }

  private static class NoIncidentCreationHandler extends IncidentCreationHandler {

    public NoIncidentCreationHandler(final IncidentClient incidentClient) {
      super(incidentClient);
    }

    @Override
    ProcessInstanceEngineDto startProcessInstanceAndCreateIncident(final String processDefinitionId) {
      return incidentClient.startProcessInstanceWithoutIncident(processDefinitionId);
    }

    @Override
    void adjustIncidentDate(final String processInstanceId, final OffsetDateTime creationDate) {
      // nothing to do here as there's no incident
    }
  }

  private static class CustomOpenIncidentTypeCreationHandler extends IncidentCreationHandler {

    private final String customIncidentType;

    public CustomOpenIncidentTypeCreationHandler(final IncidentClient incidentClient,
                                                 final String customIncidentType) {
      super(incidentClient);
      this.customIncidentType = customIncidentType;
    }

    @Override
    ProcessInstanceEngineDto startProcessInstanceAndCreateIncident(final String processDefinitionId) {
      return incidentClient.startProcessInstanceWithCustomIncident(processDefinitionId, this.customIncidentType);
    }

    @Override
    void adjustIncidentDate(final String processInstanceId, final OffsetDateTime creationDate) {
      if (durationInSec != null) {
        incidentClient.changeIncidentCreationDate(processInstanceId, creationDate);
        DateCreationFreezer.dateFreezer(creationDate.plusSeconds(durationInSec)).freezeDateAndReturn();
      }
    }
  }
}
