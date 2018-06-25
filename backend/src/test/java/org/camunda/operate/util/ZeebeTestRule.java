package org.camunda.operate.util;

import java.util.UUID;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebe.ZeebeConnector;
import org.camunda.operate.zeebe.ZeebeSubscriptionManager;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.ZeebeClient;

public class ZeebeTestRule extends TestWatcher {

  private Logger logger = LoggerFactory.getLogger(ZeebeTestRule.class);

  @Autowired
  protected ZeebeClient zeebeClient;

  @Autowired
  protected ZeebeUtil zeebeUtil;

  @Autowired
  protected ZeebeSubscriptionManager zeebeSubscriptionManager;

  @Autowired
  protected OperateProperties operateProperties;

  @Autowired
  protected EntityStorage entityStorage;

  private String topicName;
  private String workerName;

  @Override
  protected void starting(Description description) {
    //create Zeebe topic for this test method
    topicName = UUID.randomUUID().toString();
    zeebeUtil.createTopic(topicName);

    //create subscription to the new topic
    operateProperties.getZeebe().getTopics().add(topicName);
    workerName = UUID.randomUUID().toString().substring(10);
    operateProperties.getZeebe().setWorker(workerName);
    try {
      //wait till topic is created
      Thread.sleep(1000L);
    } catch (InterruptedException e) {
      //
    }
    zeebeSubscriptionManager.createSubscriptions();
  }

  @Override
  protected void finished(Description description) {
    operateProperties.getZeebe().getTopics().remove(topicName);
    try {
      zeebeSubscriptionManager.getTopicSubscriptions().get(topicName).close();
      zeebeSubscriptionManager.removeTopicSubscription(topicName);
    } catch (Exception ex) {

    }
  }

  public String getTopicName() {
    return topicName;
  }

  public String getWorkerName() {
    return workerName;
  }

  public ZeebeClient getZeebeClient() {
    return zeebeClient;
  }

  public ZeebeUtil getZeebeUtil() {
    return zeebeUtil;
  }

  public ZeebeSubscriptionManager getZeebeSubscriptionManager() {
    return zeebeSubscriptionManager;
  }
}
