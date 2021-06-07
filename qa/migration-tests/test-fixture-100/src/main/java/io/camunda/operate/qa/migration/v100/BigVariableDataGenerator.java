package io.camunda.operate.qa.migration.v100;

import static io.camunda.operate.entities.FlowNodeState.ACTIVE;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.operate.schema.templates.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.operate.schema.templates.ListViewTemplate.JOIN_RELATION;
import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.property.ImportProperties;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.operate.qa.util.migration.TestContext;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ThreadUtil;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
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

  private static final Logger logger = LoggerFactory.getLogger(BigProcessDataGenerator.class);
  public static final String PROCESS_BPMN_PROCESS_ID = "bigVariableProcess";
  protected static final String ACTIVITY_ID = "task";
  public static final String VAR_SUFFIX = "9999999999";
  private ZeebeClient zeebeClient;

  @Autowired
  private RestHighLevelClient esClient;

  private void init(TestContext testContext) {
    zeebeClient = ZeebeClient.newClientBuilder().gatewayAddress(testContext.getExternalZeebeContactPoint()).usePlaintext().build();
  }

  public void createData(TestContext testContext) throws Exception {
    init(testContext);
    try {
      final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
      logger.info("Starting generating data for process {}", PROCESS_BPMN_PROCESS_ID);

      deployProcess();
      startProcessInstance();

      waitUntilAllDataIsImported();

      logger.info("Data generation completed in: {} s",
          ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime
              .now()));
      testContext.addProcess(PROCESS_BPMN_PROCESS_ID);
    } finally {
      closeClients();
    }
  }

  private void deployProcess() {
    BpmnModelInstance process = Bpmn.createExecutableProcess(PROCESS_BPMN_PROCESS_ID)
        .startEvent("start")
        .serviceTask(ACTIVITY_ID).zeebeJobType(ACTIVITY_ID)
        .endEvent()
        .done();
    String processDefinitionKey = ZeebeTestUtil
        .deployProcess(zeebeClient, process, "bigvariableProcess.bpmn");
    logger.info("Deployed process {} with key {}", PROCESS_BPMN_PROCESS_ID, processDefinitionKey);
  }

  private void startProcessInstance() {
    final int size = ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD;
    String vars = createBigVarsWithSuffix(size, VAR_SUFFIX);
    ZeebeTestUtil
        .startProcessInstance(zeebeClient, PROCESS_BPMN_PROCESS_ID, vars);
    logger.info("Started process instance with id {} ", PROCESS_BPMN_PROCESS_ID);
  }

  private String createBigVarsWithSuffix(final int size, final String varSuffix) {
    StringBuffer vars = new StringBuffer("{");
    for (int i = 0; i < 3; i++) {
      if (vars.length() > 1) {
        vars.append(",\n");
      }
      vars.append("\"" + PROCESS_BPMN_PROCESS_ID + "_var")
          .append(i)
          .append("\": \"")
          .append(createBigVariable(size) + varSuffix)
          .append("\"");
    }
    vars.append("}");
    return vars.toString();
  }

  private void waitUntilAllDataIsImported() throws IOException {
    logger.info("Wait till data is imported.");
    SearchRequest searchRequest = new SearchRequest(getAliasFor(ListViewTemplate.INDEX_NAME));
    searchRequest.source().query(joinWithAnd(
        termQuery(JOIN_RELATION, ACTIVITIES_JOIN_RELATION),
        termQuery(ListViewTemplate.ACTIVITY_ID, ACTIVITY_ID),
        termQuery(ACTIVITY_STATE, ACTIVE)
        )
    );
    SearchResponse searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    int count = 0, maxWait = 101;
    while(searchResponse.getHits().getTotalHits().value < 1  && count < maxWait) {
      count++;
      ThreadUtil.sleepFor(2000L);
      searchResponse = esClient.search(searchRequest, RequestOptions.DEFAULT);
    }
    if (count == maxWait) {
      throw new RuntimeException("Waiting for loading process instances failed: Timeout");
    }
  }

  private String createBigVariable(int size) {
    Random random = new Random();
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < size; i++) {
      sb.append(random.nextInt(9));
    }
    return sb.toString();
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
