package org.camunda.operate.util;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.cmd.ClientCommandRejectedException;

/**
 * @author Svetlana Dorokhova.
 */
@Component
@ConditionalOnProperty(name= OperateProperties.PREFIX + ".zeebe.demoData", havingValue="true")
@Profile("zeebe")
public class ZeebeDemoDataGenerator {

  private Logger logger = LoggerFactory.getLogger(ZeebeDemoDataGenerator.class);

  @Autowired
  private ZeebeClient client;

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ZeebeUtil zeebeUtil;

  private Random random = new Random();

  @PostConstruct
  private void createZeebeData() {
    try {
      createTopic();
    } catch (ClientCommandRejectedException ex) {
      //data already exists
      return;
    }
    deployVersion1();
    startWorkflowInstances(1);

    deployVersion2();
    startWorkflowInstances(2);

    deployVersion3();
    startWorkflowInstances(3);

    progressWorkflowInstances();
  }

  private void progressWorkflowInstances() {
    List<JobWorker> subscriptions = new ArrayList<>();
    subscriptions.add(progressDemoProcessTaskA());
    subscriptions.add(progressSimpleTask("taskB"));
    subscriptions.add(progressSimpleTask("taskC"));
    subscriptions.add(progressSimpleTask("taskD"));
    subscriptions.add(progressSimpleTask("taskE"));
    subscriptions.add(progressSimpleTask("taskF"));
    subscriptions.add(progressSimpleTask("taskG"));
    subscriptions.add(progressSimpleTask("taskH"));

    subscriptions.add(progressOrderProcessCheckPayment());

    subscriptions.add(progressSimpleTask("requestPayment"));
    subscriptions.add(progressSimpleTask("shipArticles"));

    subscriptions.add(progressOrderProcessCheckItems());

    subscriptions.add(progressSimpleTask("requestWarehouse"));

    //    final TopicSubscription updateRetriesIncidentSubscription = updateRetries();

    ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    scheduler.schedule(() -> {
      for (JobWorker subscription: subscriptions) {
        subscription.close();
      }
      //      updateRetriesIncidentSubscription.close();
      logger.info("Subscriptions for demo data generation was canceled");
    }, 2, TimeUnit.MINUTES);
  }


  private JobWorker progressOrderProcessCheckPayment() {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    return client.topicClient(topic).jobClient()
      .newWorker()
      .jobType("checkPayment")
      .handler((jobClient, job) -> {
        final int scenario = random.nextInt(10);
        if (scenario<5) {
          //fail
          throw new RuntimeException("Payment system not available.");
        } else if (scenario<9) {
          if (scenario<7) {
            jobClient.newCompleteCommand(job).payload("{\"paid\":false}").send().join();
          } else {
            jobClient.newCompleteCommand(job).payload("{\"paid\":true}").send().join();
          }
        } else {
          //leave the task active
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private JobWorker progressOrderProcessCheckItems() {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    return client.topicClient(topic).jobClient().newWorker()
      .jobType("checkItems")
      .handler((jobClient, job) -> {
        final int scenario = random.nextInt(2);
        switch (scenario) {
        case 0:
          jobClient.newCompleteCommand(job).payload("{\"smthIsMissing\":false}").send().join();
          break;
        case 1:
          jobClient.newCompleteCommand(job).payload("{\"smthIsMissing\":true}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private JobWorker progressSimpleTask(String taskType) {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    return client.topicClient(topic).jobClient().newWorker()
      .jobType(taskType)
      .handler((jobClient, job) ->
      {
        final int scenarioCount = random.nextInt(2);
        switch (scenarioCount) {
        case 0:
          //leave the task active
          break;
        case 1:
          //successfully complete task
          jobClient.newCompleteCommand(job).send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private JobWorker progressDemoProcessTaskA() {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    return client.topicClient(topic).jobClient().newWorker()
      .jobType("taskA")
      .handler((jobClient, job) -> {
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //incidents for outputMapping for taskA
          jobClient.newCompleteCommand(job).withoutPayload().send().join();
          break;
        case 1:
          //successfully complete task
          jobClient.newCompleteCommand(job).send().join();
          break;
        case 2:
          //leave the task A active
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  public void createTopic() {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    zeebeUtil.createTopic(topic);
  }


  private void deployVersion1() {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    //deploy workflows v.1
    final String classpathResource = "demoProcess_v_1.bpmn";
    zeebeUtil.deployWorkflowToTheTopic(topic, classpathResource);

    zeebeUtil.deployWorkflowToTheTopic(topic, "orderProcess_v_1.bpmn");

  }

  private void startWorkflowInstances(int version) {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    final int instancesCount = random.nextInt(50) + 50;
    for (int i = 0; i < instancesCount; i++) {
      zeebeUtil.startWorkflowInstance(topic, "demoProcess", "{\"a\": \"b\"}");

      if (version < 3) {
        zeebeUtil.startWorkflowInstance(topic, "orderProcess", "{\"a\": \"b\"}");
      }

    }
  }

  private void deployVersion2() {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    //deploy workflows v.1
    zeebeUtil.deployWorkflowToTheTopic(topic, "demoProcess_v_2.bpmn");

    zeebeUtil.deployWorkflowToTheTopic(topic, "orderProcess_v_2.bpmn");
  }

  private void deployVersion3() {
    final String topic = operateProperties.getZeebe().getTopics().get(0);
    //deploy workflows v.1
    zeebeUtil.deployWorkflowToTheTopic(topic, "demoProcess_v_3.bpmn");
  }


}
