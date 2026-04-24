/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.CCSM_PROFILE;
import static io.camunda.optimize.util.ZeebeBpmnModels.USER_TASK;
import static io.camunda.zeebe.protocol.record.intent.UserTaskIntent.ASSIGNED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.dto.zeebe.definition.ZeebeProcessDefinitionRecordDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskDataDto;
import io.camunda.optimize.dto.zeebe.usertask.ZeebeUserTaskRecordDto;
import io.camunda.optimize.dto.zeebe.variable.ZeebeVariableRecordDto;
import io.camunda.optimize.exception.OptimizeIntegrationTestException;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeProcessInstanceImportService;
import io.camunda.optimize.service.importing.engine.service.zeebe.ZeebeUserTaskImportService;
import io.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import io.camunda.optimize.test.it.extension.ZeebeExtension;
import io.camunda.optimize.test.it.extension.db.TermsQueryContainer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
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

  public static boolean isZeebeVersionPre85() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.0.*|8.1.*|8.2.*|8.3.*|8.4.*");
    return zeebeVersionPattern
        .matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion())
        .matches();
  }

  public static boolean isZeebeVersionPre86() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.2.*|8.3.*|8.4.*|8.5.*");
    return zeebeVersionPattern
        .matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion())
        .matches();
  }

  protected static boolean isZeebeVersion87_OrLater() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.([7-9]|\\d{2,})");
    return zeebeVersionPattern
            .matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion())
            .matches()
        || isZeebeVersionSnapshot();
  }

  protected static boolean isZeebeVersion89_OrLater() {
    final Pattern zeebeVersionPattern = Pattern.compile("8.([9]|\\d{2,})");
    return zeebeVersionPattern
            .matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion())
            .matches()
        || isZeebeVersionSnapshot();
  }

  protected static boolean isZeebeVersionSnapshot() {
    final String dockerVersion = IntegrationTestConfigurationUtil.getZeebeDockerVersion();
    return dockerVersion.equalsIgnoreCase("snapshot");
  }

  protected static boolean isZeebeVersionWithMultiTenancy() {
    return !isZeebeVersionPre83();
  }

  @BeforeEach
  @Order(1)
  public void ensureCleanZeebeState() {
    // Safety net: records from the previous test may still be in the Zeebe exporter's write queue
    // when after() ran; they can arrive in the window between after() and this @BeforeEach.
    deleteZeebeRecordsAndAssertClean(zeebeExtension.getZeebeRecordPrefix());
  }

  @BeforeEach
  @Order(2)
  public void configureZeebeImport() {
    final String embeddedZeebePrefix = zeebeExtension.getZeebeRecordPrefix();
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setName(embeddedZeebePrefix);
    embeddedOptimizeExtension.reloadConfiguration();
  }

  @AfterEach
  public void after() {
    final Set<Long> cancelledKeys = zeebeExtension.cancelAllStartedInstances();
    waitForCancelledInstancesToExport(cancelledKeys);
    deleteZeebeRecordsAndAssertClean(zeebeExtension.getZeebeRecordPrefix());
  }

  /**
   * Waits until every cancelled instance has exported its PROCESS-level terminal record
   * (ELEMENT_COMPLETED or ELEMENT_TERMINATED) before cleanup runs. That record is the last one the
   * broker generates; once it appears in ES every earlier record for the instance — including
   * asynchronously-exported variable records — is guaranteed to be there too.
   *
   * <p>Already-completed instances are excluded from {@code cancelledKeys} by {@link
   * ZeebeExtension#cancelAllStartedInstances()}, so this wait never blocks on instances whose
   * terminal record was deleted by a mid-test cleanup.
   */
  private void waitForCancelledInstancesToExport(final Set<Long> cancelledKeys) {
    if (cancelledKeys.isEmpty()) {
      return;
    }
    final TermsQueryContainer terminalQuery = new TermsQueryContainer();
    terminalQuery.addTermQuery(
        ZeebeRecordDto.Fields.intent,
        List.of(
            ProcessInstanceIntent.ELEMENT_COMPLETED.name(),
            ProcessInstanceIntent.ELEMENT_TERMINATED.name()));
    terminalQuery.addTermQuery(
        ZeebeProcessInstanceRecordDto.Fields.value
            + "."
            + ZeebeProcessInstanceDataDto.Fields.bpmnElementType,
        BpmnElementType.PROCESS.name());
    waitUntilMinimumDataExportedCount(
        cancelledKeys.size(), DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME, terminalQuery);
  }

  private void deleteZeebeRecordsAndAssertClean(final String zeebePrefix) {
    final String processInstanceIndex =
        zeebePrefix + "-" + DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
    final String variableIndex = zeebePrefix + "-" + DatabaseConstants.ZEEBE_VARIABLE_INDEX_NAME;
    final String userTaskIndex = zeebePrefix + "-" + DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;
    Awaitility.given()
        .ignoreExceptions()
        .timeout(30, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              databaseIntegrationTestExtension.deleteAllZeebeRecordsForPrefix(zeebePrefix);
              if (databaseIntegrationTestExtension.zeebeIndexExists(processInstanceIndex)) {
                assertThat(
                        databaseIntegrationTestExtension.countRecordsByQuery(
                            getQueryForProcessableProcessInstanceEvents(), processInstanceIndex))
                    .isZero();
              }
              if (databaseIntegrationTestExtension.zeebeIndexExists(variableIndex)) {
                assertThat(
                        databaseIntegrationTestExtension.countRecordsByQuery(
                            getQueryForProcessableVariableEvents(), variableIndex))
                    .isZero();
              }
              if (databaseIntegrationTestExtension.zeebeIndexExists(userTaskIndex)) {
                // Use match-all (empty container) to catch ALL user-task intents, including
                // non-processable ones (CREATED, ASSIGNING, COMPLETING, CANCELING) that Zeebe
                // generates but Optimize does not import. A filtered check would miss these and
                // allow them to accumulate across tests.
                assertThat(
                        databaseIntegrationTestExtension.countRecordsByQuery(
                            new TermsQueryContainer(), userTaskIndex))
                    .isZero();
              }
            });
  }

  @AfterAll
  static void deleteZeebeIndices() {
    // Physically delete all Zeebe indices after the class finishes so they don't accumulate.
    // This runs after all tests in the class are complete; the broker is still alive at this
    // point but shutting down in ZeebeExtension.afterAll(), which is fine — any stray writes
    // are inconsequential since no further tests will read these indices.
    databaseIntegrationTestExtension.deleteAllZeebeIndicesForPrefix(
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

  private TermsQueryContainer getQueryForProcessableVariableEvents() {
    final TermsQueryContainer termsQueryContainer = new TermsQueryContainer();
    termsQueryContainer.addTermQuery(
        ZeebeVariableRecordDto.Fields.intent,
        List.of(VariableIntent.CREATED.name(), VariableIntent.UPDATED.name()));
    return termsQueryContainer;
  }

  protected String getConfiguredZeebeName() {
    return embeddedOptimizeExtension.getConfigurationService().getConfiguredZeebe().getName();
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
    waitUntilMinimumDataExportedCount(minRecordCount, indexName, boolQuery);
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

  protected void waitUntilMinimumDataExportedCount(
      final long minimumCount, final String indexName, final TermsQueryContainer queryContainer) {
    final String expectedIndex = zeebeExtension.getZeebeRecordPrefix() + "-" + indexName;
    Awaitility.given()
        .ignoreExceptions()
        .timeout(60, TimeUnit.SECONDS)
        .untilAsserted(
            () ->
                assertThat(databaseIntegrationTestExtension.zeebeIndexExists(expectedIndex))
                    .isTrue());
    Awaitility.given()
        .ignoreExceptions()
        .timeout(60, TimeUnit.SECONDS)
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

  protected OffsetDateTime getTimestampForLastZeebeEventsWithIntent(
      final List<? extends ZeebeRecordDto> eventsForElement, final Intent intent) {
    final ZeebeRecordDto startOfElement =
        eventsForElement.stream()
            .filter(event -> event.getIntent().equals(intent))
            .sorted(Comparator.comparing(ZeebeRecordDto::getTimestamp))
            .reduce((first, second) -> second)
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

  protected OffsetDateTime getTimestampForZeebeLastAssignedEvents(
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
            .sorted(Comparator.comparing(ZeebeRecordDto::getTimestamp))
            .reduce((first, second) -> second)
            .orElseThrow(eventNotFoundExceptionSupplier);
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(startOfElement.getTimestamp()), ZoneId.systemDefault());
  }

  protected OffsetDateTime getTimestampForZeebeUnassignEvent(
      final List<? extends ZeebeRecordDto> eventsForElement) {
    return getTimestampForZeebeAssignEvents(eventsForElement, "");
  }

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
        1, DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME, query);
  }
}
