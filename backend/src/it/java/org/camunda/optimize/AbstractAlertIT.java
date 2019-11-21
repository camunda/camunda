/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetup;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.AuthorizationDto;
import org.camunda.optimize.dto.engine.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.alert.SyncListener;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtension;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.optimize.CollectionClient;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.quartz.JobKey;
import org.quartz.SchedulerException;

import javax.ws.rs.core.Response;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.NONE;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractAlertIT extends AbstractIT {

  @RegisterExtension
  @Order(4)
  public EngineDatabaseExtension engineDatabaseExtension = new EngineDatabaseExtension(engineIntegrationExtension.getEngineName());

  protected CollectionClient collectionClient = new CollectionClient(embeddedOptimizeExtension);

  protected String createAlert(AlertCreationDto simpleAlert) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .buildCreateAlertRequest(simpleAlert)
      .execute(IdDto.class, 200)
      .getId();
  }

  protected String createAlertForReport(String reportId) {
    AlertCreationDto alertDto = createSimpleAlert(reportId);
    return createAlert(alertDto);
  }

  protected void triggerAndCompleteCheckJob(String id) throws SchedulerException, InterruptedException {
    this.triggerAndCompleteJob(checkJobKey(id));
  }

  private void triggerAndCompleteJob(JobKey jobKey) throws SchedulerException, InterruptedException {
    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeExtension.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);
    //trigger job
    embeddedOptimizeExtension.getAlertService().getScheduler().triggerJob(jobKey);
    //wait for job to finish
    jobListener.getDone().await();
    embeddedOptimizeExtension.getAlertService()
      .getScheduler()
      .getListenerManager()
      .removeJobListener(jobListener.getName());
  }

  protected void triggerAndCompleteReminderJob(String id) throws SchedulerException, InterruptedException {
    this.triggerAndCompleteJob(reminderJobKey(id));
  }

  private JobKey reminderJobKey(String id) {
    return new JobKey(id + "-reminder-job", "statusReminder-job");
  }

  protected Integer getOptimizeHttpPort() {
    return embeddedOptimizeExtension.getConfigurationService().getContainerHttpPort().orElse(8090);
  }

  protected OffsetDateTime getNextReminderExecutionTime(String id) throws SchedulerException {
    Date nextTimeReminderIsExecuted = embeddedOptimizeExtension
      .getAlertService()
      .getScheduler()
      .getTriggersOfJob(reminderJobKey(id))
      .get(0)
      .getNextFireTime();
    return OffsetDateTime.ofInstant(nextTimeReminderIsExecuted.toInstant(), ZoneId.systemDefault());
  }

  protected ProcessInstanceEngineDto deployWithTimeShift(long daysToShift, long durationInSec) throws SQLException {
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstance = deployAndStartSimpleProcess();
    adjustProcessInstanceDates(processInstance.getId(), startDate, daysToShift, durationInSec);
    embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    return processInstance;
  }

  protected ProcessInstanceEngineDto deployAndStartSimpleProcess() {
    return deployAndStartSimpleProcessWithVariables(new HashMap<>());
  }

  private ProcessInstanceEngineDto deployAndStartSimpleProcessWithVariables(Map<String, Object> variables) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess("aProcess")
      .name("aProcessName")
      .startEvent()
      .endEvent()
      .done();
    return engineIntegrationExtension.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          long durationInSec) throws SQLException {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    engineDatabaseExtension.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
    engineDatabaseExtension.changeProcessInstanceEndDate(
      processInstanceId,
      shiftedStartDate.plusSeconds(durationInSec)
    );
  }

  private JobKey checkJobKey(String id) {
    return new JobKey(id + "-check-job", "statusCheck-job");
  }

  protected AlertCreationDto createBasicAlertWithReminder() {
    AlertCreationDto simpleAlert = setupBasicProcessAlert();
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit("Seconds");
    simpleAlert.setReminder(reminderInterval);
    return simpleAlert;
  }

  protected AlertCreationDto setupBasicDecisionAlert() {
    String collectionId = collectionClient.createNewCollectionWithDefaultDecisionScope();
    String id = createNumberReportForCollection(collectionId, DECISION);
    return createSimpleAlert(id);
  }

  protected AlertCreationDto setupBasicProcessAlert() {
    return setupBasicProcessAlertAsUser("aProcess", DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  protected AlertCreationDto setupBasicProcessAlertAsUser(final String definitionKey,
                                                          final String user,
                                                          final String password) {
    String collectionId = collectionClient.createNewCollection(user, password);
    final CollectionScopeEntryDto scopeEntry = new CollectionScopeEntryDto(PROCESS, definitionKey, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollectionWithUser(collectionId, scopeEntry, user, password);
    String reportId = createNumberReportForCollection(
      definitionKey,
      collectionId,
      PROCESS,
      user,
      password
    );
    return createSimpleAlert(reportId);
  }

  protected AlertCreationDto createSimpleAlert(String reportId) {
    return createSimpleAlert(reportId, 1, "Seconds");
  }

  protected AlertCreationDto createSimpleAlert(String reportId, int intervalValue, String unit) {
    AlertCreationDto alertCreationDto = new AlertCreationDto();

    AlertInterval interval = new AlertInterval();
    interval.setUnit(unit);
    interval.setValue(intervalValue);
    alertCreationDto.setCheckInterval(interval);
    alertCreationDto.setThreshold(0);
    alertCreationDto.setThresholdOperator(">");
    alertCreationDto.setEmail("test@camunda.com");
    alertCreationDto.setName("test alert");
    alertCreationDto.setReportId(reportId);

    return alertCreationDto;
  }

  protected String createNewProcessReportAsUser(final String collectionId,
                                              final ProcessDefinitionEngineDto processDefinition) {
    SingleProcessReportDefinitionDto procReport = getProcessNumberReportDefinitionDto(
      collectionId,
      processDefinition
    );
    return createNewProcessReportAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD, procReport);
  }

  private String createNewProcessReportAsUser(final String user, final String password, final String collectionId,
                                              final ProcessDefinitionEngineDto processDefinition) {
    SingleProcessReportDefinitionDto procReport = getProcessNumberReportDefinitionDto(
      collectionId,
      processDefinition
    );
    return createNewProcessReportAsUser(user, password, procReport);
  }

  private String createNewProcessReportAsUser(final String user, final String password,
                                              final SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewDecisionReportAsUser(final String user, final String password, final String collectionId,
                                               final DecisionDefinitionEngineDto decisionDefinitionDto) {
    SingleDecisionReportDefinitionDto decReport = getDecisionNumberReportDefinitionDto(
      collectionId,
      decisionDefinitionDto
    );
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateSingleDecisionReportRequest(decReport)
      .execute(IdDto.class, 200)
      .getId();
  }

  protected String createAndStoreDurationNumberReportInNewCollection(final ProcessInstanceEngineDto instanceEngineDto) {
    String collectionId = collectionClient.createNewCollectionWithProcessScope(instanceEngineDto);
    return createAndStoreDurationNumberReport(
      collectionId,
      instanceEngineDto.getProcessDefinitionKey(),
      instanceEngineDto.getProcessDefinitionVersion()
    );
  }

  private String createAndStoreDurationNumberReport(final String collectionId, final String processDefinitionKey,
                                                    final String processDefinitionVersion) {
    return createAndStoreDurationNumberReportAsUser(
      collectionId, processDefinitionKey, processDefinitionVersion, DEFAULT_USERNAME, DEFAULT_PASSWORD
    );
  }

  private String createAndStoreDurationNumberReportAsUser(final String collectionId,
                                                          final String processDefinitionKey,
                                                          final String processDefinitionVersion,
                                                          final String user,
                                                          final String password) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto =
      getDurationReportDefinitionDto(collectionId, processDefinitionKey, processDefinitionVersion);
    return createNewProcessReportAsUser(user, password, singleProcessReportDefinitionDto);
  }

  protected String createNumberReportForCollection(final String collectionId, final DefinitionType definitionType) {
    return createNumberReportForCollection(
      DEFAULT_DEFINITION_KEY,
      collectionId,
      definitionType,
      DEFAULT_USERNAME,
      DEFAULT_PASSWORD
    );
  }

  private String createNumberReportForCollection(final String definitionKey, final String collectionId,
                                                 final DefinitionType definitionType,
                                                 final String user, final String password) {
    switch (definitionType) {
      case PROCESS:
        ProcessDefinitionEngineDto processDefinition = deployAndStartSimpleServiceTaskProcess(definitionKey);
        embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
        elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
        return createNewProcessReportAsUser(user, password, collectionId, processDefinition);

      case DECISION:
        DecisionDefinitionEngineDto decisionDefinitionDto = deployAndStartSimpleDecisionDefinition(definitionKey);
        embeddedOptimizeExtension.importAllEngineEntitiesFromScratch();
        elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
        return createNewDecisionReportAsUser(user, password, collectionId, decisionDefinitionDto);

      default:
        throw new OptimizeRuntimeException("Unknown resource type provided.");
    }
  }

  private SingleDecisionReportDefinitionDto getDecisionNumberReportDefinitionDto(String collectionId,
                                                                                 DecisionDefinitionEngineDto decisionDefinitionDto) {
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto.getVersion());
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();

    SingleDecisionReportDefinitionDto report = new SingleDecisionReportDefinitionDto();
    report.setData(reportData);
    report.setId("something");
    report.setLastModifier("something");
    report.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    report.setCreated(someDate);
    report.setLastModified(someDate);
    report.setOwner("something");
    report.setCollectionId(collectionId);

    return report;
  }

  protected SingleProcessReportDefinitionDto getProcessNumberReportDefinitionDto(String collectionId,
                                                                                 ProcessDefinitionEngineDto processDefinition) {
    return getProcessNumberReportDefinitionDto(
      collectionId,
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion())
    );
  }


  private SingleProcessReportDefinitionDto getProcessNumberReportDefinitionDto(String collectionId,
                                                                               String processDefinitionKey,
                                                                               String processDefinitionVersion) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.COUNT_PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setName("something");
    report.setCollectionId(collectionId);
    return report;
  }

  protected void updateSingleProcessReport(String id,
                                           SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildUpdateSingleProcessReportRequest(id, updatedReport, true)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  protected ProcessDefinitionEngineDto deployAndStartSimpleServiceTaskProcess() {
    return deployAndStartSimpleServiceTaskProcess("aProcess");
  }

  protected ProcessDefinitionEngineDto deployAndStartSimpleServiceTaskProcess(final String definitionKey) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess(definitionKey);
    engineIntegrationExtension.startProcessInstance(processDefinition.getId());
    return processDefinition;
  }

  protected ProcessDefinitionEngineDto deploySimpleServiceTaskProcess(String definitionKey) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(processModel);
  }

  private SingleProcessReportDefinitionDto getDurationReportDefinitionDto(String collectionId,
                                                                          String processDefinitionKey,
                                                                          String processDefinitionVersion) {
    ProcessReportDataDto reportData = ProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionDto report = new SingleProcessReportDefinitionDto();
    report.setData(reportData);
    report.setName("something");
    report.setCollectionId(collectionId);
    return report;
  }

  protected void setEmailConfiguration() {
    embeddedOptimizeExtension.getConfigurationService().setEmailEnabled(true);
    embeddedOptimizeExtension.getConfigurationService().setAlertEmailAddress("from@localhost.com");
    embeddedOptimizeExtension.getConfigurationService().setAlertEmailHostname("127.0.0.1");
    embeddedOptimizeExtension.getConfigurationService()
      .setAlertEmailPort(IntegrationTestConfigurationUtil.getSmtpPort());
    EmailAuthenticationConfiguration emailAuthenticationConfiguration =
      embeddedOptimizeExtension.getConfigurationService()
      .getEmailAuthenticationConfiguration();
    emailAuthenticationConfiguration.setEnabled(true);
    emailAuthenticationConfiguration.setUsername("demo");
    emailAuthenticationConfiguration.setPassword("demo");
    emailAuthenticationConfiguration.setSecurityProtocol(NONE);
  }

  protected GreenMail initGreenMail() {
    GreenMail greenMail = new GreenMail(
      new ServerSetup(IntegrationTestConfigurationUtil.getSmtpPort(), null, ServerSetup.PROTOCOL_SMTP)
    );
    greenMail.setUser("from@localhost.com", "demo", "demo");
    greenMail.setUser("test@camunda.com", "test@camunda.com", "test@camunda.com");
    greenMail.start();
    return greenMail;
  }

  protected void grantSingleDefinitionAuthorizationsForUser(String userId, String definitionKey) {
    AuthorizationDto authorizationDto = new AuthorizationDto();
    authorizationDto.setResourceType(RESOURCE_TYPE_PROCESS_DEFINITION);
    authorizationDto.setPermissions(Collections.singletonList(ALL_PERMISSION));
    authorizationDto.setResourceId(definitionKey);
    authorizationDto.setType(AUTHORIZATION_TYPE_GRANT);
    authorizationDto.setUserId(userId);
    engineIntegrationExtension.createAuthorization(authorizationDto);
  }

  private DecisionDefinitionEngineDto deployAndStartSimpleDecisionDefinition(String definitionKey) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(definitionKey);
    return engineIntegrationExtension.deployAndStartDecisionDefinition(modelInstance);
  }
}
