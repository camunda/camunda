/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import io.camunda.operate.entities.FlowNodeInstanceEntity;
import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.exceptions.NoSuchIndexException;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.util.TestApplication;
import io.camunda.operate.entities.listview.ProcessInstanceForListViewEntity;
import io.camunda.operate.entities.listview.ProcessInstanceState;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateZeebeIntegrationTest;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.webapp.es.reader.ProcessInstanceReader;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.reindex.ReindexRequest;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import org.springframework.boot.test.mock.mockito.SpyBean;

import static io.camunda.operate.zeebe.ImportValueType.PROCESS_INSTANCE;
import static org.assertj.core.api.Assertions.assertThat;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = { TestApplication.class},
    properties = { OperateProperties.PREFIX + ".importer.startLoadingDataOnStartup = false",
        OperateProperties.PREFIX + ".importer.threadsCount = 1",
        OperateProperties.PREFIX + ".archiver.rolloverEnabled = false",
        "spring.mvc.pathmatch.matching-strategy=ANT_PATH_MATCHER"})
public class ImportMidnightIT extends OperateZeebeIntegrationTest {

  @Autowired
  private ProcessInstanceReader processInstanceReader;

  @Autowired
  private OperateProperties operateProperties;

  @SpyBean
  private RecordsReaderHolder recordsReaderHolder;

  @Autowired
  private RestHighLevelClient esClient;

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule  = new ElasticsearchTestRule() {
    @Override
    public void refreshZeebeESIndices() {
      //do nothing
    }
  };

  @Override
  public void before() {
    super.before();
  }

  @Test
  public void testProcessInstancesCompletedNextDay() throws IOException {
    // having
    String processId = "demoProcess";
    BpmnModelInstance process = Bpmn.createExecutableProcess(processId)
        .startEvent("start")
          .serviceTask("task1").zeebeJobType("task1")
          .serviceTask("task2").zeebeJobType("task2")
        .endEvent().done();
    deployProcess(process, "demoProcess_v_1.bpmn");
    mockRecordsReaderToImitateNPE();    //#3861

    //disable automatic index refreshes
    zeebeRule.updateRefreshInterval("-1");

    final Instant firstDate = pinZeebeTime();
    fillIndicesWithData(processId, firstDate);

    //start process instance
    long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    completeTask(processInstanceKey, "task1", null, false);
    //let Zeebe export data
    sleepFor(5000);
    //complete instances next day
    Instant secondDate = firstDate.plus(1, ChronoUnit.DAYS);
    pinZeebeTime(secondDate);
    completeTask(processInstanceKey, "task2", null, false);
    //let Zeebe export data
    sleepFor(5000);
    duplicateRecords(firstDate, secondDate);

    //when
    //refresh 2nd date index and load all data
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCompletedCheck, () -> {
      zeebeRule.refreshIndices(secondDate);
      return null;
    }, processInstanceKey);

    //then internally previous index will also be refreshed and full data will be loaded
    ProcessInstanceForListViewEntity pi = processInstanceReader.getProcessInstanceByKey(processInstanceKey);
    assertThat(pi.getState()).isEqualTo(ProcessInstanceState.COMPLETED);

    //assert flow node instances
    final List<FlowNodeInstanceEntity> allFlowNodeInstances = tester
        .getAllFlowNodeInstances(processInstanceKey);
    assertThat(allFlowNodeInstances).hasSize(4);
    FlowNodeInstanceEntity activity = allFlowNodeInstances.get(1);
    assertThat(activity.getFlowNodeId()).isEqualTo("task1");
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(OffsetDateTime.ofInstant(firstDate, ZoneOffset.systemDefault()));

    activity = allFlowNodeInstances.get(2);
    assertThat(activity.getFlowNodeId()).isEqualTo("task2");
    assertThat(activity.getState()).isEqualTo(FlowNodeState.COMPLETED);
    assertThat(activity.getEndDate()).isAfterOrEqualTo(OffsetDateTime.ofInstant(secondDate, ZoneOffset.systemDefault()));

  }

  private void duplicateRecords(Instant firstDate, Instant secondDate) throws IOException {
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(
        operateProperties.getArchiver().getRolloverDateFormat()).withZone(ZoneId.systemDefault());
    String firstDateStr = dateTimeFormatter.format(firstDate);
    String secondDateStr = dateTimeFormatter.format(secondDate);
    String script = "ctx._index = ctx._index.replace( \"" + firstDateStr + "\", \"" + secondDateStr + "\");";
    ReindexRequest reindexRequest = new ReindexRequest().setSourceIndices(zeebeRule.getPrefix() + "*")
        .setDestIndex("generated")
        .setScript(new Script(ScriptType.INLINE, Script.DEFAULT_SCRIPT_LANG, script, new HashMap<>()));
    esClient.reindex(reindexRequest, RequestOptions.DEFAULT);
  }

  /**
   * We don't know the root cause of this behaviour, but customer faced the situation, when index reread returned empty batch
   */
  private void mockRecordsReaderToImitateNPE() {
    RecordsReader processInstanceRecordsReader = recordsReaderHolder.getRecordsReader(1, PROCESS_INSTANCE);
    when(recordsReaderHolder.getRecordsReader(eq(1), eq(PROCESS_INSTANCE))).thenReturn(new RecordsReader(1, PROCESS_INSTANCE, 5) {
      boolean calledOnce = false;

      @Override
      public ImportBatch readNextBatchByPositionAndPartition(long positionFrom, Long positionTo) throws NoSuchIndexException {
        if (calledOnce) {
          return processInstanceRecordsReader.readNextBatchByPositionAndPartition(positionFrom, positionTo);
        } else {
          calledOnce = true;
          return new ImportBatch(1, PROCESS_INSTANCE, new ArrayList<>(), null);
        }
      }

      @Override
      public ImportBatch readNextBatchBySequence(final Long sequence, final Long lastSequence) throws NoSuchIndexException {
        if (calledOnce) {
          return processInstanceRecordsReader.readNextBatchBySequence(sequence, lastSequence);
        } else {
          calledOnce = true;
          return new ImportBatch(1, PROCESS_INSTANCE, new ArrayList<>(), null);
        }
      }
    });
  }

  public void fillIndicesWithData(String processId, Instant firstDate) {
    //two instances for two partitions
    long processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelProcessInstance(processInstanceKey, false);
    sleepFor(2000);
    zeebeRule.refreshIndices(firstDate);
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
    processInstanceKey = ZeebeTestUtil.startProcessInstance(zeebeClient, processId, "{\"a\": \"b\"}");
    cancelProcessInstance(processInstanceKey, false);
    sleepFor(2000);
    zeebeRule.refreshIndices(firstDate);
    elasticsearchTestRule.processAllRecordsAndWait(processInstanceIsCanceledCheck, processInstanceKey);
  }

}
