package org.camunda.operate.util;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.zeebe.DataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.cmd.ClientException;

@Component("dataGenerator")
@ConditionalOnProperty(name= OperateProperties.PREFIX + ".zeebe.demoData", havingValue="true")
@DependsOn("zeebeClient")
public class ZeebeDemoDataGenerator implements DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(ZeebeDemoDataGenerator.class);

  @Autowired
  private ZeebeClient client;

  @Autowired
  private OperateProperties operateProperties;

  private Random random = new Random();

  private boolean manuallyCalled = false;

  private ScheduledExecutorService scheduler;

  List<Long> workflowInstanceKeys = new ArrayList<>();

  @PostConstruct
  private void createZeebeData() {
    this.createZeebeData(false);
  }

  public void createZeebeData(boolean manuallyCalled) {
    this.manuallyCalled = manuallyCalled;
//    try {
      //TODO
//      createTopic();
//    } catch (ClientCommandRejectedException ex) {
      //data already exists
//      logger.debug("Topic [{}] already exists.", operateProperties.getZeebe().getTopics().get(0));
//      if (!manuallyCalled) {
//        return;
//      }
//    }

    deployVersion1();
    startWorkflowInstances(1);

    deployVersion2();
    startWorkflowInstances(2);

    deployVersion3();
    startWorkflowInstances(3);

    progressWorkflowInstances();
  }

  private void progressWorkflowInstances() {
    List<JobWorker> jobWorkers = new ArrayList<>();

    jobWorkers.add(progressDemoProcessTaskA());
    jobWorkers.add(progressReviewLoanRequestTask());
    jobWorkers.add(progressCheckSchufaTask());
    jobWorkers.add(progressOrderProcessCheckPayment());

    jobWorkers.add(progressSimpleTask("taskB"));
    jobWorkers.add(progressSimpleTask("taskC"));
    jobWorkers.add(progressSimpleTask("taskD"));
    jobWorkers.add(progressSimpleTask("taskE"));
    jobWorkers.add(progressSimpleTask("taskF"));
    jobWorkers.add(progressSimpleTask("taskG"));
    jobWorkers.add(progressSimpleTask("taskH"));

    jobWorkers.add(progressSimpleTask("requestPayment"));
    jobWorkers.add(progressSimpleTask("shipArticles"));

    jobWorkers.add(progressOrderProcessCheckItems());

    jobWorkers.add(progressSimpleTask("requestWarehouse"));

    //    final TopicSubscription updateRetriesIncidentSubscription = updateRetries();

    //start more instances after 1 minute
    scheduler = Executors.newScheduledThreadPool(2);
    scheduler.schedule(() ->
      startWorkflowInstances(1)
    , 1, TimeUnit.MINUTES);

    scheduler.schedule(() -> {
      for (JobWorker jobWorker: jobWorkers) {
        jobWorker.close();
      }
      //      updateRetriesIncidentSubscription.close();
    }, 90, TimeUnit.SECONDS);

    //there is a bug in Zeebe, when cancel happens concurrently with job worker running -> we're canceling after the job workers are stopped
    scheduler.schedule(() ->
      cancelSomeInstances(),
      100, TimeUnit.SECONDS);

    if (manuallyCalled) {
      shutdownScheduler();
    }
  }

  @PreDestroy
  private void shutdownScheduler() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(2, TimeUnit.MINUTES)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
    }
  }

  private void cancelSomeInstances() {
    final Iterator<Long> iterator = workflowInstanceKeys.iterator();
    while (iterator.hasNext()) {
      long workflowInstanceKey = iterator.next();
      if (random.nextInt(20) == 1) {
        try {
          client.workflowClient().newCancelInstanceCommand(workflowInstanceKey).send().join();
        } catch (ClientException ex) {
          logger.error("Error occurred when cancelling workflow instance:", ex);
        }
      }
      iterator.remove();
    }
  }

  private JobWorker progressOrderProcessCheckPayment() {
    return client.jobClient()
      .newWorker()
      .jobType("checkPayment")
      .handler((jobClient, job) -> {
        final int scenario = random.nextInt(6);
        switch (scenario){
        case 0:
          //fail
          throw new RuntimeException("Payment system not available.");
        case 1:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"paid\":false}").send().join();
          break;
        case 2:
        case 3:
        case 4:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"paid\":true}").send().join();
          break;
        case 5:
          jobClient.newCompleteCommand(job.getKey()).send().join();    //incident in gateway for v.1
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private JobWorker progressOrderProcessCheckItems() {
    return client.jobClient().newWorker()
      .jobType("checkItems")
      .handler((jobClient, job) -> {
        final int scenario = random.nextInt(4);
        switch (scenario) {
        case 0:
        case 1:
        case 2:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"smthIsMissing\":false}").send().join();
          break;
        case 4:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"smthIsMissing\":true}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private JobWorker progressSimpleTask(String taskType) {
    return client.jobClient().newWorker()
      .jobType(taskType)
      .handler((jobClient, job) ->
      {
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //leave the task active
          break;
        case 1:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).send().join();
          break;
        case 2:
          //fail task -> create incident
          jobClient.newFailCommand(job.getKey()).retries(0).send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private JobWorker progressDemoProcessTaskA() {
    return client.jobClient().newWorker()
      .jobType("taskA")
      .handler((jobClient, job) -> {
        final int scenarioCount = random.nextInt(2);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).send().join();
          break;
        case 1:
          //leave the task A active
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private JobWorker progressReviewLoanRequestTask() {
    return client.jobClient().newWorker()
      .jobType("reviewLoanRequest")
      .handler((jobClient, job) -> {
        final int scenarioCount = random.nextInt(2);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).payload("{\"loanRequestOK\": " + random.nextBoolean() + "}").send().join();
          break;
        case 1:
          //leave the task A active
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private JobWorker progressCheckSchufaTask() {
    return client.jobClient().newWorker()
      .jobType("checkSchufa")
      .handler((jobClient, job) -> {
        final int scenarioCount = random.nextInt(2);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).payload("{\"schufaOK\": " + random.nextBoolean() + "}").send().join();
          break;
        case 1:
          //leave the task A active
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .open();
  }

  private void deployVersion1() {
    //deploy workflows v.1
    final String classpathResource = "demoProcess_v_1.bpmn";
    ZeebeUtil.deployWorkflow(client, classpathResource);

    ZeebeUtil.deployWorkflow(client, "orderProcess_v_1.bpmn");

    ZeebeUtil.deployWorkflow(client, "loanProcess_v_1.bpmn");

  }

  private void startWorkflowInstances(int version) {
    final int instancesCount = random.nextInt(50) + 50;
    for (int i = 0; i < instancesCount; i++) {
      String instanceId = ZeebeUtil.startWorkflowInstance(client, "demoProcess", "{\"a\": \"b\"}");
      workflowInstanceKeys.add(IdUtil.extractKey(instanceId));
      if (version < 2) {
        instanceId = ZeebeUtil.startWorkflowInstance(client, "loanProcess",
          "{\"requestId\": \"RDG123000001\",\n"
          + "  \"amount\": " + (random.nextInt(10000) + 20000) + ",\n"
          + "  \"applier\": {\n"
          + "    \"firstname\": \"Max\",\n"
          + "    \"lastname\": \"Muster\",\n"
          + "    \"age\": "+ (random.nextInt(30) + 18) +"\n"
          + "  },\n"
          + "  \"newClient\": false,\n"
          + "  \"previousRequestIds\": [\"RDG122000001\", \"RDG122000501\", \"RDG122000057\"],\n"
          + "  \"attachedDocs\": [\n"
          + "    {\n"
          + "      \"docType\": \"ID\",\n"
          + "      \"number\": 123456789\n"
          + "    },\n"
          + "    {\n"
          + "      \"docType\": \"APPLICATION_FORM\",\n"
          + "      \"number\": 321547\n"
          + "    }\n"
          + "  ],\n"
          + "  \"otherInfo\": null\n"
          + "}");
        workflowInstanceKeys.add(IdUtil.extractKey(instanceId));
      }
      if (version < 3) {
        instanceId = ZeebeUtil.startWorkflowInstance(client, "orderProcess", "{\"a\": \"b\"}");
        workflowInstanceKeys.add(IdUtil.extractKey(instanceId));
      }

    }
  }

  private void deployVersion2() {
    //deploy workflows v.1
    ZeebeUtil.deployWorkflow(client, "demoProcess_v_2.bpmn");

    ZeebeUtil.deployWorkflow(client, "orderProcess_v_2.bpmn");
  }

  private void deployVersion3() {
    //deploy workflows v.1
    ZeebeUtil.deployWorkflow(client, "demoProcess_v_3.bpmn");
  }

  public void setClient(ZeebeClient client) {
    this.client = client;
  }

  public void setOperateProperties(OperateProperties operateProperties) {
    this.operateProperties = operateProperties;
  }

}
