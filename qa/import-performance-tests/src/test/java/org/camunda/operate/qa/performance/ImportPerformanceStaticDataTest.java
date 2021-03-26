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
import org.camunda.operate.archiver.ProcessInstancesArchiverJob;
import org.camunda.operate.exceptions.ArchiverException;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.qa.util.ElasticsearchUtil;
import org.camunda.operate.schema.indices.ProcessIndex;
import org.camunda.operate.schema.templates.ListViewTemplate;
import org.camunda.operate.webapp.security.OperateURIs;
import org.camunda.operate.zeebe.PartitionHolder;
import org.camunda.operate.zeebe.ZeebeESConstants;
import org.camunda.operate.zeebeimport.ZeebeImporter;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import static java.lang.Math.abs;
import static org.assertj.core.api.Assertions.assertThat;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ImportPerformanceStaticDataTest {

  private static final Logger logger = LoggerFactory.getLogger(ImportPerformanceStaticDataTest.class);

  private static final double PRECISION_RATE = 0.01;

  private ConfigurableApplicationContext applicationContext;
  private OperateProperties operateProperties;

  @Before
  public void setup() {
    logger.info("Operate will be started");
    applicationContext = new SpringApplicationBuilder(Application.class)
      .addCommandLineProperties(true)
      .profiles(OperateURIs.AUTH_PROFILE)
      .run();
    operateProperties = applicationContext.getBean(OperateProperties.class);
  }

  @After
  public void stop() {
    applicationContext.stop();
  }

  @Test
  public void testAImport() throws InterruptedException, IOException {
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

  @Test
  public void testBArchiver() throws ArchiverException {
    final PartitionHolder partitionHolder = applicationContext.getBean(PartitionHolder.class);
    ProcessInstancesArchiverJob archiverJob = applicationContext.getBean(ProcessInstancesArchiverJob.class, partitionHolder.getPartitionIds());
    final int archivedCount = archiverJob.archiveNextBatch();
    assertThat(archivedCount).isEqualTo(1);
  }

  private void assertData() throws IOException {
    final RestHighLevelClient esClient = applicationContext.getBean("esClient", RestHighLevelClient.class);
    final RestHighLevelClient zeebeEsClient = applicationContext.getBean("zeebeEsClient", RestHighLevelClient.class);

    //assert process count
    int expectedCount = ElasticsearchUtil.getFieldCardinality(zeebeEsClient, getZeebeAliasName(ZeebeESConstants.DEPLOYMENT_INDEX_NAME), "value.deployedProcesses.bpmnProcessId");
    final ProcessIndex processIndex = applicationContext.getBean(ProcessIndex.class);
    int count = ElasticsearchUtil.getDocCount(esClient, processIndex.getAlias());
    assertThat(count).isEqualTo(expectedCount);

    //assert process instances count
    expectedCount = ElasticsearchUtil.getFieldCardinality(zeebeEsClient, getZeebeAliasName(ZeebeESConstants.PROCESS_INSTANCE_INDEX_NAME), "value.processInstanceKey");
    final ListViewTemplate listViewTemplate = applicationContext.getBean(ListViewTemplate.class);
    count = ElasticsearchUtil.getFieldCardinality(esClient, listViewTemplate.getAlias(), ListViewTemplate.PROCESS_INSTANCE_KEY);
    assertThat(((double) abs(count - expectedCount)) / expectedCount).isLessThanOrEqualTo(PRECISION_RATE);
  }

  public String getZeebeAliasName(String name) {
    return String.format("%s-%s", operateProperties.getZeebeElasticsearch().getPrefix(), name);
  }

}
