/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static io.camunda.optimize.util.ZeebeBpmnModels.START_EVENT;
import static io.camunda.optimize.util.ZeebeBpmnModels.createSimpleServiceTaskProcess;
import static io.camunda.optimize.util.ZeebeBpmnModels.createStartEndProcess;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.AbstractCCSMIT;
import io.camunda.optimize.dto.optimize.ProcessInstanceConstants;
import io.camunda.optimize.dto.optimize.query.process.FlowNodeInstanceDto;
import io.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import io.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceRecordDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.importing.zeebe.fetcher.es.AbstractZeebeRecordFetcherES;
import io.camunda.optimize.service.importing.zeebe.fetcher.os.AbstractZeebeRecordFetcherOS;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.github.netmikey.logunit.api.LogCapturer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.LoggingEvent;

public class ZeebePositionBasedImportIndexIT extends AbstractCCSMIT {

  public static final OffsetDateTime BEGINNING_OF_TIME =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());

  @RegisterExtension
  @Order(1)
  private final LogCapturer positionBasedHandlerLogs =
      LogCapturer.create().captureForType(PositionBasedImportIndexHandler.class);

  @RegisterExtension
  @Order(2)
  private final LogCapturer zeebeFetcherLogs =
      LogCapturer.create()
          .captureForType(AbstractZeebeRecordFetcherES.class)
          .captureForType(AbstractZeebeRecordFetcherOS.class);

  @Test
  public void importPositionIsZeroIfNothingIsImportedYet() {
    // when
    final List<PositionBasedImportIndexHandler> positionBasedHandlers =
        embeddedOptimizeExtension.getAllPositionBasedImportHandlers();

    // then
    assertThat(positionBasedHandlers)
        .hasSize(10)
        .allSatisfy(
            handler -> {
              assertThat(handler.getPersistedPositionOfLastEntity()).isZero();
              assertThat(handler.getPendingSequenceOfLastEntity()).isZero();
              assertThat(handler.getTimestampOfLastPersistedEntity()).isEqualTo(BEGINNING_OF_TIME);
              assertThat(handler.getLastImportExecutionTimestamp()).isEqualTo(BEGINNING_OF_TIME);
              assertThat(handler.isHasSeenSequenceField()).isFalse();
            });
  }

  @Test
  public void latestPositionImportIndexesAreRestoredAfterRestartOfOptimize() {
    // given
    deployZeebeData();

    importAllZeebeEntitiesFromScratch();

    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    final List<Long> positionsBeforeRestart = getCurrentHandlerPositions();
    final List<OffsetDateTime> lastImportedEntityTimestamps = getLastImportedEntityTimestamps();

    // when
    startAndUseNewOptimizeInstance();
    setupZeebeImportAndReloadConfiguration();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getCurrentHandlerPositions())
        .anySatisfy(position -> assertThat(position).isPositive())
        .isEqualTo(positionsBeforeRestart);
    assertThat(getLastImportedEntityTimestamps()).isEqualTo(lastImportedEntityTimestamps);
  }

  @Test
  public void latestSequenceImportIndexesAreRestoredAfterRestartOfOptimize() {
    // given
    deployZeebeData();

    importAllZeebeEntitiesFromScratch();

    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();
    final List<Long> sequencesBeforeRestart = getCurrentHandlerSequences();
    final List<OffsetDateTime> lastImportedEntityTimestamps = getLastImportedEntityTimestamps();

    // when
    startAndUseNewOptimizeInstance();
    setupZeebeImportAndReloadConfiguration();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getCurrentHandlerSequences())
        .anySatisfy(sequence -> assertThat(sequence).isPositive())
        .isEqualTo(sequencesBeforeRestart);
    assertThat(getLastImportedEntityTimestamps()).isEqualTo(lastImportedEntityTimestamps);
    assertThat(embeddedOptimizeExtension.getAllPositionBasedImportHandlers())
        .filteredOn(handler -> handler.getPersistedSequenceOfLastEntity() > 0)
        .anySatisfy(handler -> assertThat(handler.isHasSeenSequenceField()).isTrue());
  }

  @Test
  public void importIndexCanBeReset() {
    // given
    deployZeebeData();

    importAllZeebeEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.resetPositionBasedImportStartIndexes();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getCurrentHandlerPositions()).allSatisfy(position -> assertThat(position).isZero());
    assertThat(getCurrentHandlerSequences()).allSatisfy(sequence -> assertThat(sequence).isZero());
    assertThat(getLastImportedEntityTimestamps())
        .allSatisfy(timestamp -> assertThat(timestamp).isEqualTo(BEGINNING_OF_TIME));
    assertThat(embeddedOptimizeExtension.getAllPositionBasedImportHandlers())
        .allSatisfy(handler -> assertThat(handler.isHasSeenSequenceField()).isFalse());
  }

  @Test
  public void recordsAreFetchedWithSequenceOrPosition() {
    // given
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setMaxImportPageSize(1);
    embeddedOptimizeExtension.reloadConfiguration();
    deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("aProcess"));
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK);
    waitUntilMinimumDataExportedCount(
        3, // need all records up to the startEvent completing
        DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
        getQueryForProcessableProcessInstanceEvents());
    // change the process start/end records to have no sequence, so we can check that fetcher
    // queries correctly based on position
    removeSequenceFieldOfProcessRecords();
    // change position of a later record, so we can check that once we've seen a sequence field,
    // fetcher queries based on
    // sequence and disregards position
    updatePositionOfStartEventCompletedRecords();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // when importing the first record
    importAllZeebeEntitiesFromScratch(); // process activating - imported

    // then based on position, the first record is the process activating record, no flownode data
    // yet
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(instance -> assertThat(instance.getFlowNodeInstances()).isEmpty());

    // when importing the second and third record based on position (note only records with relevant
    // intent are imported)
    importAllZeebeEntitiesFromLastIndex(); // process activated - fetched but not imported
    importAllZeebeEntitiesFromLastIndex(); // startEvent activating (first record with sequence) -
    // imported

    // then based on position, the next imported record is the startEvent activating record
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance ->
                assertThat(instance.getFlowNodeInstances())
                    .singleElement()
                    .extracting(
                        FlowNodeInstanceDto::getFlowNodeType, FlowNodeInstanceDto::getEndDate)
                    .containsExactly(START_EVENT, null));
    // and it was logged that the importer has seen a sequence field
    positionBasedHandlerLogs.assertContains(
        "First Zeebe record with sequence field for import type zeebeProcessInstanceImportIndex has been imported. "
            + "Zeebe records will now be fetched based on sequence.");

    // when
    importAllZeebeEntitiesFromLastIndex(); // start event activated - fetched but not imported
    importAllZeebeEntitiesFromLastIndex(); // start event completing - fetched but not imported
    importAllZeebeEntitiesFromLastIndex(); // start event completed - imported
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // then based on sequence, the next imported record is the startEvent completed record
    // demonstrating that the importer
    // ignores the position field of the startEvent completed record which was to 9999
    assertThat(getCurrentHandlerPositions()).contains(9999L);
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance -> {
              assertThat(instance.getFlowNodeInstances())
                  .singleElement()
                  .extracting(FlowNodeInstanceDto::getFlowNodeType)
                  .isEqualTo(START_EVENT);
              assertThat(instance.getFlowNodeInstances())
                  .singleElement()
                  .extracting(FlowNodeInstanceDto::getEndDate)
                  .isNotNull();
            });
  }

  @Test
  public void dynamicRecordQueryingIsUsedToFetchNewUnreachableData() {
    // covers the scenario of a "gap" in zeebe record sequences that is bigger than the configured
    // importPageSize,
    // leading to empty pages being fetched. Optimize is expected to be able to recognise this and
    // adjust its import to not get
    // stuck

    // given
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .getImportConfig()
        .setMaxEmptyPagesToImport(3);
    embeddedOptimizeExtension.reloadConfiguration();
    deployAndStartInstanceForProcess(createStartEndProcess("aProcess"));
    waitUntilInstanceRecordWithElementTypeAndIntentExported(
        BpmnElementType.PROCESS, ELEMENT_COMPLETED);

    final List<ZeebeProcessInstanceRecordDto> allProcessInstanceRecords =
        getZeebeExportedProcessInstances();
    allProcessInstanceRecords.sort(Comparator.comparing(ZeebeRecordDto::getPosition));

    // We start by setting all the process instance records to have an unreasonably large sequence
    updateSequenceOfAllProcessInstanceRecords(5000);

    // Then we set the first record back to having a sequence of 1
    updateSequenceOfRecordWithPosition(allProcessInstanceRecords.get(0).getPosition(), 1);
    // and the last record to having a sequence that can be caught in the batch after the high
    // sequenced documents
    updateSequenceOfRecordWithPosition(
        allProcessInstanceRecords.get(allProcessInstanceRecords.size() - 1).getPosition(), 5100);

    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // the first search always uses the position query, so we set the page size here as we only want
    // to import the first record
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setMaxImportPageSize(1);
    embeddedOptimizeExtension.reloadConfiguration();
    // when importing the first record
    importAllZeebeEntitiesFromScratch();

    // then the first record is imported, and the importer has seen a record with a sequence field
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance -> {
              assertThat(instance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
              assertThat(instance.getFlowNodeInstances()).isEmpty();
            });
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    databaseIntegrationTestExtension.refreshAllOptimizeIndices();

    // we can increase the page size again to a more reasonable amount
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setMaxImportPageSize(20);
    embeddedOptimizeExtension.reloadConfiguration();
    // when importing the next page, it gets an empty result as the next record sequence is not
    // caught by the sequence query
    importAllZeebeEntitiesFromLastIndex();
    importAllZeebeEntitiesFromLastIndex();
    importAllZeebeEntitiesFromLastIndex();

    // we confirm that the instance is still not imported
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance -> {
              assertThat(instance.getState()).isEqualTo(ProcessInstanceConstants.ACTIVE_STATE);
              assertThat(instance.getFlowNodeInstances()).isEmpty();
            });

    // After three attempts (we set this value at the beginning of the test) the next one will use
    // the position query to get
    // the next page, if records exist. In this case records do exist
    importAllZeebeEntitiesFromLastIndex();
    final List<String> allLoggedEvents =
        zeebeFetcherLogs.getEvents().stream()
            .map(LoggingEvent::getMessage)
            .collect(Collectors.toList());
    // Only one partition processes all the process instance records in this test. That means the
    // other partition hasn't seen
    // a record with sequence yet, and thus this log message will only be seen once
    assertThat(allLoggedEvents)
        .filteredOn(
            loggedMessage ->
                loggedMessage.contains(
                    "Using the position query to see if there are new records in the process-instance index"))
        .hasSize(1);
    // This log should only appear once, on the partition that is handling the process instance
    // events
    assertThat(allLoggedEvents)
        .filteredOn(
            loggedMessage ->
                loggedMessage.contains(
                    "that can't be imported by the current sequence query. Will revert to position query for the next fetch attempt"))
        .hasSize(1);

    // Once the next page is fetched
    importAllZeebeEntitiesFromLastIndex();

    // Then the import for this instance is complete
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance -> {
              assertThat(instance.getState()).isEqualTo(ProcessInstanceConstants.COMPLETED_STATE);
              assertThat(instance.getFlowNodeInstances()).hasSize(2);
            });
  }

  @Test
  public void dynamicRecordQueryingIsUsedToFetchNewUnreachableData_noUnreachableData() {
    // given
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .setMaxImportPageSize(1000);
    embeddedOptimizeExtension
        .getConfigurationService()
        .getConfiguredZeebe()
        .getImportConfig()
        .setMaxEmptyPagesToImport(3);
    embeddedOptimizeExtension.reloadConfiguration();
    deployAndStartInstanceForProcess(createStartEndProcess("aProcess"));
    waitUntilInstanceRecordWithElementTypeAndIntentExported(
        BpmnElementType.PROCESS, ELEMENT_COMPLETED);
    importAllZeebeEntitiesFromScratch();
    assertThat(databaseIntegrationTestExtension.getAllProcessInstances())
        .singleElement()
        .satisfies(
            instance ->
                assertThat(instance.getState())
                    .isEqualTo(ProcessInstanceConstants.COMPLETED_STATE));

    // when the configured number of consecutive empty pages are fetched
    importAllZeebeEntitiesFromLastIndex();
    importAllZeebeEntitiesFromLastIndex();
    importAllZeebeEntitiesFromLastIndex();

    // and the next page is requested
    importAllZeebeEntitiesFromLastIndex();

    // then it uses the position query to fetch data
    assertThat(zeebeFetcherLogs.getEvents())
        .extracting(LoggingEvent::getMessage)
        .anyMatch(
            eventLog ->
                eventLog.contains(
                    "Using the position query to see if there are new records in the process-instance index"));
    assertThat(zeebeFetcherLogs.getEvents())
        .extracting(LoggingEvent::getMessage)
        .anyMatch(
            eventLog ->
                eventLog.contains(
                    "There are no newer records to process, so empty pages of records are currently expected"));
  }

  private void deployZeebeData() {
    deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("firstProcess"));
    deployAndStartInstanceForProcess(createSimpleServiceTaskProcess("secondProcess"));
    waitUntilMinimumDataExportedCount(
        8,
        DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
        getQueryForProcessableProcessInstanceEvents());
  }

  private void removeSequenceFieldOfProcessRecords() {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("sequenceFieldName", ZeebeRecordDto.Fields.sequence)
                .build());

    databaseIntegrationTestExtension.updateZeebeProcessRecordsOfBpmnElementTypeForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        BpmnElementType.PROCESS,
        substitutor.replace("ctx._source.remove(\"${sequenceFieldName}\");"));
  }

  private void updatePositionOfStartEventCompletedRecords() {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("positionField", ZeebeRecordDto.Fields.position)
                .put("intentField", ZeebeRecordDto.Fields.intent)
                .build());

    databaseIntegrationTestExtension.updateZeebeProcessRecordsOfBpmnElementTypeForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        BpmnElementType.START_EVENT,
        substitutor.replace(
            "if (ctx._source.${intentField}.equals('ELEMENT_COMPLETED')) { ctx._source.${positionField} = 9999; }"));
  }

  private void updateSequenceOfAllProcessInstanceRecords(final long sequence) {
    databaseIntegrationTestExtension.updateZeebeRecordsForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
        getUpdateScript(sequence));
  }

  private void updateSequenceOfRecordWithPosition(final long position, final long sequence) {
    databaseIntegrationTestExtension.updateZeebeRecordsWithPositionForPrefix(
        zeebeExtension.getZeebeRecordPrefix(),
        DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
        position,
        getUpdateScript(sequence));
  }

  private String getUpdateScript(final long sequence) {
    final StringSubstitutor substitutor =
        new StringSubstitutor(
            ImmutableMap.<String, String>builder()
                .put("fieldName", ZeebeRecordDto.Fields.sequence)
                .put("sequence", String.valueOf(sequence))
                .build());
    return substitutor.replace("ctx._source.${fieldName} = ${sequence};");
  }

  private List<Long> getCurrentHandlerPositions() {
    return embeddedOptimizeExtension.getAllPositionBasedImportHandlers().stream()
        .map(PositionBasedImportIndexHandler::getPersistedPositionOfLastEntity)
        .collect(Collectors.toList());
  }

  private List<Long> getCurrentHandlerSequences() {
    return embeddedOptimizeExtension.getAllPositionBasedImportHandlers().stream()
        .map(PositionBasedImportIndexHandler::getPersistedSequenceOfLastEntity)
        .collect(Collectors.toList());
  }

  private List<OffsetDateTime> getLastImportedEntityTimestamps() {
    return embeddedOptimizeExtension.getAllPositionBasedImportHandlers().stream()
        .map(PositionBasedImportIndexHandler::getTimestampOfLastPersistedEntity)
        .collect(Collectors.toList());
  }

  private List<ZeebeProcessInstanceRecordDto> getZeebeExportedProcessInstances() {
    final String expectedIndex =
        zeebeExtension.getZeebeRecordPrefix()
            + "-"
            + DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
    return databaseIntegrationTestExtension.getAllDocumentsOfIndexAs(
        expectedIndex, ZeebeProcessInstanceRecordDto.class);
  }
}
