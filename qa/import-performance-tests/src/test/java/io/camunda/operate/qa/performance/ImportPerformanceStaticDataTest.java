/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.qa.performance;

import io.camunda.operate.webapp.security.OperateProfileService;
import io.camunda.operate.zeebeimport.CountImportListener;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import io.camunda.operate.Application;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebe.ZeebeESConstants;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import java.util.Map;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
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
    final SpringApplication application = new SpringApplicationBuilder(Application.class)
        .addCommandLineProperties(true)
        .profiles(OperateProfileService.AUTH_PROFILE).application();
    application.setDefaultProperties(Map.of("camunda.operate.importer.threadsCount", 5,
        "camunda.operate.importer.queueSize", 7,
        "camunda.operate.importer.readerThreadsCount", 5));
    applicationContext = application.run();
    operateProperties = applicationContext.getBean(OperateProperties.class);
  }

  @After
  public void stop() {
    applicationContext.stop();
  }

  @Test
  public void testAImport() throws IOException {
    final ZeebeImporter zeebeImporter = applicationContext.getBean(ZeebeImporter.class);

    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting data import...");

    zeebeImporter.scheduleReaders();

    sleepFor(240_000L);

    waitImportFinish();

    logger.info("Data import completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");

    try {
      assertData();
    } catch (AssertionError as) {
      //wait more
      logger.info("Assertion failed: " + as.getMessage() + " Wait more.");
      waitImportFinish();
      logger.info("Data import completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");
      assertData();
    }

  }

  private void waitImportFinish() {
    final CountImportListener countImportListener = applicationContext.getBean(CountImportListener.class);
    int countImported = 0;
    while (countImportListener.getImportedCount() > countImported) {
      countImported = countImportListener.getImportedCount();
      logger.debug(countImported + " records imported");
      sleepFor(60_000L);
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
    int expectedCount = ElasticsearchUtil.getFieldCardinality(zeebeEsClient, getZeebeAliasName(ZeebeESConstants.PROCESS_INDEX_NAME), "value.bpmnProcessId");
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
