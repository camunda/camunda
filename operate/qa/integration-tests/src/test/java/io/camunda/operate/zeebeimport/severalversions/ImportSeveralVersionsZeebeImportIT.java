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
import io.camunda.operate.util.OperateAbstractIT;
import io.camunda.operate.util.SearchTestRule;
import io.camunda.operate.util.TestImportListener;
import io.camunda.operate.util.TestUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.RecordsReaderHolder;
import io.camunda.operate.zeebeimport.ZeebeImporter;
import io.camunda.operate.zeebeimport.v8_6.processors.ImportBulkProcessor;
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

  @SpyBean private io.camunda.operate.zeebeimport.v8_5.processors.ImportBulkProcessor importerv1;

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
