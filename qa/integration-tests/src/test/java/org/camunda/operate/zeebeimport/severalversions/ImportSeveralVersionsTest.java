/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport.severalversions;

import java.io.IOException;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.qa.util.ElasticsearchUtil;
import org.camunda.operate.schema.indices.WorkflowIndex;
import org.camunda.operate.schema.templates.IncidentTemplate;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.util.ElasticsearchTestRule;
import org.camunda.operate.util.OperateIntegrationTest;
import org.camunda.operate.zeebeimport.ImportBatch;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ContextConfiguration;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static org.camunda.operate.schema.templates.ListViewTemplate.WORKFLOW_INSTANCE_JOIN_RELATION;
import static org.camunda.operate.util.ThreadUtil.sleepFor;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@ContextConfiguration(initializers = ImportSeveralVersionsInitializer.class)
public class ImportSeveralVersionsTest extends OperateIntegrationTest {

  @Rule
  public ElasticsearchTestRule elasticsearchTestRule = new ElasticsearchTestRule(ImportSeveralVersionsInitializer.OPERATE_PREFIX);

  @Autowired
  private ZeebeImporter zeebeImporter;

  @Autowired
  private WorkflowIndex workflowIndex;

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
  private org.camunda.operate.zeebeimport.v25.processors.ElasticsearchBulkProcessor importerv1;

  @SpyBean
  private org.camunda.operate.zeebeimport.v26.processors.ElasticsearchBulkProcessor importerv2;

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
    sleepFor(15000L);
    assertOperateData();
    //make sure that both importers were called
    verify(importerv1, atLeastOnce()).performImport(any(ImportBatch.class));
    verify(importerv2, atLeastOnce()).performImport(any(ImportBatch.class));
  }

  private void startImportAndWaitTillItFinishes() {
    zeebeImporter.start();

    final Object importFinishedLock = zeebeImporter.getImportFinished();
    synchronized (importFinishedLock) {
      try {
        importFinishedLock.wait();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private void assertOperateData() {
    try {
      //assert workflow count
      int count = ElasticsearchUtil.getDocCount(esClient, workflowIndex.getAlias());
      assertThat(count).isEqualTo(1);

      //assert workflow instances count
      count = ElasticsearchUtil.getFieldCardinality(esClient, listViewTemplate.getAlias(), ListViewTemplate.WORKFLOW_INSTANCE_KEY);
      assertThat(count).isEqualTo(wiCount);

      //assert finished count
      final TermQueryBuilder isWorkflowInstanceQuery = termQuery(JOIN_RELATION, WORKFLOW_INSTANCE_JOIN_RELATION);
      count = ElasticsearchUtil.getFieldCardinalityWithRequest(esClient, listViewTemplate.getAlias(), ListViewTemplate.END_DATE, isWorkflowInstanceQuery);
      assertThat(count).isEqualTo(finishedCount);

      //assert incidents count
      count = ElasticsearchUtil.getFieldCardinality(esClient, incidentTemplate.getAlias(), IncidentTemplate.KEY);
      assertThat(count).isEqualTo(incidentCount);
    } catch (IOException ex) {
      fail("Unable to assert data.", ex);
    }
  }

}
