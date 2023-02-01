/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import com.google.common.collect.ImmutableMap;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.github.netmikey.logunit.api.LogCapturer;
import lombok.SneakyThrows;
import org.apache.commons.text.StringSubstitutor;
import org.camunda.optimize.AbstractZeebeIT;
import org.camunda.optimize.dto.optimize.query.event.process.FlowNodeInstanceDto;
import org.camunda.optimize.dto.zeebe.ZeebeRecordDto;
import org.camunda.optimize.dto.zeebe.process.ZeebeProcessInstanceDataDto;
import org.camunda.optimize.service.importing.zeebe.fetcher.AbstractZeebeRecordFetcher;
import org.camunda.optimize.test.it.extension.IntegrationTestConfigurationUtil;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.camunda.optimize.util.ZeebeBpmnModels;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.BpmnModels.START_EVENT;
import static org.camunda.optimize.util.SuppressionConstants.UNUSED;
import static org.camunda.optimize.util.ZeebeBpmnModels.SERVICE_TASK;
import static org.elasticsearch.index.query.QueryBuilders.boolQuery;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

public class PositionBasedImportIndexIT extends AbstractZeebeIT {

  public static final OffsetDateTime BEGINNING_OF_TIME = OffsetDateTime.ofInstant(
    Instant.EPOCH,
    ZoneId.systemDefault()
  );

  @RegisterExtension
  @Order(1)
  private final LogCapturer logCapturer = LogCapturer.create().captureForType(AbstractZeebeRecordFetcher.class);

  @Test
  public void importPositionIsZeroIfNothingIsImportedYet() {
    // when
    final List<PositionBasedImportIndexHandler> positionBasedHandlers =
      embeddedOptimizeExtension.getAllPositionBasedImportHandlers();

    // then
    assertThat(positionBasedHandlers).hasSize(8)
      .allSatisfy(handler -> {
        assertThat(handler.getPersistedPositionOfLastEntity()).isZero();
        assertThat(handler.getPendingSequenceOfLastEntity()).isZero();
        assertThat(handler.getTimestampOfLastPersistedEntity()).isEqualTo(BEGINNING_OF_TIME);
        assertThat(handler.getLastImportExecutionTimestamp()).isEqualTo(BEGINNING_OF_TIME);
      });
  }

