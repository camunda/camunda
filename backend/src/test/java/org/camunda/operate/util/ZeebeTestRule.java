package org.camunda.operate.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.camunda.operate.es.writer.EntityStorage;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebe.ZeebeSubscriptionManager;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.api.subscription.TopicSubscription;

public class ZeebeTestRule extends TestWatcher {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeTestRule.class);

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

  private JobWorker jobWorker;

  private List<TopicSubscription> topicSubscriptions = new ArrayList<>();

  private String topicName;
  private String workerName;

  @Override
  protected void starting(Description description) {
    //create Zeebe topic for this test method
    topicName = TestUtil.createRandomString(20);
    zeebeUtil.createTopic(topicName);

    //create subscription to the new topic
    operateProperties.getZeebe().getTopics().add(topicName);
    workerName = TestUtil.createRandomString(10);
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

    if (jobWorker != null && jobWorker.isOpen()) {
        jobWorker.close();
        jobWorker = null;
    }

    for (Iterator<TopicSubscription> iterator = topicSubscriptions.iterator(); iterator.hasNext(); ) {
      iterator.next().close();
      iterator.remove();
    }

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

  public JobWorker getJobWorker() {
    return jobWorker;
  }

  public void setJobWorker(JobWorker jobWorker) {
    this.jobWorker = jobWorker;
  }

  public List<TopicSubscription> getTopicSubscriptions() {
    return topicSubscriptions;
  }

  public void setTopicSubscriptions(List<TopicSubscription> topicSubscriptions) {
    this.topicSubscriptions = topicSubscriptions;
  }
}