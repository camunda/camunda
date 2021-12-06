/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.zeebeimport.severalversions;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.IncidentTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ElasticsearchTestRule;
import io.camunda.operate.util.OperateIntegrationTest;
import io.camunda.operate.util.TestImportListener;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import java.io.IOException;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(initializers = ImportSeveralVersionsInitializer.class)
public class ImportSeveralVersionsTest extends OperateIntegrationTest {

  private static final Logger logger = LoggerFactory.getLogger(ImportSeveralVersionsTest.class);

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule(ImportSeveralVersionsInitializer.OPERATE_PREFIX);

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
  private io.camunda.operate.zeebeimport.v1_3.processors.ElasticsearchBulkProcessor importerv2;

  @SpyBean
  private io.camunda.operate.zeebeimport.v1_2.processors.ElasticsearchBulkProcessor importerv1;

  public TestContainerUtil testContainerUtil = new TestContainerUtil();

  @After
  public void afterTest() {
    testContainerUtil.stopAll();
  }

  @Test
  public void shouldImportFromSeveralZeebeVersions() throws PersistenceException {
    //when
    startImportAndWaitTillItFinishes();
    //then
    sleepFor(5000L);
    assertOperateData();
    //make sure that both importers were called
    verify(importerv1, atLeastOnce()).performImport(any(ImportBatch.class));
    verify(importerv2, atLeastOnce()).performImport(any(ImportBatch.class));
  }

  private void startImportAndWaitTillItFinishes() {

    zeebeImporter.scheduleReaders();

    int countImported = 0;
    sleepFor(20000L);
    while (countImportListener.getImportedCount() > countImported) {
      countImported += countImportListener.getImportedCount();
      sleepFor(10000L);
    }

    logger.info("All import jobs are scheduled");
  }

  private void assertOperateData() {
    try {
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

}
