/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.severalversions;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.operate.zeebeimport.severalversions.ImportSeveralVersionsInitializer.OPERATE_PREFIX;
import static io.camunda.operate.zeebeimport.severalversions.ImportSeveralVersionsInitializer.ZEEBE_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.TestImportListener;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.camunda.operate.zeebeimport.v8_3.processors.ElasticsearchBulkProcessor;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = ImportSeveralVersionsInitializer.class)
public class ImportSeveralVersionsIT extends OperateIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(ImportSeveralVersionsIT.class);
  private static final int TIMEOUT_IN_SECONDS = 5 * 60; // 5 minutes
  private static final int TIMEOUT_IN_MILLIS = TIMEOUT_IN_SECONDS * 1000; // 5 minutes

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule(OPERATE_PREFIX);

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  private TestImportListener countImportListener;

  @Autowired
  private ProcessIndex processIndex;

  @Autowired
  private ListViewTemplate listViewTemplate;

  @Autowired
  private IncidentTemplate incidentTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Value("${test.wiCount}")
  private int wiCount;

  @Value("${test.finishedCount}")
  private int finishedCount;

  @Value("${test.incidentCount}")
  private int incidentCount;

  @SpyBean
  private ElasticsearchBulkProcessor importerv2;

  @SpyBean
  private io.camunda.operate.zeebeimport.v8_2.processors.ElasticsearchBulkProcessor importerv1;

  @MockBean
  private PartitionHolder partitionHolder;

  @Autowired
  private RecordsReaderHolder recordsReaderHolder;

  @Before
  public void beforeTest() {
    when(partitionHolder.getPartitionIds()).thenReturn(Arrays.asList(1));
  }

  @After
  public void afterTest() {
    TestUtil.removeAllIndices(esClient, ZEEBE_PREFIX);
    TestUtil.removeAllIndices(esClient, OPERATE_PREFIX);
  }

  @Test
  @Ignore("https://github.com/camunda/operate/issues/3713")
  public void shouldImportFromSeveralZeebeVersions() throws PersistenceException {
    //when
    AtomicBoolean hold = new AtomicBoolean(true);
    zeebeImporter.scheduleReaders();
    countImportListener.setBatchFinishedListener(() -> {
      logger.info("Batch finished. Imported batches: " + countImportListener.getImportedCount() + "   ::   scheduled: " + countImportListener.getScheduledCount());
      if (countImportListener.getImportedCount() == countImportListener.getScheduledCount()) {
        hold.set(false);
        logger.info("All readers have finished importing batch");
      }
    });

    int waitingFor = 0;
    while (hold.get()) {
      // waiting callback to be executed
      if (waitingFor == TIMEOUT_IN_MILLIS) {
        fail("timeout... Tests waited " + TIMEOUT_IN_SECONDS + " seconds for batches to finish");
      }
      waitingFor += 1000;
      sleepFor(1000);
    }

    // then
    executeAndRetry(60, 1000L, this::assertOperateData);
    //make sure that both importers were called
    verify(importerv1, atLeastOnce()).performImport(any(ImportBatch.class));
    verify(importerv2, atLeastOnce()).performImport(any(ImportBatch.class));
  }

  private void assertOperateData() {
    try {
      ElasticsearchUtil.flushData(esClient);

      //assert process count
      int count = ElasticsearchUtil.getDocCount(esClient, processIndex.getAlias());
      assertThat(count).isEqualTo(1);

      //assert process instances count
      count = ElasticsearchUtil.getFieldCardinality(esClient, listViewTemplate.getAlias(), ListViewTemplate.PROCESS_INSTANCE_KEY);
      assertThat(count).isEqualTo(wiCount);

      //assert finished count
      final TermQueryBuilder isProcessInstanceQuery = termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
      count = ElasticsearchUtil.getFieldCardinalityWithRequest(esClient, listViewTemplate.getAlias(), ListViewTemplate.END_DATE, isProcessInstanceQuery);
      assertThat(count).isEqualTo(finishedCount);

      //assert incidents count
      count = ElasticsearchUtil.getFieldCardinality(esClient, incidentTemplate.getAlias(), IncidentTemplate.KEY);
      assertThat(count).isEqualTo(incidentCount);
    } catch (IOException ex) {
      fail("Unable to assert data.", ex);
    }
  }

  private void executeAndRetry(int retryTimes, long waitTime, Runnable execute)  {
    executeAndRetry(0, retryTimes, waitTime, execute);
  }

  private void executeAndRetry(int currentRun, int retryTimes, long waitTime, Runnable execute) {
    currentRun++;
    try {
      execute.run();
    } catch (Exception | AssertionError ex) {
      if (currentRun > retryTimes) {
        throw ex;
      } else {
        sleepFor(waitTime);
        executeAndRetry(currentRun, retryTimes, waitTime, execute);
      }
    }
  }


  // ImportSeveralVersionsIT.shouldImportFromSeveralZeebeVersions:138
  //    ->executeAndRetry:178
  //    ->executeAndRetry:184
  //    ->assertOperateData:163
  //        expected:<[2]> but was:<[1]>
}
