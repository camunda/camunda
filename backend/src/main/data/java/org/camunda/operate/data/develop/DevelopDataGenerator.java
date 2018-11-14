package org.camunda.operate.data.develop;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.camunda.operate.data.usertest.UserTestDataGenerator;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.client.api.subscription.JobWorker;

@Component("dataGenerator")
@Profile("dev-data")
public class DevelopDataGenerator extends UserTestDataGenerator {

  List<Long> workflowInstanceKeys = new ArrayList<>();

  @Override
  protected void progressWorkflowInstances() {

    super.progressWorkflowInstances();

    //demo process
    jobWorkers.add(progressDemoProcessTaskA());
    jobWorkers.add(progressSimpleTask("taskB"));
    jobWorkers.add(progressSimpleTask("taskC"));
    jobWorkers.add(progressSimpleTask("taskD"));
    jobWorkers.add(progressSimpleTask("taskE"));
    jobWorkers.add(progressSimpleTask("taskF"));
    jobWorkers.add(progressSimpleTask("taskG"));
    jobWorkers.add(progressSimpleTask("taskH"));

    //complex process
    jobWorkers.add(progressSimpleTask("upperTask"));
    jobWorkers.add(progressSimpleTask("lowerTask"));
    jobWorkers.add(progressSimpleTask("subprocessTask"));

    sendMessages("clientMessage", "{\"messageVar\": \"someValue\"}", 50);

    //    final TopicSubscription updateRetriesIncidentSubscription = updateRetries();

  }

  private void sendMessages(String messageName, String payload, int count) {
    for (int i = 0; i<count; i++) {
      client.workflowClient().newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(String.valueOf(random.nextInt(9)))
        .payload(payload)
        .timeToLive(Duration.ofSeconds(30))
        .messageId(UUID.randomUUID().toString())
        .send().join();
    }
  }

  @Override
  protected JobWorker progressOrderProcessCheckPayment() {
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

  private JobWorker progressSimpleTask(String taskType) {
    return client.jobClient().newWorker()
      .jobType(taskType)
      .handler((jobClient, job) ->
      {
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
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

  @Override
  protected void deployVersion1() {
    super.deployVersion1();

    //deploy workflows v.1
    ZeebeTestUtil.deployWorkflow(client, "develop/demoProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/complexProcess_v_1.bpmn");

  }

  @Override
  protected void startWorkflowInstances(int version) {
    super.startWorkflowInstances(version);
    final int instancesCount = random.nextInt(50) + 50;
    for (int i = 0; i < instancesCount; i++) {
      String instanceId = ZeebeTestUtil.startWorkflowInstance(client, "demoProcess", "{\"a\": \"b\"}");
      workflowInstanceKeys.add(IdUtil.extractKey(instanceId));
      if (version < 2) {
        instanceId = ZeebeTestUtil.startWorkflowInstance(client, "complexProcess",
          "{\"clientId\": \"" + random.nextInt(10) + "\"\n}");
        workflowInstanceKeys.add(IdUtil.extractKey(instanceId));
      }
    }
  }

  @Override
  protected void deployVersion2() {
    super.deployVersion2();
    //deploy workflows v.2
    ZeebeTestUtil.deployWorkflow(client, "develop/demoProcess_v_2.bpmn");

  }

  @Override
  protected void deployVersion3() {
    super.deployVersion3();
    //deploy workflows v.3
    ZeebeTestUtil.deployWorkflow(client, "develop/demoProcess_v_3.bpmn");
  }

  public void setClient(ZeebeClient client) {
    this.client = client;
  }

}
