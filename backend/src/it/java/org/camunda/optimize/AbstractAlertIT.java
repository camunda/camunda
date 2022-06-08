/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.optimize.dto.engine.definition.DecisionDefinitionEngineDto;
import org.camunda.optimize.dto.engine.definition.ProcessDefinitionEngineDto;
import org.camunda.optimize.dto.optimize.DefinitionType;
import org.camunda.optimize.dto.optimize.query.IdResponseDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertCreationRequestDto;
import org.camunda.optimize.dto.optimize.query.alert.AlertInterval;
import org.camunda.optimize.dto.optimize.query.alert.AlertIntervalUnit;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.rest.engine.dto.ProcessInstanceEngineDto;
import org.camunda.optimize.service.alert.SyncListener;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.util.configuration.EmailAuthenticationConfiguration;
import org.camunda.optimize.service.util.configuration.WebhookConfiguration;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.util.ProcessReportDataType;
import org.camunda.optimize.test.util.TemplatedProcessReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataBuilder;
import org.camunda.optimize.test.util.decision.DecisionReportDataType;
import org.camunda.optimize.util.BpmnModels;
import org.junit.jupiter.api.BeforeEach;
import org.mockserver.client.MockServerClient;
import org.mockserver.verify.VerificationTimes;
import org.quartz.JobKey;
import org.quartz.SchedulerException;

import javax.ws.rs.core.Response;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.camunda.optimize.dto.optimize.DefinitionType.DECISION;
import static org.camunda.optimize.dto.optimize.DefinitionType.PROCESS;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_PASSWORD;
import static org.camunda.optimize.rest.RestTestConstants.DEFAULT_USERNAME;
import static org.camunda.optimize.service.util.configuration.EmailSecurityProtocol.NONE;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_DEFINITION_KEY;
import static org.camunda.optimize.test.optimize.CollectionClient.DEFAULT_TENANTS;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_INVALID_PORT_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_METHOD;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_NAME;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_URL_INVALID_PORT;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.TEST_WEBHOOK_URL_PATH;
import static org.camunda.optimize.test.optimize.UiConfigurationClient.createWebhookHostUrl;
import static org.camunda.optimize.test.util.decision.DmnHelper.createSimpleDmnModel;
import static org.camunda.optimize.util.BpmnModels.getSimpleBpmnDiagram;
import static org.mockserver.model.HttpRequest.request;

public abstract class AbstractAlertIT extends AbstractIT {
  @BeforeEach
  public void beforeEach() throws Exception {
    embeddedOptimizeExtension.getAlertService().getScheduler().clear();
  }

  @SneakyThrows
  protected void triggerAndCompleteCheckJob(String id) {
    this.triggerAndCompleteJob(checkJobKey(id));
  }

