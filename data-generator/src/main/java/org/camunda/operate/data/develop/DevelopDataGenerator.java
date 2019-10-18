/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.develop;

import io.zeebe.client.api.worker.JobWorker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.camunda.operate.data.usertest.UserTestDataGenerator;
import org.camunda.operate.util.ZeebeTestUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;

@Component("dataGenerator")
@Profile("dev-data")
public class DevelopDataGenerator extends UserTestDataGenerator {

  List<Long> workflowInstanceKeys = new ArrayList<>();

  @Override
  public void createSpecialDataV1() {
    int orderId = random.nextInt(10);
    long instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "interruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "interruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task2", null);

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    failTask(instanceKey, "task1", "error");

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startWorkflowInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchWorkflowInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task1", null);

  }

  @Override
  protected void progressWorkflowInstances() {

    super.progressWorkflowInstances();

    //demo process
    jobWorkers.add(progressTaskA());
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

    //eventBasedGatewayProcess
    jobWorkers.add(progressSimpleTask("messageTask"));
    jobWorkers.add(progressSimpleTask("afterMessageTask"));
    jobWorkers.add(progressSimpleTask("messageTaskInterrupted"));
    jobWorkers.add(progressSimpleTask("timerTask"));
    jobWorkers.add(progressSimpleTask("afterTimerTask"));
    jobWorkers.add(progressSimpleTask("timerTaskInterrupted"));
    jobWorkers.add(progressSimpleTask("lastTask"));

    //interruptingBoundaryEvent and nonInterruptingBoundaryEvent
    jobWorkers.add(progressSimpleTask("task1"));
    jobWorkers.add(progressSimpleTask("task2"));

    sendMessages("clientMessage", "{\"messageVar\": \"someValue\"}", 20);
    sendMessages("interruptMessageTask", "{\"messageVar2\": \"someValue2\"}", 20);
    sendMessages("dataReceived", "{\"messageVar3\": \"someValue3\"}", 20);

  }

  private void sendMessages(String messageName, String payload, int count, String correlationKey) {
    for (int i = 0; i<count; i++) {
      client.newPublishMessageCommand()
        .messageName(messageName)
        .correlationKey(correlationKey)
        .variables(payload)
        .timeToLive(Duration.ofSeconds(30))
        .messageId(UUID.randomUUID().toString())
        .send().join();
    }
  }
  private void sendMessages(String messageName, String payload, int count) {
    sendMessages(messageName, payload, count, String.valueOf(random.nextInt(7)));
  }

  @Override
  protected JobWorker progressOrderProcessCheckPayment() {
    return client
      .newWorker()
      .jobType("checkPayment")
      .handler((jobClient, job) -> {
        final int scenario = random.nextInt(6);
        switch (scenario){
        case 0:
          //fail
          throw new RuntimeException("Payment system not available.");
        case 1:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"paid\":false}").send().join();
          break;
        case 2:
        case 3:
        case 4:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"paid\":true}").send().join();
          break;
        case 5:
          jobClient.newCompleteCommand(job.getKey()).send().join();    //incident in gateway for v.1
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressSimpleTask(String taskType) {
    return client.newWorker()
      .jobType(taskType)
      .handler((jobClient, job) ->
      {
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //timeout
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
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressTaskA() {
    return client.newWorker()
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
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  @Override
  protected void deployVersion1() {
    super.deployVersion1();

    //deploy workflows v.1
    ZeebeTestUtil.deployWorkflow(client, "develop/complexProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/eventBasedGatewayProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/subProcess.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/interruptingBoundaryEvent_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/nonInterruptingBoundaryEvent_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/timerProcess_v_1.bpmn");

  }

  @Override
  protected void startWorkflowInstances(int version) {
    super.startWorkflowInstances(version);
    final int instancesCount = random.nextInt(30) + 30;
    for (int i = 0; i < instancesCount; i++) {

      if (version == 1) {
        //eventBasedGatewayProcess v.1
        sendMessages("newClientMessage", "{\"clientId\": \"" + random.nextInt(10) + "\"\n}", 1);
      }

      if (version == 2) {
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "interruptingBoundaryEvent", null));
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "nonInterruptingBoundaryEvent", null));
      }
      if (version < 2) {
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "prWithSubprocess", null));
      }

      if (version < 3) {
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "complexProcess", "{\"clientId\": \"" + random.nextInt(10) + "\"}"));
      }

      if (version == 3) {
        workflowInstanceKeys.add(ZeebeTestUtil.startWorkflowInstance(client, "complexProcess", "{\"goUp\": " + random.nextInt(10) + "}"));
      }

    }
  }

  @Override
  protected void deployVersion2() {
    super.deployVersion2();
//    deploy workflows v.2
    ZeebeTestUtil.deployWorkflow(client, "develop/complexProcess_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/eventBasedGatewayProcess_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/interruptingBoundaryEvent_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "develop/nonInterruptingBoundaryEvent_v_2.bpmn");

  }

  @Override
  protected void deployVersion3() {
    super.deployVersion3();
    //deploy workflows v.3
    ZeebeTestUtil.deployWorkflow(client, "develop/complexProcess_v_3.bpmn");

  }

  public void setClient(ZeebeClient client) {
    this.client = client;
  }

}