  @Test
  @SneakyThrows
  public void latestPositionImportIndexesAreRestoredAfterRestartOfOptimize() {
    // given
    deployZeebeData();

    importAllZeebeEntitiesFromScratch();

    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    final List<Long> positionsBeforeRestart = getCurrentHandlerPositions();
    final List<OffsetDateTime> lastImportedEntityTimestamps = getLastImportedEntityTimestamps();

    // when
    startAndUseNewOptimizeInstance();
    setupZeebeImportAndReloadConfiguration();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getCurrentHandlerPositions())
      .anySatisfy(position -> assertThat(position).isPositive())
      .isEqualTo(positionsBeforeRestart);
    assertThat(getLastImportedEntityTimestamps()).isEqualTo(lastImportedEntityTimestamps);
  }

  // this test is disabled for versions pre 8.2.0 because it relies on the sequence field being present in zeebe records
  @DisabledIf("isZeebeVersionPreSequenceField")
  @Test
  @SneakyThrows
  public void latestSequenceImportIndexesAreRestoredAfterRestartOfOptimize() {
    // given
    deployZeebeData();

    importAllZeebeEntitiesFromScratch();

    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();
    final List<Long> sequencesBeforeRestart = getCurrentHandlerSequences();
    final List<OffsetDateTime> lastImportedEntityTimestamps = getLastImportedEntityTimestamps();

    // when
    startAndUseNewOptimizeInstance();
    setupZeebeImportAndReloadConfiguration();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getCurrentHandlerSequences())
      .anySatisfy(sequence -> assertThat(sequence).isPositive())
      .isEqualTo(sequencesBeforeRestart);
    assertThat(getLastImportedEntityTimestamps()).isEqualTo(lastImportedEntityTimestamps);
  }

  @Test
  public void importIndexCanBeReset() {
    // given
    deployZeebeData();

    importAllZeebeEntitiesFromScratch();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when
    embeddedOptimizeExtension.resetImportStartIndexes();
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then
    assertThat(getCurrentHandlerPositions()).allSatisfy(position -> assertThat(position).isZero());
    assertThat(getCurrentHandlerSequences()).allSatisfy(sequence -> assertThat(sequence).isZero());
    assertThat(getLastImportedEntityTimestamps())
      .allSatisfy(timestamp -> assertThat(timestamp).isEqualTo(BEGINNING_OF_TIME));
  }

  // this test is disabled for versions pre 8.2.0 because it relies on the sequence field being present in zeebe records
  @DisabledIf("isZeebeVersionPreSequenceField")
  @Test
  public void recordsAreFetchedWithSequenceOrPosition() {
    // given
    getEmbeddedOptimizeExtension().getConfigurationService().getConfiguredZeebe().setMaxImportPageSize(1);
    embeddedOptimizeExtension.reloadConfiguration();
    deployAndStartInstanceForProcess(ZeebeBpmnModels.createSimpleServiceTaskProcess("aProcess"));
    zeebeExtension.completeTaskForInstanceWithJobType(SERVICE_TASK);
    waitUntilMinimumDataExportedCount(
      3, // need all records up to the startEvent completing
      ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      getQueryForProcessableEvents()
    );
    // change the process start/end records to have no sequence so we can check that fetcher queries correctly based on position
    removeSequenceFieldOfProcessRecords();
    // change position of a later record so we can check that once we've seen a sequence field, fetcher queries based on
    // sequence and disregards position
    updatePositionOfStartEventCompletedRecords();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // when importing the first record
    importAllZeebeEntitiesFromScratch(); // process activating - imported

    // then based on position, the first record is the process activating record, no flownode data yet
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> assertThat(instance.getFlowNodeInstances()).isEmpty());

    // when importing the second and third record based on position (note only records with relevant intent are imported)
    importAllZeebeEntitiesFromLastIndex(); // process activated - fetched but not imported
    importAllZeebeEntitiesFromLastIndex(); // startEvent activating (first record with sequence) - imported

    // then based on position, the next imported record is the startEvent activating record
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> assertThat(instance.getFlowNodeInstances()).singleElement()
        .extracting(FlowNodeInstanceDto::getFlowNodeType, FlowNodeInstanceDto::getEndDate)
        .containsExactly(START_EVENT, null));
    // and it was logged that the importer has seen a sequence field
    logCapturer.assertContains(
      "First Zeebe record with sequence field has been imported. Zeebe records will now be fetched based on sequence");

    // when
    importAllZeebeEntitiesFromLastIndex();  // start event activated - fetched but not imported
    importAllZeebeEntitiesFromLastIndex();  // start event completing - fetched but not imported
    importAllZeebeEntitiesFromLastIndex();  // start event completed - imported
    embeddedOptimizeExtension.storeImportIndexesToElasticsearch();
    elasticSearchIntegrationTestExtension.refreshAllOptimizeIndices();

    // then based on sequence, the next imported record is the startEvent completed record demonstrating that the importer
    // ignores the position field of the startEvent completed record which was to 9999
    assertThat(getCurrentHandlerPositions()).contains(9999L);
    assertThat(elasticSearchIntegrationTestExtension.getAllProcessInstances())
      .singleElement()
      .satisfies(instance -> {
        assertThat(instance.getFlowNodeInstances()).singleElement()
          .extracting(FlowNodeInstanceDto::getFlowNodeType)
          .isEqualTo(START_EVENT);
        assertThat(instance.getFlowNodeInstances()).singleElement().extracting(FlowNodeInstanceDto::getEndDate).isNotNull();
      });
  }

  private void deployZeebeData() {
    deployAndStartInstanceForProcess(ZeebeBpmnModels.createSimpleServiceTaskProcess("firstProcess"));
    deployAndStartInstanceForProcess(ZeebeBpmnModels.createSimpleServiceTaskProcess("secondProcess"));
    waitUntilMinimumDataExportedCount(
      8,
      ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      getQueryForProcessableEvents()
    );
  }

  private void removeSequenceFieldOfProcessRecords() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder().put("sequenceFieldName", ZeebeRecordDto.Fields.sequence).build()
    );

    elasticSearchIntegrationTestExtension.updateZeebeRecordsForPrefix(
      zeebeExtension.getZeebeRecordPrefix(),
      ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      boolQuery()
        .must(termQuery(
          ZeebeRecordDto.Fields.value + "." + ZeebeProcessInstanceDataDto.Fields.bpmnElementType,
          BpmnElementType.PROCESS.name()
        )),
      substitutor.replace("ctx._source.remove(\"${sequenceFieldName}\");")
    );
  }

  private void updatePositionOfStartEventCompletedRecords() {
    final StringSubstitutor substitutor = new StringSubstitutor(
      ImmutableMap.<String, String>builder().put("positionFieldName", ZeebeRecordDto.Fields.position).build()
    );

    elasticSearchIntegrationTestExtension.updateZeebeRecordsForPrefix(
      zeebeExtension.getZeebeRecordPrefix(),
      ElasticsearchConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME,
      boolQuery()
        .must(termQuery(
          ZeebeRecordDto.Fields.value + "." + ZeebeProcessInstanceDataDto.Fields.bpmnElementType,
          BpmnElementType.START_EVENT.name()
        ))
        .must(termQuery(ZeebeRecordDto.Fields.intent, ELEMENT_COMPLETED.name())),
      substitutor.replace("ctx._source.${positionFieldName} = 9999;")
    );
  }

  private List<Long> getCurrentHandlerPositions() {
    return embeddedOptimizeExtension.getAllPositionBasedImportHandlers()
      .stream()
      .map(PositionBasedImportIndexHandler::getPersistedPositionOfLastEntity)
      .collect(Collectors.toList());
  }

  private List<Long> getCurrentHandlerSequences() {
    return embeddedOptimizeExtension.getAllPositionBasedImportHandlers()
      .stream()
      .map(PositionBasedImportIndexHandler::getPersistedSequenceOfLastEntity)
      .collect(Collectors.toList());
  }

  private List<OffsetDateTime> getLastImportedEntityTimestamps() {
    return embeddedOptimizeExtension.getAllPositionBasedImportHandlers()
      .stream()
      .map(PositionBasedImportIndexHandler::getTimestampOfLastPersistedEntity)
      .collect(Collectors.toList());
  }

  @SuppressWarnings(UNUSED)
  private static boolean isZeebeVersionPreSequenceField() {
    final Pattern zeebeVersionPreSequenceField = Pattern.compile("8.0.*|8.1.*");
    return zeebeVersionPreSequenceField.matcher(IntegrationTestConfigurationUtil.getZeebeDockerVersion()).matches();
  }

}