  @SneakyThrows
  private void triggerAndCompleteJob(JobKey jobKey) {
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

  @SneakyThrows
  protected void triggerAndCompleteReminderJob(String id) {
    this.triggerAndCompleteJob(reminderJobKey(id));
  }

  private JobKey reminderJobKey(String id) {
    return new JobKey(id + "-reminder-job", "statusReminder-job");
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

  protected ProcessInstanceEngineDto deployWithTimeShift(long daysToShift, long durationInSec) {
    OffsetDateTime startDate = OffsetDateTime.now();
    ProcessInstanceEngineDto processInstance = engineIntegrationExtension.deployAndStartProcessWithVariables(
      getSimpleBpmnDiagram(),
      new HashMap<>()
    );
    adjustProcessInstanceDates(processInstance.getId(), startDate, daysToShift, durationInSec);
    importAllEngineEntitiesFromScratch();
    return processInstance;
  }

  private void adjustProcessInstanceDates(String processInstanceId,
                                          OffsetDateTime startDate,
                                          long daysToShift,
                                          long durationInSec) {
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

  protected AlertCreationRequestDto createBasicAlertWithReminder() {
    AlertCreationRequestDto simpleAlert = setupBasicProcessAlert();
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(1);
    reminderInterval.setUnit(AlertIntervalUnit.SECONDS);
    simpleAlert.setReminder(reminderInterval);
    return simpleAlert;
  }

  protected AlertCreationRequestDto setupBasicDecisionAlert() {
    String collectionId = collectionClient.createNewCollectionWithDefaultDecisionScope();
    String id = createNumberReportForCollection(collectionId, DECISION);
    return alertClient.createSimpleAlert(id);
  }

  protected AlertCreationRequestDto setupBasicProcessAlert() {
    return setupBasicProcessAlertAsUser("aProcess", DEFAULT_USERNAME, DEFAULT_PASSWORD);
  }

  protected AlertCreationRequestDto setupBasicProcessAlertAsUser(final String definitionKey,
                                                                 final String user,
                                                                 final String password) {
    String collectionId = collectionClient.createNewCollection(user, password);
    final CollectionScopeEntryDto scopeEntry = new CollectionScopeEntryDto(PROCESS, definitionKey, DEFAULT_TENANTS);
    collectionClient.addScopeEntryToCollectionAsUser(collectionId, scopeEntry, user, password);
    String reportId = createNumberReportForCollection(
      definitionKey,
      collectionId,
      PROCESS,
      user,
      password
    );
    return alertClient.createSimpleAlert(reportId);
  }

  protected String createNewProcessReportAsUser(final String collectionId,
                                                final ProcessDefinitionEngineDto processDefinition) {
    SingleProcessReportDefinitionRequestDto procReport = getProcessNumberReportDefinitionDto(
      collectionId,
      processDefinition
    );
    return createNewProcessReportAsUser(DEFAULT_USERNAME, DEFAULT_PASSWORD, procReport);
  }

  private String createNewProcessReportAsUser(final String user, final String password, final String collectionId,
                                              final ProcessDefinitionEngineDto processDefinition) {
    SingleProcessReportDefinitionRequestDto procReport = getProcessNumberReportDefinitionDto(
      collectionId,
      processDefinition
    );
    return createNewProcessReportAsUser(user, password, procReport);
  }

  private String createNewProcessReportAsUser(final String user, final String password,
                                              final SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto) {
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateSingleProcessReportRequest(singleProcessReportDefinitionDto)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
      .getId();
  }

  private String createNewDecisionReportAsUser(final String user, final String password, final String collectionId,
                                               final DecisionDefinitionEngineDto decisionDefinitionDto) {
    SingleDecisionReportDefinitionRequestDto decReport = getDecisionNumberReportDefinitionDto(
      collectionId,
      decisionDefinitionDto
    );
    return embeddedOptimizeExtension
      .getRequestExecutor()
      .withUserAuthentication(user, password)
      .buildCreateSingleDecisionReportRequest(decReport)
      .execute(IdResponseDto.class, Response.Status.OK.getStatusCode())
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

  protected String createAndStoreDurationNumberReport(final String collectionId, final String processDefinitionKey,
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
    SingleProcessReportDefinitionRequestDto singleProcessReportDefinitionDto =
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
        importAllEngineEntitiesFromScratch();
        return createNewProcessReportAsUser(user, password, collectionId, processDefinition);

      case DECISION:
        DecisionDefinitionEngineDto decisionDefinitionDto = deployAndStartSimpleDecisionDefinition(definitionKey);
        importAllEngineEntitiesFromScratch();
        return createNewDecisionReportAsUser(user, password, collectionId, decisionDefinitionDto);

      default:
        throw new OptimizeRuntimeException("Unknown resource type provided.");
    }
  }

  private SingleDecisionReportDefinitionRequestDto getDecisionNumberReportDefinitionDto(String collectionId,
                                                                                        DecisionDefinitionEngineDto decisionDefinitionDto) {
    final String decisionDefinitionVersion1 = String.valueOf(decisionDefinitionDto.getVersion());
    DecisionReportDataDto reportData = DecisionReportDataBuilder.create()
      .setDecisionDefinitionKey(decisionDefinitionDto.getKey())
      .setDecisionDefinitionVersion(decisionDefinitionVersion1)
      .setReportDataType(DecisionReportDataType.COUNT_DEC_INST_FREQ_GROUP_BY_NONE)
      .build();

    SingleDecisionReportDefinitionRequestDto report = new SingleDecisionReportDefinitionRequestDto();
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

  protected SingleProcessReportDefinitionRequestDto getProcessNumberReportDefinitionDto(String collectionId,
                                                                                        ProcessDefinitionEngineDto processDefinition) {
    return getProcessNumberReportDefinitionDto(
      collectionId,
      processDefinition.getKey(),
      String.valueOf(processDefinition.getVersion())
    );
  }

  protected SingleProcessReportDefinitionRequestDto getProcessNumberReportDefinitionDto(String collectionId,
                                                                                        String processDefinitionKey,
                                                                                        String processDefinitionVersion) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_FREQ_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
    report.setData(reportData);
    report.setName("something");
    report.setCollectionId(collectionId);
    return report;
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
    return engineIntegrationExtension.deployProcessAndGetProcessDefinition(BpmnModels.getSingleServiceTaskProcess(
      definitionKey));
  }

  private SingleProcessReportDefinitionRequestDto getDurationReportDefinitionDto(String collectionId,
                                                                                 String processDefinitionKey,
                                                                                 String processDefinitionVersion) {
    ProcessReportDataDto reportData = TemplatedProcessReportDataBuilder
      .createReportData()
      .setProcessDefinitionKey(processDefinitionKey)
      .setProcessDefinitionVersion(processDefinitionVersion)
      .setReportDataType(ProcessReportDataType.PROC_INST_DUR_GROUP_BY_NONE)
      .build();
    SingleProcessReportDefinitionRequestDto report = new SingleProcessReportDefinitionRequestDto();
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

  protected void setWebhookConfiguration(final Integer webhookPort) {
    // payload using all possible placeholders
    final String payload = "{\n"
      + Stream.of(WebhookConfiguration.Placeholder.values())
      .map(placeholder -> String.format("\"%s\": \"%s\"", placeholder.name(), placeholder.getPlaceholderString()))
      .collect(Collectors.joining(",\n"))
      + "\n}";

    setWebhookConfiguration(webhookPort, payload);
  }

  protected void setWebhookConfiguration(final Integer webhookPort, final String payload) {
    Map<String, WebhookConfiguration> webhookConfigurationMap = new HashMap<>();


    final WebhookConfiguration webhook1 = uiConfigurationClient.createWebhookConfiguration(
      createWebhookHostUrl(webhookPort) + TEST_WEBHOOK_URL_PATH,
      ImmutableMap.of("Content-type", "application/json"),
      TEST_WEBHOOK_METHOD,
      payload
    );
    final WebhookConfiguration webhook2 = uiConfigurationClient.createWebhookConfiguration(
      createWebhookHostUrl(webhookPort) + TEST_WEBHOOK_URL_PATH,
      ImmutableMap.of("Content-type", "some/customType"),
      TEST_WEBHOOK_METHOD,
      payload
    );
    final WebhookConfiguration webhook3 = uiConfigurationClient.createWebhookConfiguration(
      TEST_WEBHOOK_URL_INVALID_PORT + TEST_WEBHOOK_URL_PATH,
      ImmutableMap.of("Content-type", "application/json"),
      TEST_WEBHOOK_METHOD,
      payload
    );

    webhookConfigurationMap.put(TEST_WEBHOOK_NAME, webhook1);
    webhookConfigurationMap.put(TEST_CUSTOM_CONTENT_TYPE_WEBHOOK_NAME, webhook2);
    webhookConfigurationMap.put(TEST_INVALID_PORT_WEBHOOK_NAME, webhook3);

    embeddedOptimizeExtension.getConfigurationService().setConfiguredWebhooks(webhookConfigurationMap);
  }

  protected AlertCreationRequestDto addReminderToAlert(AlertCreationRequestDto alert) {
    AlertInterval reminderInterval = new AlertInterval();
    reminderInterval.setValue(3);
    reminderInterval.setUnit(AlertIntervalUnit.SECONDS);
    alert.setReminder(reminderInterval);
    alert.setThreshold(1500.0);
    alert.getCheckInterval().setValue(5);
    return alert;
  }

  protected void assertWebhookRequestReceived(final MockServerClient client, final Integer times) {
    client.verify(
      request().withMethod(TEST_WEBHOOK_METHOD).withPath(TEST_WEBHOOK_URL_PATH),
      VerificationTimes.exactly(times)
    );
  }

  protected void clearWebhookRequestsFromClient(final MockServerClient client) {
    client.clear(request().withPath(TEST_WEBHOOK_URL_PATH));
  }

  private DecisionDefinitionEngineDto deployAndStartSimpleDecisionDefinition(String definitionKey) {
    final DmnModelInstance modelInstance = createSimpleDmnModel(definitionKey);
    return engineIntegrationExtension.deployAndStartDecisionDefinition(modelInstance);
  }

}
