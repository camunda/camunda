package org.camunda.operate.util;

import java.util.UUID;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebe.ZeebeConnector;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import io.zeebe.client.ZeebeClient;

/**
 *
 * @author Svetlana Dorokhova.
 */
@ActiveProfiles({"zeebe", "elasticsearch"})   //TODO this profiles are not working any more (and probably not needed)
public abstract class ZeebeIntegrationTest extends OperateIntegrationTest {

  private Logger logger = LoggerFactory.getLogger(ZeebeIntegrationTest.class);

  public static final long QUEUE_POLL_TIMEOUT = 1000L;

  @Rule
  public TestName name = new TestName();

  @Autowired
  protected ZeebeClient zeebeClient;

  @Autowired
  protected ZeebeUtil zeebeUtil;

  @Autowired
  protected ZeebeConnector zeebeConnector;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  protected EntityStorage entityStorage;

  private String newTopicName;

  public void starting() {
    //create Zeebe topic for this test method
    newTopicName = UUID.randomUUID().toString();
    zeebeUtil.createTopic(newTopicName);

    //create subscription to the new topic
    operateProperties.getZeebe().getTopics().add(newTopicName);
    try {
      //wait till topic is created
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      //
    }
    zeebeConnector.checkAndCreateTopicSubscriptions(newTopicName);
  }

  public void finished() {
    operateProperties.getZeebe().getTopics().remove(newTopicName);
    try {
      zeebeConnector.getTopicSubscriptions().get(newTopicName).close();
      zeebeConnector.removeTopicSubscription(newTopicName);
    } catch (Exception ex) {

    }
  }

  protected String getNewTopicName() {
    return newTopicName;
  }

}
