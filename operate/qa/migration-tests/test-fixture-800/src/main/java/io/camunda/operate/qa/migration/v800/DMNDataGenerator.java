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
package io.camunda.operate.qa.migration.v800;

import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.qa.util.TestContext;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.elasticsearch.action.admin.indices.refresh.RefreshRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** It is considered that Zeebe and Elasticsearch are running. */
@Component
public class DMNDataGenerator {

  public static final String PROCESS_BPMN_PROCESS_ID = "basicDecision";
  public static final int PROCESS_INSTANCE_COUNT = 13;
  public static final int DECISION_COUNT = 2;
  private static final Logger logger = LoggerFactory.getLogger(DMNDataGenerator.class);

  /**
   * ZeebeClient must not be reused between different test fixtures, as this may be different
   * versions of client in the future.
   */
  private ZeebeClient zeebeClient;

  @Autowired private RestHighLevelClient esClient;

  private Random random = new Random();

  private List<Long> processInstanceKeys = new ArrayList<>();

  private StatefulRestTemplate operateRestClient;

  private void init(TestContext testContext) {
    zeebeClient =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(testContext.getExternalZeebeContactPoint())
            .usePlaintext()
            .build();
    operateRestClient =
        new StatefulRestTemplate(
            testContext.getExternalOperateHost(),
            testContext.getExternalOperatePort(),
            testContext.getExternalOperateContextPath());
    operateRestClient.loginWhenNeeded();
  }

  public void createData(TestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      logger.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

      deployProcessAndDecision();

      processInstanceKeys = startProcessInstances(PROCESS_INSTANCE_COUNT);

      waitUntilAllDataIsImported();

      try {
        esClient.indices().refresh(new RefreshRequest("operate-*"), RequestOptions.DEFAULT);
      } catch (IOException e) {
        logger.error("Error in refreshing indices", e);
      }
      logger.info(
          "Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()));
      testContext.addProcess(PROCESS_BPMN_PROCESS_ID);
    } finally {
      closeClients();
    }
  }

  private void closeClients() {
    if (zeebeClient != null) {
      zeebeClient.close();
      zeebeClient = null;
    }
  }

  private void waitUntilAllDataIsImported() throws IOException {
    logger.info("Wait till data is imported.");
    // count process instances
    SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest.source().query(termQuery(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION));
    long loadedProcessInstances = 0;
    int count = 0, maxWait = 101;
    while (PROCESS_INSTANCE_COUNT > loadedProcessInstances && count < maxWait) {
      count++;
      loadedProcessInstances = countEntitiesFor(searchRequest);
      ThreadUtil.sleepFor(1000L);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
    // count decision instances
    searchRequest = new SearchRequest(getAliasFor(DecisionInstanceTemplate.INDEX_NAME));
    loadedProcessInstances = 0;
    count = 0;
    maxWait = 101;
    while (PROCESS_INSTANCE_COUNT * 2 > loadedProcessInstances && count < maxWait) {
      count++;
      loadedProcessInstances = countEntitiesFor(searchRequest);
      ThreadUtil.sleepFor(1000L);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading decision instances failed: Timeout");
    }
  }

  private List<Long> startProcessInstances(int numberOfProcessInstances) {
    for (int i = 0; i < numberOfProcessInstances; i++) {
      String bpmnProcessId = PROCESS_BPMN_PROCESS_ID;
      long processInstanceKey =
          ZeebeTestUtil.startProcessInstance(
              zeebeClient, bpmnProcessId, "{\"amount\": 100, \"invoiceCategory\": \"Misc\"}");
      logger.debug("Started processInstance {} for process {}", processInstanceKey, bpmnProcessId);
      processInstanceKeys.add(processInstanceKey);
    }
    logger.info("{} processInstances started", processInstanceKeys.size());
    return processInstanceKeys;
  }

  private void deployProcessAndDecision() {
    final String demoDecisionId2 = "invoiceAssignApprover";

    String bpmnProcessId = PROCESS_BPMN_PROCESS_ID;
    final String elementId = "task";
    final BpmnModelInstance instance =
        Bpmn.createExecutableProcess(PROCESS_BPMN_PROCESS_ID)
            .startEvent()
            .businessRuleTask(
                elementId,
                task ->
                    task.zeebeCalledDecisionId(demoDecisionId2)
                        .zeebeResultVariable("approverGroups"))
            .done();
    String processDefinitionKey =
        ZeebeTestUtil.deployProcess(zeebeClient, instance, bpmnProcessId + ".bpmn");
    logger.info("Deployed process {} with key {}", bpmnProcessId, processDefinitionKey);
    ZeebeTestUtil.deployDecision(zeebeClient, "invoiceBusinessDecisions_v_1.dmn");
    logger.info("Deployed decision {}", demoDecisionId2);
  }

  private long countEntitiesFor(SearchRequest searchRequest) throws IOException {
    searchRequest.source().size(1000);
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    return searchResponse.getHits().getTotalHits().value;
  }

  private String getAliasFor(String index) {
    return String.format("operate-%s-*_alias", index);
  }
}
