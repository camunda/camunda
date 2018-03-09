package org.camunda.optimize.websocket;

import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.optimize.test.it.rule.ElasticSearchIntegrationTestRule;
import org.camunda.optimize.test.it.rule.EmbeddedOptimizeRule;
import org.camunda.optimize.test.it.rule.EngineIntegrationRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

import javax.websocket.ContainerProvider;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.net.URI;
import java.time.temporal.ChronoUnit;

/**
 * @author Askar Akhmerov
 */
public class StatusWebSocketIT {

  private static final String PROCESS_ID = "aProcessId";
  public EngineIntegrationRule engineRule = new EngineIntegrationRule();
  public ElasticSearchIntegrationTestRule elasticSearchRule = new ElasticSearchIntegrationTestRule();
  public EmbeddedOptimizeRule embeddedOptimizeRule = new EmbeddedOptimizeRule();

  @Rule
  public RuleChain chain = RuleChain
    .outerRule(elasticSearchRule).around(engineRule).around(embeddedOptimizeRule);

  @Test
  public void getConnectionStatusOk() throws Exception {
    //given
    BpmnModelInstance processModel = Bpmn.createExecutableProcess(PROCESS_ID)
        .startEvent()
        .endEvent()
      .done();
    engineRule.deployAndStartProcess(processModel);
    embeddedOptimizeRule.getConfigurationService().setStatusIntervalUnit(ChronoUnit.MILLIS.toString());
    embeddedOptimizeRule.scheduleAllJobsAndImportEngineEntities();
    elasticSearchRule.refreshOptimizeIndexInElasticsearch();

    String dest = "ws://localhost:8090/ws/status";
    StatusClientSocket socket = new StatusClientSocket();
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();

    //when
    Session session = container.connectToServer(socket, new URI(dest));

    //then - assertion will happen in client
    while(session.isOpen()) {
      Thread.sleep(1000l);
    }
  }

}