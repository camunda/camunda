/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport.severalversions;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.operate.zeebeimport.severalversions.ImportSeveralVersionsInitializer.OPERATE_PREFIX;
import static io.camunda.operate.zeebeimport.severalversions.ImportSeveralVersionsInitializer.ZEEBE_PREFIX;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestImportListener;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import io.camunda.operate.zeebeimport.processors.ImportBulkProcessor;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.operate.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
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
public class ImportSeveralVersionsZeebeImportIT extends OperateAbstractIT {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ImportSeveralVersionsZeebeImportIT.class);
  private static final int TIMEOUT_IN_SECONDS = 5 * 60; // 5 minutes
  private static final int TIMEOUT_IN_MILLIS = TIMEOUT_IN_SECONDS * 1000; // 5 minutes

  @Rule public SearchTestRule searchTestRule = new SearchTestRule(OPERATE_PREFIX);

  @Autowired private ZeebeImporter zeebeImporter;

  @Autowired private TestImportListener countImportListener;

  @Autowired private ProcessIndex processIndex;

  @Autowired private ListViewTemplate listViewTemplate;

  @Autowired private IncidentTemplate incidentTemplate;

  @Autowired private RestHighLevelClient esClient;

  @Value("${test.wiCount}")
  private int wiCount;

  @Value("${test.finishedCount}")
  private int finishedCount;

  @Value("${test.incidentCount}")
  private int incidentCount;

  @SpyBean private ImportBulkProcessor importerv2;

  @SpyBean private io.camunda.operate.zeebeimport.v8_6.processors.ImportBulkProcessor importerv1;

  @MockBean private PartitionHolder partitionHolder;

  @Autowired private RecordsReaderHolder recordsReaderHolder;

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
    // when
    final AtomicBoolean hold = new AtomicBoolean(true);
    zeebeImporter.scheduleReaders();
    countImportListener.setBatchFinishedListener(
        () -> {
          LOGGER.info(
              "Batch finished. Imported batches: "
                  + countImportListener.getImportedCount()
                  + "   ::   scheduled: "
                  + countImportListener.getScheduledCount());
          if (countImportListener.getImportedCount() == countImportListener.getScheduledCount()) {
            hold.set(false);
            LOGGER.info("All readers have finished importing batch");
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
    // make sure that both importers were called
    verify(importerv1, atLeastOnce()).performImport(any(ImportBatch.class));
    verify(importerv2, atLeastOnce()).performImport(any(ImportBatch.class));
  }

  private void assertOperateData() {
    try {
      ElasticsearchUtil.flushData(esClient);

      // assert process count
      int count = ElasticsearchUtil.getDocCount(esClient, processIndex.getAlias());
      assertThat(count).isEqualTo(1);

      // assert process instances count
      count =
          ElasticsearchUtil.getFieldCardinality(
              esClient, listViewTemplate.getAlias(), ListViewTemplate.PROCESS_INSTANCE_KEY);
      assertThat(count).isEqualTo(wiCount);

      // assert finished count
      final TermQueryBuilder isProcessInstanceQuery =
          termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
      count =
          ElasticsearchUtil.getFieldCardinalityWithRequest(
              esClient,
              listViewTemplate.getAlias(),
              ListViewTemplate.END_DATE,
              isProcessInstanceQuery);
      assertThat(count).isEqualTo(finishedCount);

      // assert incidents count
      count =
          ElasticsearchUtil.getFieldCardinality(
              esClient, incidentTemplate.getAlias(), IncidentTemplate.KEY);
      assertThat(count).isEqualTo(incidentCount);
    } catch (final IOException ex) {
      fail("Unable to assert data.", ex);
    }
  }

  private void executeAndRetry(final int retryTimes, final long waitTime, final Runnable execute) {
    executeAndRetry(0, retryTimes, waitTime, execute);
  }

  private void executeAndRetry(
      int currentRun, final int retryTimes, final long waitTime, final Runnable execute) {
    currentRun++;
    try {
      execute.run();
    } catch (final Exception | AssertionError ex) {
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
