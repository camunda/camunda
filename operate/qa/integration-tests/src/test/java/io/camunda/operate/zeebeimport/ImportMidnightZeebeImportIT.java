/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.operate.zeebe.ImportValueType.PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.OperateZeebeAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.util.searchrepository.TestSearchRepository;
import io.camunda.operate.webapp.elasticsearch.reader.ProcessInstanceReader;
import io.camunda.operate.zeebeimport.elasticsearch.ElasticsearchRecordsReader;
import io.camunda.webapps.schema.entities.operate.FlowNodeInstanceEntity;
import io.camunda.webapps.schema.entities.operate.FlowNodeState;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceForListViewEntity;
import io.camunda.webapps.schema.entities.operate.listview.ProcessInstanceState;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;

@SpringBootTest(
    classes = {TestApplication.class},
    properties = {
      OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
      OperateProperties.PREFIX + ".importer.threadsCount = 1",
      OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
      "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"
    })
public class ImportMidnightZeebeImportIT extends OperateZeebeAbstractIT {

  @Rule
  public SearchTestRule searchTestRule =
      new SearchTestRule() {
        @Override
        public void refreshZeebeIndices() {
          // do nothing
        }
      };

  @Autowired private ProcessInstanceReader processInstanceReader;
  @Autowired private OperateProperties operateProperties;
  @SpyBean private RecordsReaderHolder recordsReaderHolder;
  @Autowired private TestSearchRepository testSearchRepository;

  @Override
  public void before() {
    super.before();
  }

  @Test
  public void testProcessInstancesCompletedNextDay() throws IOException {
    // having
    final String processId = "demoProcess";
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(processId)
            .startEvent("start")
            .serviceTask("task1")
            .zeebeJobType("task1")
            .serviceTask("task2")
            .zeebeJobType("task2")
            .endEvent()
            .done();
    deployProcess(process, "demoProcess_v_1.bpmn");
    mockRecordsReaderToImitateNPE(); // #3861

    // disable automatic index refreshes
    zeebeRule.updateRefreshInterval("-1");

    final Instant firstDate = pinZeebeTime();
    fillIndicesWithData(processId, firstDate);

    // start process instance
    final long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    completeTask(processInstanceKey, "task1", null, false);
    // let Zeebe export data
    sleepFor(5000);
    // complete instances next day
    final Instant secondDate = firstDate.plus(1, ChronoUnit.DAYS);
    pinZeebeTime(secondDate);
    completeTask(processInstanceKey, "task2", null, false);
    // let Zeebe export data
    sleepFor(5000);
    duplicateRecords(firstDate, secondDate);

    // when
    // refresh 2nd date index and load all data
    searchTestRule.processAllRecordsAndWait(
        processInstanceIsCompletedCheck,
        () -> {
          zeebeRule.refreshIndices(secondDate);
          return null;
        },
        processInstanceKey);

    // then internally previous index will also be refreshed and full data will be loaded
    final ProcessInstanceForListViewEntity pi =
        processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(pi.getState()).isEqualTo(ProcessInstanceState.COMPLETED);

    // assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances =
        tester.getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(4);
    FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getFlowNodeId()).isEqualTo("task1");
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate())
        .isAfterOrEqualTo(OffsetDateTime.ofInstant(firstDate, ZoneOffset.systemDefault()));

    activity = allFlowNodeInstances.get(2);
    assertThat(activity.getFlowNodeId()).isEqualTo("task2");
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate())
        .isAfterOrEqualTo(OffsetDateTime.ofInstant(secondDate, ZoneOffset.systemDefault()));
  }

  private void duplicateRecords(final Instant firstDate, final Instant secondDate)
      throws IOException {
    final DateTimeFormatter dateTimeFormatter =
        DateTimeFormatter.ofPattern(operateProperties.getArchiver().getRolloverDateFormat())
            .withZone(ZoneId.systemDefault());
    final String firstDateStr = dateTimeFormatter.format(firstDate);
    final String secondDateStr = dateTimeFormatter.format(secondDate);
    final String script =
        "ctx._index = ctx._index.replace( \"" + firstDateStr + "\", \"" + secondDateStr + "\");";

    testSearchRepository.reindex(zeebeRule.getPrefix() + "*", "generated", script, Map.of());
  }

  /**
   * We don't know the root cause of this behaviour, but customer faced the situation, when index
   * reread returned empty batch
   */
  private void mockRecordsReaderToImitateNPE() {
    final RecordsReader processInstanceRecordsReader =
        recordsReaderHolder.getRecordsReader(1, PROCESS_INSTANCE);
    when(recordsReaderHolder.getRecordsReader(eq(1), eq(PROCESS_INSTANCE)))
        .thenReturn(
            new ElasticsearchRecordsReader(1, PROCESS_INSTANCE, 5) {
              boolean calledOnce = false;

              @Override
              public ImportBatch readNextBatchBySequence(
                  final Long sequence, final Long lastSequence) throws NoSuchIndexException {
                if (calledOnce) {
                  return processInstanceRecordsReader.readNextBatchBySequence(
                      sequence, lastSequence);
                } else {
                  calledOnce = true;
                  return new ImportBatch(1, PROCESS_INSTANCE, new ArrayList<>(), null);
                }
              }

              @Override
              public ImportBatch readNextBatchByPositionAndPartition(
                  final long positionFrom, final Long positionTo) throws NoSuchIndexException {
                if (calledOnce) {
                  return processInstanceRecordsReader.readNextBatchByPositionAndPartition(
                      positionFrom, positionTo);
                } else {
                  calledOnce = true;
                  return new ImportBatch(1, PROCESS_INSTANCE, new ArrayList<>(), null);
                }
              }
            });
  }

  public void fillIndicesWithData(final String processId, final Instant firstDate) {
    // two instances for two partitions
    long processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelProcessInstance(processInstanceKey, false);
    sleepFor(2000);
    zeebeRule.refreshIndices(firstDate);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    processInstanceKey =
        ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelProcessInstance(processInstanceKey, false);
    sleepFor(2000);
    zeebeRule.refreshIndices(firstDate);
    searchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
  }
}
