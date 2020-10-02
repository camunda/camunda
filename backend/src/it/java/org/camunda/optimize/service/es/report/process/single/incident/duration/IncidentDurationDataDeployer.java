/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.process.single.incident.duration;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.test.engine.IncidentClient;
import org.camunda.optimize.test.util.DateCreationFreezer;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.DEFAULT_PROCESS_ID;

public class IncidentDurationDataDeployer {

  public static final String PROCESS_DEFINITION_KEY = DEFAULT_PROCESS_ID;

  private final IncidentClient incidentClient;
  private IncidentClient.IncidentProcessType processType;
  private final List<IncidentCreationHandler> incidentCreationHandlers = new ArrayList<>();

  private IncidentDurationDataDeployer(final IncidentClient incidentClient) {
    this.incidentClient = incidentClient;
  }

  public static AddProcessBuilder createIncidentProcess(final IncidentClient incidentClient) {
    return new AddProcessBuilder(new IncidentDurationDataDeployer(incidentClient));
  }

  @AllArgsConstructor
  public static class AddProcessBuilder {

    final IncidentDurationDataDeployer incidentDurationDataDeployer;

    public InstanceStarterBuilder deployProcess(final IncidentClient.IncidentProcessType processType) {
      incidentDurationDataDeployer.processType = processType;
      return new InstanceStarterBuilder(incidentDurationDataDeployer);
    }
  }

  @AllArgsConstructor
  public static class InstanceStarterBuilder {
    final IncidentDurationDataDeployer incidentDurationDataDeployer;

    public IncidentTypeDeciderBuilder startProcessInstance() {
      return new IncidentTypeDeciderBuilder(incidentDurationDataDeployer);
    }
  }

  @AllArgsConstructor
  public static class IncidentTypeDeciderBuilder {
    final IncidentDurationDataDeployer incidentDurationDataDeployer;

    public OpenIncidentCreationDateFromNowBuilder withOpenIncident() {
      OpenIncidentCreationHandler openIncidentCreationHandler =
        new OpenIncidentCreationHandler(incidentDurationDataDeployer.incidentClient);
      return new OpenIncidentCreationDateFromNowBuilder(
        incidentDurationDataDeployer,
        openIncidentCreationHandler
      );
    }

    public IncidentDurationBuilder withResolvedIncident() {
      ResolvedIncidentCreationHandler incidentCreationHandler =
        new ResolvedIncidentCreationHandler(incidentDurationDataDeployer.incidentClient);
      return new IncidentDurationBuilder(incidentDurationDataDeployer, incidentCreationHandler);
    }

    public IncidentDurationBuilder withDeletedIncident() {
      DeletedIncidentCreationHandler incidentCreationHandler =
        new DeletedIncidentCreationHandler(incidentDurationDataDeployer.incidentClient);
      return new IncidentDurationBuilder(incidentDurationDataDeployer, incidentCreationHandler);
    }

    public IncidentDataDeploymentExecutor withoutIncident() {
      NoIncidentCreationHandler incidentCreationHandler =
        new NoIncidentCreationHandler(incidentDurationDataDeployer.incidentClient);
      incidentDurationDataDeployer.incidentCreationHandlers.add(incidentCreationHandler);
      return new IncidentDataDeploymentExecutor(incidentDurationDataDeployer);
    }
  }

  @AllArgsConstructor
  public static class OpenIncidentCreationDateFromNowBuilder {
    final IncidentDurationDataDeployer incidentDurationDataDeployer;
    final IncidentCreationHandler incidentCreationHandler;

    public IncidentDataDeploymentExecutor withIncidentDurationInSec(final Long durationInSec) {
      incidentCreationHandler.durationInSec = durationInSec;
      incidentDurationDataDeployer.incidentCreationHandlers.add(incidentCreationHandler);
      return new IncidentDataDeploymentExecutor(incidentDurationDataDeployer);
    }
  }

  @AllArgsConstructor
  public static class IncidentDurationBuilder {
    final IncidentDurationDataDeployer incidentDurationDataDeployer;
    final IncidentCreationHandler incidentCreationHandler;

    public IncidentDataDeploymentExecutor withIncidentDurationInSec(final Long durationInSec) {
      incidentCreationHandler.durationInSec = durationInSec;
      incidentDurationDataDeployer.incidentCreationHandlers.add(incidentCreationHandler);
      return new IncidentDataDeploymentExecutor(incidentDurationDataDeployer);
    }
  }

  @AllArgsConstructor
  public static class IncidentDataDeploymentExecutor {
    final IncidentDurationDataDeployer incidentDurationDataDeployer;

    public IncidentTypeDeciderBuilder startProcessInstance() {
      return new IncidentTypeDeciderBuilder(incidentDurationDataDeployer);
    }

    public void executeDeployment() {
      final IncidentClient incidentClient = incidentDurationDataDeployer.incidentClient;
      final String processId = incidentClient.deployProcessAndReturnId(incidentDurationDataDeployer.processType);

      assertThat(incidentDurationDataDeployer.incidentCreationHandlers).hasSizeGreaterThanOrEqualTo(1);
      OffsetDateTime creationDate = DateCreationFreezer.dateFreezer().freezeDateAndReturn();
      for (IncidentCreationHandler incidentCreationHandler :
        incidentDurationDataDeployer.incidentCreationHandlers) {
        final ProcessInstanceEngineDto processInstanceEngineDto =
          incidentCreationHandler.startProcessInstanceAndCreateIncident(processId);
        incidentCreationHandler.adjustIncidentDate(processInstanceEngineDto.getId(), creationDate);
      }
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
      incidentClient.changeIncidentCreationDate(processInstanceId, creationDate);
      DateCreationFreezer.dateFreezer(creationDate.plusSeconds(durationInSec)).freezeDateAndReturn();
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
      final OffsetDateTime endDate = creationDate.plusSeconds(durationInSec);
      incidentClient.changeIncidentCreationAndEndDate(processInstanceId, creationDate, endDate);
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
      final OffsetDateTime endDate = creationDate.plusSeconds(durationInSec);
      incidentClient.changeIncidentCreationAndEndDate(processInstanceId, creationDate, endDate);
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
}
