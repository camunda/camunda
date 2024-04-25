/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static org.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;

import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.SneakyThrows;
import org.awaitility.Awaitility;
import org.camunda.optimize.dto.optimize.ProcessInstanceDto;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import org.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import org.camunda.optimize.exception.OptimizeIntegrationTestException;
import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeProcessInstanceImportService;
import org.camunda.optimize.service.importing.engine.service.zeebe.ZeebeUserTaskImportService;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.test.it.extension.ZeebeExtension;
import org.camunda.optimize.test.it.extension.db.TermsQueryContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.ActiveProfiles;

@Tag("ccsm-test")
@ActiveProfiles(CCSM_PROFILE)
public abstract class AbstractCCSMIT extends AbstractIT {
  @RegisterExtension
  @Order(4)
  protected static ZeebeExtension zeebeExtension = new ZeebeExtension();

  protected final Supplier<OptimizeIntegrationTestException> eventNotFoundExceptionSupplier =
      () -> new OptimizeIntegrationTestException("Cannot find exported event");

  protected static boolean isZeebeVersionPre83() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.0.*|8.1.*|8.2.*");
    return zeebeVersionPattern
        .matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion())
        .matches();
  }

  protected static boolean isZeebeVersionPre84() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.0.*|8.1.*|8.2.*|8.3.*");
    return zeebeVersionPattern
        .matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion())
        .matches();
  }

  protected static boolean isZeebeVersionPre85() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.0.*|8.1.*|8.2.*|8.3.*|8.4.*");
    return zeebeVersionPattern
        .matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion())
        .matches();
  }

  protected static boolean isZeebeVersionWithMultiTenancy() {
    return !isZeebeVersionPre83();
  }

  @BeforeEach
  public void setupZeebeImportAndReloadConfiguration() {
    final String embeddedZeebePrefix = zeebeExtension.getZeebeRecordPrefix();
    // set the new record prefix for the next test
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setName(embeddedZeebePrefix);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @AfterEach
  public void after() {
    // Clear all potential existing Zeebe records in Optimize
    databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix());
  }

  @Override
  protected void startAndUseNewOptimizeInstance() {
    startAndUseNewOptimizeInstance(new HashMap<>(), CCSM_PROFILE);
  }

  protected void importAllZeebeEntitiesFromScratch() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromScratch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected void importAllZeebeEntitiesFromLastIndex() {
    embeddedOptimizeExtension.importAllZeebeEntitiesFromLastIndex();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
  }

  protected ProcessInstanceEvent deployAndStartInstanceForProcess(final BpmnModelInstance process) {
    final Process deployedProcess = zeebeExtension.deployProcess(process);
    return zeebeExtension.startProcessInstanceForProcess(deployedProcess.getBpmnProcessId());
  }

  protected TermsQueryContainer getQueryForProcessableProcessInstanceEvents() {
    final TermsQueryContainer termsQueryContainer = new TermsQueryContainer();
    termsQueryContainer.addTermQuery(
        ZeebeProcessInstanceRecordDto.Fields.intent,
        ZeebeProcessInstanceImportService.INTENTS_TO_IMPORT.stream()
            .map(ProcessInstanceIntent::name)
            .toList());
    return termsQueryContainer;
  }

  protected String getConfiguredZeebeName() {
    return embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().getName();
  }

  protected void waitUntilMinimumDataExportedCount(
      final int minExportedEventCount,
      final String indexName,
      final TermsQueryContainer boolQueryBuilder) {
    waitUntilMinimumDataExportedCount(minExportedEventCount, indexName, boolQueryBuilder, 15);
  }

  protected void waitUntilMinimumProcessInstanceEventsExportedCount(
      final int minExportedEventCount) {
    waitUntilMinimumDataExportedCount(
        minExportedEventCount,
        DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
        getQueryForProcessableProcessInstanceEvents());
  }

  protected void waitUntilNumberOfDefinitionsExported(final int expectedDefinitionsCount) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeProcessDefinitionRecordDto.Fields.intent, List.of(ProcessIntent.CREATED.name()));
    waitUntilMinimumDataExportedCount(
        expectedDefinitionsCount, DatabaseConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME, query);
  }

  protected void waitUntilRecordMatchingQueryExported(
      final String indexName, final TermsQueryContainer boolQuery) {
    waitUntilRecordMatchingQueryExported(1, indexName, boolQuery);
  }

  protected void waitUntilRecordMatchingQueryExported(
      final long minRecordCount, final String indexName, final TermsQueryContainer boolQuery) {
    waitUntilMinimumDataExportedCount(minRecordCount, indexName, boolQuery, 10);
  }

  protected void waitUntilInstanceRecordWithElementIdExported(final String instanceElementId) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeProcessInstanceRecordDto.Fields.value
            + "."
            + ZeebeProcessInstanceDataDto.Fields.elementId,
        instanceElementId);
    waitUntilRecordMatchingQueryExported(
        DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME, query);
  }

  protected void waitUntilUserTaskRecordWithElementIdExported(final String instanceElementId) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeUserTaskRecordDto.Fields.value + "." + ZeebeUserTaskDataDto.Fields.elementId,
        instanceElementId);
    waitUntilRecordMatchingQueryExported(DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME, query);
  }

  protected void waitUntilUserTaskRecordWithIntentExported(final UserTaskIntent intent) {
    waitUntilUserTaskRecordWithIntentExported(1, intent);
  }

  protected void waitUntilUserTaskRecordWithIntentExported(
      final long minRecordCount, final UserTaskIntent intent) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeUserTaskRecordDto.Fields.value + "." + ZeebeUserTaskDataDto.Fields.elementId,
        USER_TASK);
    query.addTermQuery(ZeebeRecordDto.Fields.intent, intent.name());
    waitUntilRecordMatchingQueryExported(
        minRecordCount, DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME, query);
  }

  protected void waitUntilDefinitionWithIdExported(final String processDefinitionId) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(ZeebeProcessDefinitionRecordDto.Fields.intent, ProcessIntent.CREATED.name());
    query.addTermQuery(
        ZeebeProcessDefinitionRecordDto.Fields.value
            + "."
            + ZeebeProcessInstanceDataDto.Fields.bpmnProcessId,
        processDefinitionId);
    waitUntilRecordMatchingQueryExported(
        DatabaseConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME, query);
  }

  protected String getFlowNodeInstanceIdFromProcessInstanceForActivity(
      final ProcessInstanceDto processInstanceDto, final String activityId) {
    return getPropertyIdFromProcessInstanceForActivity(
        processInstanceDto, activityId, FlowNodeInstanceDto::getFlowNodeInstanceId);
  }

  protected String getPropertyIdFromProcessInstanceForActivity(
      final ProcessInstanceDto processInstanceDto,
      final String activityId,
      final Function<FlowNodeInstanceDto, String> propertyFunction) {
    return processInstanceDto.getFlowNodeInstances().stream()
        .filter(flowNodeInstanceDto -> flowNodeInstanceDto.getFlowNodeId().equals(activityId))
        .map(propertyFunction)
        .findFirst()
        .orElseThrow(
            () ->
                new OptimizeIntegrationTestException(
                    "Could not find property for process instance with key: "
                        + processInstanceDto.getProcessDefinitionKey()));
  }

  protected BpmnModelInstance readProcessDiagramAsInstance(final String diagramPath) {
    final InputStream inputStream = AbstractCCSMIT.class.getResourceAsStream(diagramPath);
    return Bpmn.readModelFromStream(inputStream);
  }

  protected void setTenantIdForExportedZeebeRecords(final String indexName, final String tenantId) {
    databaseIntegrationTestExtension.updateZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        indexName,
        String.format("ctx._source.value.tenantId = \"%s\";", tenantId));
  }

  @SneakyThrows
  protected void waitUntilMinimumDataExportedCount(
      final long minimumCount,
      final String indexName,
      final TermsQueryContainer queryContainer,
      final long countTimeoutInSeconds) {
    final String expectedIndex = zeebeExtension.getZeebeRecordPrefix() + "-" + indexName;
    Awaitility.given()
        .ignoreExceptions()
        .timeout(15, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(databaseIntegrationTestExtension.zeebeIndexExists(expectedIndex))
                    .isTrue());
    Awaitility.given()
        .ignoreExceptions()
        .timeout(countTimeoutInSeconds, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(
                        databaseIntegrationTestExtension.countRecordsByQuery(
                            queryContainer, expectedIndex))
                    .isGreaterThanOrEqualTo(minimumCount));
  }

  protected Map<String, List<ZeebeUserTaskRecordDto>> getZeebeExportedUserTaskEventsByElementId() {
    return getZeebeExportedProcessableEvents(
            zeebeExtension.getZeebeRecordPrefix()
                + "-"
                + DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME,
            getQueryForProcessableUserTaskEvents(),
            ZeebeUserTaskRecordDto.class)
        .stream()
        .collect(Collectors.groupingBy(event -> event.getValue().getElementId()));
  }

  @SneakyThrows
  protected Map<String, List<ZeebeProcessInstanceRecordDto>>
      getZeebeExportedProcessInstanceEventsByElementId() {
    return getZeebeExportedProcessableEvents(
            zeebeExtension.getZeebeRecordPrefix()
                + "-"
                + DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
            getQueryForProcessableProcessInstanceEvents(),
            ZeebeProcessInstanceRecordDto.class)
        .stream()
        .collect(Collectors.groupingBy(event -> event.getValue().getElementId()));
  }

  protected OffsetDateTime getTimestampForFirstZeebeEventsWithIntent(
      final List<? extends ZeebeRecordDto> eventsForElement, final Intent intent) {
    final ZeebeRecordDto startOfElement =
        eventsForElement.stream()
            .filter(event -> event.getIntent().equals(intent))
            .findFirst()
            .orElseThrow(eventNotFoundExceptionSupplier);
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(startOfElement.getTimestamp()), ZoneId.systemDefault());
  }

  protected OffsetDateTime getTimestampForZeebeAssignEvents(
      final List<? extends ZeebeRecordDto> eventsForElement, final String assigneeId) {
    final ZeebeRecordDto startOfElement =
        eventsForElement.stream()
            .filter(
                event ->
                    event.getIntent().equals(ASSIGNED)
                        && ((ZeebeUserTaskRecordDto) event)
                            .getValue()
                            .getAssignee()
                            .equals(assigneeId))
            .findFirst()
            .orElseThrow(eventNotFoundExceptionSupplier);
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(startOfElement.getTimestamp()), ZoneId.systemDefault());
  }

  protected OffsetDateTime getTimestampForZeebeUnassignEvent(
      final List<? extends ZeebeRecordDto> eventsForElement) {
    return getTimestampForZeebeAssignEvents(eventsForElement, "");
  }

  @SneakyThrows
  private <T> List<T> getZeebeExportedProcessableEvents(
      final String exportIndex,
      final TermsQueryContainer queryForProcessableEvents,
      final Class<T> zeebeRecordClass) {
    return databaseIntegrationTestExtension.getZeebeExportedRecordsByQuery(
        exportIndex, queryForProcessableEvents, zeebeRecordClass);
  }

  private TermsQueryContainer getQueryForProcessableUserTaskEvents() {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeUserTaskRecordDto.Fields.intent,
        ZeebeUserTaskImportService.INTENTS_TO_IMPORT.stream().map(UserTaskIntent::name).toList());
    return query;
  }

  protected void waitUntilInstanceRecordWithElementTypeAndIntentExported(
      final BpmnElementType elementType, final Intent intent) {
    final TermsQueryContainer query = new TermsQueryContainer();
    query.addTermQuery(
        ZeebeProcessInstanceRecordDto.Fields.value
            + "."
            + ZeebeProcessInstanceDataDto.Fields.bpmnElementType,
        elementType.name());
    query.addTermQuery(ZeebeProcessInstanceRecordDto.Fields.intent, intent.name().toUpperCase());
    waitUntilMinimumDataExportedCount(
        1, DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME, query, 10);
  }
}
