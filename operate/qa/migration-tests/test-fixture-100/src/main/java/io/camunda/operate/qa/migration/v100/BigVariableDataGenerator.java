/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
package io.camunda.operate.qa.migration.v100;

import static io.camunda.operate.entities.FlowNodeState.ACTIVE;
import static io.camunda.operate.qa.util.VariablesUtil.VAR_SUFFIX;
import static io.camunda.operate.qa.util.VariablesUtil.createBigVarsWithSuffix;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.property.ImportProperties;
import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class BigVariableDataGenerator {

  public static final String PROCESS_BPMN_PROCESS_ID = "bigVariableProcess";
  protected static final String ACTIVITY_ID = "task";
  private static final Logger logger = LoggerFactory.getLogger(BigProcessDataGenerator.class);
  private ZeebeClient zeebeClient;

  @Autowired private RestHighLevelClient esClient;

  private void init(TestContext testContext) {
    zeebeClient =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(testContext.getExternalZeebeContactPoint())
            .usePlaintext()
            .build();
  }

  public void createData(TestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      logger.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

      deployProcess();
      startProcessInstance();

      waitUntilAllDataIsImported();

      logger.info(
          "Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
      testContext.addProcess(PROCESS_BPMN_PROCESS_ID);
    } finally {
      closeClients();
    }
  }

  private void deployProcess() {
    BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_BPMN_PROCESS_ID)
            .startEvent("start")
            .serviceTask(ACTIVITY_ID)
            .zeebeJobType(ACTIVITY_ID)
            .endEvent()
            .done();
    String processDefinitionKey =
        ZeebeTestUtil.deployProcess(zeebeClient, process, "bigvariableProcess.bpmn");
    logger.info("Deployed process {} with key {}", PROCESS_BPMN_PROCESS_ID, processDefinitionKey);
  }

  private void startProcessInstance() {
    final int size = ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD;
    String vars = createBigVarsWithSuffix(PROCESS_BPMN_PROCESS_ID, size, VAR_SUFFIX);
    ZeebeTestUtil.startProcessInstance(zeebeClient, PROCESS_BPMN_PROCESS_ID, vars);
    logger.info("Started process instance with id {} ", PROCESS_BPMN_PROCESS_ID);
  }

  private void waitUntilAllDataIsImported() throws IOException {
    logger.info("Wait till data is imported.");
    SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest
        .source()
        .query(
            joinWithAnd(
                termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
                termQuery(ListViewTemplate.ACTIVITY_ID, ACTIVITY_ID),
                termQuery(ACTIVITY_STATE, ACTIVE)));
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    int count = 0, maxWait = 101;
    while (searchResponse.getHits().getTotalHits().value < 1 && count < maxWait) {
      count++;
      ThreadUtil.sleepFor(2000L);
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private String getAliasFor(String index) {
    return String.format("operate-%s-*_alias", index);
  }

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
  }
}
