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
import org.camunda.optimize.dto.optimize.query.IdDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.alert.SyncListener;
import org.camunda.optimize.test.it.extension.ElasticSearchIntegrationTestExtensionRule;
import org.camunda.optimize.test.it.extension.EmbeddedOptimizeExtensionRule;
import org.camunda.optimize.test.it.extension.EngineDatabaseExtensionRule;
import org.camunda.optimize.test.it.extension.EngineIntegrationExtensionRule;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.util.ProcessReportDataBuilder;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.junit.Rule;
import org.junit.rules.RuleChain;
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

import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.ALL_PERMISSION;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.AUTHORIZATION_TYPE_GRANT;
import static org.camunda.optimize.service.util.configuration.EngineConstantsUtil.RESOURCE_TYPE_PROCESS_DEFINITION;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_PASSWORD;
import static org.camunda.optimize.test.it.extension.TestEmbeddedCamundaOptimize.DEFAULT_USERNAME;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public abstract class AbstractAlertIT {

  public EngineIntegrationExtensionRule engineIntegrationExtensionRule = new EngineIntegrationExtensionRule();
  public ElasticSearchIntegrationTestExtensionRule elasticSearchIntegrationTestExtensionRule =
    new ElasticSearchIntegrationTestExtensionRule();
  public EmbeddedOptimizeExtensionRule embeddedOptimizeExtensionRule = new EmbeddedOptimizeExtensionRule();
  public EngineDatabaseExtensionRule engineDatabaseExtensionRule = new EngineDatabaseExtensionRule(
    engineIntegrationExtensionRule.getEngineName());

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchIntegrationTestExtensionRule)
    .around(engineIntegrationExtensionRule)
    .around(embeddedOptimizeExtensionRule);

  protected String createAlert(AlertCreationDto simpleAlert) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateAlertRequest(simpleAlert)
      .execute(IdDto.class, 200)
      .getId();
  }

  protected void triggerAndCompleteCheckJob(String id) throws SchedulerException, InterruptedException {
    this.triggerAndCompleteJob(checkJobKey(id));
  }

  private void triggerAndCompleteJob(JobKey jobKey) throws SchedulerException, InterruptedException {
    SyncListener jobListener = new SyncListener(1);
    embeddedOptimizeExtensionRule.getAlertService().getScheduler().getListenerManager().addJobListener(jobListener);
    //trigger job
    embeddedOptimizeExtensionRule.getAlertService().getScheduler().triggerJob(jobKey);
    //wait for job to finish
    jobListener.getDone().await();
    embeddedOptimizeExtensionRule.getAlertService()
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
    return embeddedOptimizeExtensionRule.getConfigurationService().getContainerHttpPort().orElse(8090);
  }

  protected OffsetDateTime getNextReminderExecutionTime(String id) throws SchedulerException {
    Date nextTimeReminderIsExecuted = embeddedOptimizeExtensionRule
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
    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();
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
    return engineIntegrationExtensionRule.deployAndStartProcessWithVariables(processModel, variables);
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          long durationInSec) throws SQLException {
    OffsetDateTime shiftedStartDate = startDate.plusDays(daysToShift);
    engineDatabaseExtensionRule.changeProcessInstanceStartDate(processInstanceId, shiftedStartDate);
    engineDatabaseExtensionRule.changeProcessInstanceEndDate(
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
    DecisionDefinitionEngineDto decisionDefinitionDto = deployAndStartSimpleDecisionDefinition();

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    String id = createAndStoreDecisionNumberReport(decisionDefinitionDto);
    return createSimpleAlert(id);
  }

  private String createAndStoreDecisionNumberReport(DecisionDefinitionEngineDto decisionDefinitionDto1) {
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto1.getVersion());
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto1.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();

    SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto = new SingleDecisionReportDefinitionDto();
    singleDecisionReportDefinitionDto.setData(reportData);
    singleDecisionReportDefinitionDto.setId("something");
    singleDecisionReportDefinitionDto.setLastModifier("something");
    singleDecisionReportDefinitionDto.setName("something");
    OffsetDateTime someDate = OffsetDateTime.now().plusHours(1);
    singleDecisionReportDefinitionDto.setCreated(someDate);
    singleDecisionReportDefinitionDto.setLastModified(someDate);
    singleDecisionReportDefinitionDto.setOwner("something");

    return createNewDecisionReport(singleDecisionReportDefinitionDto);
  }

  protected AlertCreationDto setupBasicProcessAlert() {
    return setupBasicProcessAlert("aProcess");
  }

  protected AlertCreationDto setupBasicProcessAlert(String definitionKey) {
    return setupBasicProcessAlertAsUser(definitionKey, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  protected AlertCreationDto setupBasicProcessAlertAsUser(final String definitionKey,
                                                          final String user,
                                                          final String password) {
    ProcessDefinitionEngineDto processDefinition = deploySimpleServiceTaskProcess(definitionKey);
    engineIntegrationExtensionRule.startProcessInstance(processDefinition.getId());

    embeddedOptimizeExtensionRule.importAllEngineEntitiesFromScratch();
    elasticSearchIntegrationTestExtensionRule.refreshAllOptimizeIndices();

    String reportId = createAndStoreNumberReportAsUser(processDefinition, user, password);
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

  private String createNewProcessReportAsUser(final String user, final String password,
                                              SingleProcessReportDefinitionDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  private String createNewDecisionReport(final SingleDecisionReportDefinitionDto singleDecisionReportDefinitionDto) {
    return embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .buildCreateSingleDecisionReportRequest(singleDecisionReportDefinitionDto)
      .execute(IdDto.class, 200)
      .getId();
  }

  protected String createAndStoreNumberReport(ProcessDefinitionEngineDto processDefinition) {
    return createAndStoreNumberReportAsUser(processDefinition, DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  protected String createAndStoreNumberReportAsUser(final ProcessDefinitionEngineDto processDefinition,
                                                    final String user,
                                                    final String password) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto = getNumberReportDefinitionDto(
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion())
    );
    return createNewProcessReportAsUser(user, password, singleProcessReportDefinitionDto);
  }

  private String createAndStoreDurationNumberReportAsUser(final String processDefinitionKey,
                                                          final String processDefinitionVersion,
                                                          final String user,
                                                          final String password) {
    SingleProcessReportDefinitionDto singleProcessReportDefinitionDto =
      getDurationReportDefinitionDto(processDefinitionKey, processDefinitionVersion);
    return createNewProcessReportAsUser(user, password, singleProcessReportDefinitionDto);
  }

  protected SingleProcessReportDefinitionDto getNumberReportDefinitionDto(ProcessDefinitionEngineDto processDefinition) {
    return getNumberReportDefinitionDto(processDefinition.getKey(), String.valueOf(processDefinition.getVersion()));
  }


  protected SingleProcessReportDefinitionDto getNumberReportDefinitionDto(String processDefinitionKey,
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
    return report;
  }

  protected void updateSingleProcessReport(String id,
                                                 SingleProcessReportDefinitionDto updatedReport) {
    Response response = embeddedOptimizeExtensionRule
      .getRequestExecutor()
      .withUserAuthentication(DEFAULT_USERNAME, DEFAULT_PASSWORD)
      .buildUpdateSingleProcessReportRequest(id, updatedReport, true)
      .execute();
    assertThat(response.getStatus(), is(204));
  }

  protected ProcessDefinitionEngineDto deploySimpleServiceTaskProcess() {
    return deploySimpleServiceTaskProcess("aProcess");
  }

  protected ProcessDefinitionEngineDto deploySimpleServiceTaskProcess(String definitionKey) {
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(definitionKey)
      .name("aProcessName")
      .startEvent()
      .serviceTask()
      .camundaExpression("${true}")
      .endEvent()
      .done();
    return engineIntegrationExtensionRule.deployProcessAndGetProcessDefinition(processModel);
  }

  protected String createAndStoreDurationNumberReport(ProcessInstanceEngineDto instanceEngineDto) {
    return createAndStoreDurationNumberReport(
      instanceEngineDto.getProcessDefinitionKey(),
      instanceEngineDto.getProcessDefinitionVersion()
    );
  }

  private String createAndStoreDurationNumberReport(String processDefinitionKey, String processDefinitionVersion) {
    return createAndStoreDurationNumberReportAsUser(
      processDefinitionKey, processDefinitionVersion, DEFAULT_USERNAME, DEFAULT_PASSWORD
    );
  }

  private SingleProcessReportDefinitionDto getDurationReportDefinitionDto(String processDefinitionKey,
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
    return report;
  }

  protected void setEmailConfiguration() {
    embeddedOptimizeExtensionRule.getConfigurationService().setEmailEnabled(true);
    embeddedOptimizeExtensionRule.getConfigurationService().setAlertEmailUsername("demo");
    embeddedOptimizeExtensionRule.getConfigurationService().setAlertEmailPassword("demo");
    embeddedOptimizeExtensionRule.getConfigurationService().setAlertEmailAddress("from@localhost.com");
    embeddedOptimizeExtensionRule.getConfigurationService().setAlertEmailHostname("127.0.0.1");
    embeddedOptimizeExtensionRule.getConfigurationService()
      .setAlertEmailPort(IntegrationTestConfigurationUtil.getSmtpPort());
    embeddedOptimizeExtensionRule.getConfigurationService().setAlertEmailProtocol("NONE");
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
    engineIntegrationExtensionRule.createAuthorization(authorizationDto);
  }

  private DecisionDefinitionEngineDto deployAndStartSimpleDecisionDefinition() {
    final DmnModelInstance modelInstance = createSimpleDmnModel("key");
    return engineIntegrationExtensionRule.deployAndStartDecisionDefinition(modelInstance);
  }
}
