/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.qa.performance;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static java.lang.Math.abs;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.OperateProfileService;
import io.camunda.operate.StandaloneOperate;
import io.camunda.operate.archiver.Archiver;
import io.camunda.operate.archiver.ProcessInstancesArchiverJob;
import io.camunda.operate.exceptions.ArchiverException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.ElasticsearchUtil;
import io.camunda.operate.schema.indices.ProcessIndex;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebe.ZeebeESConstants;
import io.camunda.operate.zeebeimport.CountImportListener;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ImportPerformanceStaticDataTest {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ImportPerformanceStaticDataTest.class);

  private static final double PRECISION_RATE = 0.01;

  private ConfigurableApplicationContext applicationContext;
  private OperateProperties operateProperties;

  @Before
  public void setup() {
    LOGGER.info("Operate will be started");
    final SpringApplication application =
        new SpringApplicationBuilder(StandaloneOperate.class)
            .addCommandLineProperties(true)
            .profiles(OperateProfileService.AUTH_PROFILE)
            .application();
    application.setDefaultProperties(
        Map.of(
            "camunda.operate.importer.threadsCount",
            5,
            "camunda.operate.importer.queueSize",
            7,
            "camunda.operate.importer.readerThreadsCount",
            5,
            "spring.mvc.pathmatch.matching-strategy",
            "ANT_PATH_MATCHER"));
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
    LOGGER.info("Starting data import...");

    zeebeImporter.scheduleReaders();

    sleepFor(240_000L);

    waitImportFinish();

    LOGGER.info(
        "Data import completed in: "
            + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now())
            + " s");

    try {
      assertData();
    } catch (AssertionError as) {
      // wait more
      LOGGER.info("Assertion failed: " + as.getMessage() + " Wait more.");
      waitImportFinish();
      LOGGER.info(
          "Data import completed in: "
              + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now())
              + " s");
      assertData();
    }
  }

  private void waitImportFinish() {
    final CountImportListener countImportListener =
        applicationContext.getBean(CountImportListener.class);
    int countImported = 0;
    while (countImportListener.getImportedCount() > countImported) {
      countImported = countImportListener.getImportedCount();
      LOGGER.debug(countImported + " records imported");
      sleepFor(60_000L);
    }
  }

  @Test
  public void testBArchiver() throws ArchiverException {
    final Archiver archiver = applicationContext.getBean(Archiver.class);
    final PartitionHolder partitionHolder = applicationContext.getBean(PartitionHolder.class);
    final ProcessInstancesArchiverJob archiverJob =
        applicationContext.getBean(
            ProcessInstancesArchiverJob.class, archiver, partitionHolder.getPartitionIds());
    final int archivedCount = archiverJob.archiveNextBatch().join();
    assertThat(archivedCount).isEqualTo(1);
  }

  private void assertData() throws IOException {
    final RestHighLevelClient esClient =
        applicationContext.getBean("esClient", RestHighLevelClient.class);
    final RestHighLevelClient zeebeEsClient =
        applicationContext.getBean("zeebeEsClient", RestHighLevelClient.class);

    // assert process count
    int expectedCount =
        ElasticsearchUtil.getFieldCardinality(
            zeebeEsClient,
            getZeebeAliasName(ZeebeESConstants.PROCESS_INDEX_NAME),
            "value.bpmnProcessId");
    final ProcessIndex processIndex = applicationContext.getBean(ProcessIndex.class);
    int count = ElasticsearchUtil.getDocCount(esClient, processIndex.getAlias());
    assertThat(count).isEqualTo(expectedCount);

    // assert process instances count
    expectedCount =
        ElasticsearchUtil.getFieldCardinality(
            zeebeEsClient,
            getZeebeAliasName(ZeebeESConstants.PROCESS_INSTANCE_INDEX_NAME),
            "value.processInstanceKey");
    final ListViewTemplate listViewTemplate = applicationContext.getBean(ListViewTemplate.class);
    count =
        ElasticsearchUtil.getFieldCardinality(
            esClient, listViewTemplate.getAlias(), ListViewTemplate.PROCESS_INSTANCE_KEY);
    assertThat(((double) abs(count - expectedCount)) / expectedCount)
        .isLessThanOrEqualTo(PRECISION_RATE);
  }

  public String getZeebeAliasName(String name) {
    return String.format("%s-%s", operateProperties.getZeebeElasticsearch().getPrefix(), name);
  }
}
