/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.qa.performance;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.camunda.operate.Application;
import org.camunda.operate.es.schema.indices.WorkflowIndex;
import org.camunda.operate.es.schema.templates.ListViewTemplate;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.qa.util.ElasticsearchUtil;
import org.camunda.operate.zeebeimport.ZeebeESConstants;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static java.lang.Math.abs;
import static org.assertj.core.api.Assertions.assertThat;

public class ImportPerformanceStaticDataTest {

  private static final Logger logger = LoggerFactory.getLogger(ImportPerformanceStaticDataTest.class);

  private static final double PRECISION_RATE = 0.01;

  private ConfigurableApplicationContext applicationContext;
  private OperateProperties operateProperties;

  @Before
  public void setup() {
    applicationContext = new SpringApplicationBuilder(Application.class)
      .addCommandLineProperties(true)
      .run();
    operateProperties = applicationContext.getBean(OperateProperties.class);
  }

  @Test
  public void test() throws InterruptedException, IOException {
    final ZeebeImporter zeebeImporter = applicationContext.getBean(ZeebeImporter.class);

    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting data import...");

    zeebeImporter.start();

    final Object importFinishedLock = zeebeImporter.getImportFinished();
    synchronized (importFinishedLock) {
      importFinishedLock.wait();
      logger.info("Data import completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");
      assertData();
    }

  }

  private void assertData() throws IOException {
    final RestHighLevelClient esClient = applicationContext.getBean("esClient", RestHighLevelClient.class);
    final RestHighLevelClient zeebeEsClient = applicationContext.getBean("zeebeEsClient", RestHighLevelClient.class);

    //assert workflow count
    int expectedCount = ElasticsearchUtil.getFieldCardinality(zeebeEsClient, getZeebeAliasName(ZeebeESConstants.DEPLOYMENT_INDEX_NAME), "value.deployedWorkflows.bpmnProcessId");
    final WorkflowIndex workflowIndex = applicationContext.getBean(WorkflowIndex.class);
    int count = ElasticsearchUtil.getDocCount(esClient, workflowIndex.getAlias());
    assertThat(count).isEqualTo(expectedCount);

    //assert workflow instances count
    expectedCount = ElasticsearchUtil.getFieldCardinality(zeebeEsClient, getZeebeAliasName(ZeebeESConstants.WORKFLOW_INSTANCE_INDEX_NAME), "value.workflowInstanceKey");
    final ListViewTemplate listViewTemplate = applicationContext.getBean(ListViewTemplate.class);
    count = ElasticsearchUtil.getFieldCardinality(esClient, listViewTemplate.getAlias(), ListViewTemplate.WORKFLOW_INSTANCE_ID);
    assertThat(((double) abs(count - expectedCount)) / expectedCount).isLessThanOrEqualTo(PRECISION_RATE);
  }

  public String getZeebeAliasName(String name) {
    return String.format("%s-%s", operateProperties.getZeebeElasticsearch().getPrefix(), name);
  }

}
