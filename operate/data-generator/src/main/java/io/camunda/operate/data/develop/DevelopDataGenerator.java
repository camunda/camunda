/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.data.develop;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.operate.data.usertest.UserTestDataGenerator;
import io.camunda.webapps.schema.entities.operation.OperationType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("dataGenerator")
@Profile("dev-data")
public class DevelopDataGenerator extends UserTestDataGenerator {

  private static final String TENANT_A = "tenantA";

  private final List<Long> processInstanceKeys = new ArrayList<>();

  @Override
  public void createSpecialDataV1() {
    int orderId = ThreadLocalRandom.current().current().nextInt(10);
    long instanceKey =
        startProcessInstance(
            true,
            getTenant(TENANT_A),
            "interruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = ThreadLocalRandom.current().current().nextInt(10);
    instanceKey =
        startProcessInstance(
            true,
            getTenant(TENANT_A),
            "interruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task2", null);

    orderId = ThreadLocalRandom.current().current().nextInt(10);
    instanceKey =
        startProcessInstance(
            true,
            getTenant(TENANT_A),
            "nonInterruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = ThreadLocalRandom.current().current().nextInt(10);
    instanceKey =
        startProcessInstance(
            true,
            getTenant(TENANT_A),
            "nonInterruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    failTask(instanceKey, "task1", "error");

    orderId = ThreadLocalRandom.current().current().nextInt(10);
    instanceKey =
        startProcessInstance(
            true,
            getTenant(TENANT_A),
            "nonInterruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task1", null);

    final long rootCauseDecisionInstance =
        startProcessInstance(true, getTenant(TENANT_A), "Process_rootCauseDecision", null);
    doNotTouchProcessInstanceKeys.add(rootCauseDecisionInstance);

    final long messageSubscriptionInstance =
        startProcessInstance(true, getTenant(TENANT_A), "Process_MessageSubscriptions", null);
    doNotTouchProcessInstanceKeys.add(messageSubscriptionInstance);
  }

  @Override
  protected void progressProcessInstances() {

    super.progressProcessInstances();

    // complex process
    jobWorkers.add(progressSimpleTask("upperTask"));
    jobWorkers.add(progressSimpleTask("lowerTask", 1));
    jobWorkers.add(progressSimpleTask("subprocessTask"));

    // eventBasedGatewayProcess
    jobWorkers.add(progressSimpleTask("messageTask"));
    jobWorkers.add(progressSimpleTask("afterMessageTask"));
    jobWorkers.add(progressSimpleTask("messageTaskInterrupted"));
    jobWorkers.add(progressSimpleTask("timerTask"));
    jobWorkers.add(progressSimpleTask("afterTimerTask"));
    jobWorkers.add(progressSimpleTask("timerTaskInterrupted"));
    jobWorkers.add(progressSimpleTask("lastTask"));

    // interruptingBoundaryEvent and nonInterruptingBoundaryEvent
    jobWorkers.add(progressSimpleTask("task1"));
    jobWorkers.add(progressSimpleTask("task2"));

    // call activity process
    jobWorkers.add(progressSimpleTask("called-task"));

    // eventSubprocess
    jobWorkers.add(progressSimpleTask("parentProcessTask"));
    jobWorkers.add(progressSimpleTask("subprocessTask"));
    jobWorkers.add(progressSimpleTask("subSubprocessTask"));
    jobWorkers.add(progressSimpleTask("eventSupbprocessTask"));

    // big process
    jobWorkers.add(progressBigProcessTaskA());
    jobWorkers.add(progressBigProcessTaskB());

    // error process
    jobWorkers.add(progressErrorTask());

    // link event process
    jobWorkers.add(progressRetryTask());
    // escalation events process
    jobWorkers.add(progressPlaceOrderTask());

    // message subscriptions process
    jobWorkers.add(progressSimpleTask("processMessageA"));
    jobWorkers.add(progressSimpleTask("processMessageB"));

    // call activity chain (level-1, level-2) - level-3 intentionally not deployed
    jobWorkers.add(progressSimpleTask("level1PostProcessing"));
    jobWorkers.add(progressSimpleTask("level2PostProcessing"));

    // incidents / root cause process
    jobWorkers.add(progressSimpleTask("rootCauseIOMapping"));
    jobWorkers.add(progressSimpleTask("rootCauseBadPath"));
    jobWorkers.add(progressSimpleTask("rootCauseGoodPath"));

    sendMessages("clientMessage", "{\"messageVar\": \"someValue\"}", 20);
    sendMessages("interruptMessageTask", "{\"messageVar2\": \"someValue2\"}", 20);
    sendMessages("dataReceived", "{\"messageVar3\": \"someValue3\"}", 20);
    // Send messages for message subscriptions process
    sendMessages("MessageA", "{\"messageAVar\": \"valueA\"}", 5, "123");
    sendMessages("MessageB", "{\"messageBVar\": \"valueB\"}", 5, "456");
  }

  @Override
  protected void createOperations() {
    final int operationsCount = ThreadLocalRandom.current().nextInt(20) + 90;
    for (int i = 0; i < operationsCount; i++) {
      final int no = ThreadLocalRandom.current().nextInt(operationsCount);
      final Long processInstanceKey = processInstanceKeys.get(no);
      final OperationType type = getType(i);
      switch (type) {
        case CANCEL_PROCESS_INSTANCE -> cancelProcessInstance(false, processInstanceKey);
        case RESOLVE_INCIDENT -> resolveIncidentForProcessInstance(processInstanceKey);
        default ->
            throw new IllegalArgumentException(
                "Unsupported operation type for dev data generation: " + type);
      }
    }
  }

  @Override
  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  protected JobWorker progressOrderProcessCheckPayment() {
    return client
        .newWorker()
        .jobType("checkPayment")
        .handler(
            (jobClient, job) -> {
              final int scenario = ThreadLocalRandom.current().nextInt(6);
              switch (scenario) {
                case 0:
                  // fail
                  throw new RuntimeException("Payment system not available.");
                case 1:
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .variables("{\"paid\":false}")
                      .send()
                      .join();
                  break;
                case 2:
                case 3:
                case 4:
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .variables("{\"paid\":true}")
                      .send()
                      .join();
                  break;
                case 5:
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .send()
                      .join(); // incident in gateway for v.1
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

    // deploy processes v.1
    deployProcess(true, getTenant(TENANT_A), "develop/complexProcess_v_1.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/eventBasedGatewayProcess_v_1.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/subProcess.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/interruptingBoundaryEvent_v_1.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/nonInterruptingBoundaryEvent_v_1.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/timerProcess_v_1.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/callActivityProcess.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/eventSubProcess_v_1.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/bigProcess.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/errorProcess.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/error-end-event.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/terminateEndEvent.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/undefined-task.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/dataStore.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/linkEvents.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/escalationEvents_v_1.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/signalEvent.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/collapsedSubProcess.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/compensationEvents.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/executionListeners.bpmn");

    deployDecision(getTenant(TENANT_A), "develop/dmn-with-decisions-chain.dmn");

    deployProcess(true, getTenant(TENANT_A), "develop/process-with-root-cause-decision.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/process-with-message-subscriptions.bpmn");

    // Deploy call activity chain for incident propagation testing
    // Note: level-3 is intentionally NOT deployed to create incident chain
    deployProcess(true, getTenant(TENANT_A), "develop/level-1.bpmn");
    deployProcess(true, getTenant(TENANT_A), "develop/level-2.bpmn");
    deployDecision(getTenant(TENANT_A), "develop/decisions-chain-a.dmn");

    deployProcess(true, getTenant(TENANT_A), "develop/incidents-process.bpmn");

    // reverted in Zeebe https://github.com/camunda/camunda/issues/13640
    // deployProcess(true, getTenant(TENANT_A),
    // "develop/inclusiveGateway.bpmn");
  }

  @Override
  protected void startProcessInstances(final int version) {
    super.startProcessInstances(version);
    if (version == 1) {
      createBigProcess(40, 1000);
    }
    final int instancesCount = ThreadLocalRandom.current().nextInt(15) + 15;
    for (int i = 0; i < instancesCount; i++) {

      if (version == 1) {
        // eventBasedGatewayProcess v.1
        sendMessages(
            "newClientMessage",
            "{\"clientId\": \"" + ThreadLocalRandom.current().nextInt(10) + "\"\n}",
            1);

        // call activity process
        // these instances will have incident on call activity
        processInstanceKeys.add(
            startProcessInstance(
                true,
                getTenant(TENANT_A),
                "call-activity-process",
                "{\"var\": " + ThreadLocalRandom.current().nextInt(10) + "}"));

        // eventSubprocess
        processInstanceKeys.add(
            startProcessInstance(
                true,
                getTenant(TENANT_A),
                "eventSubprocessProcess",
                "{\"clientId\": \"" + ThreadLocalRandom.current().nextInt(10) + "\"}"));

        // errorProcess
        processInstanceKeys.add(
            startProcessInstance(
                true, getTenant(TENANT_A), "errorProcess", "{\"errorCode\": \"boundary\"}"));
        processInstanceKeys.add(
            startProcessInstance(
                true, getTenant(TENANT_A), "errorProcess", "{\"errorCode\": \"subProcess\"}"));
        processInstanceKeys.add(
            startProcessInstance(
                true, getTenant(TENANT_A), "errorProcess", "{\"errorCode\": \"unknown\"}"));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "error-end-process", null));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "terminateEndEvent", null));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "collapsedSubProcess", null));

        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "dataStoreProcess", null));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "linkEventProcess", null));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "escalationEvents", null));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "undefined-task-process", null));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "compensationEvents", null));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "executionListeners", null));

        // Root cause decision process
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "Process_rootCauseDecision", null));

        // Call activity chain (level-1 → level-2 → level-3 [not deployed] = incident chain)
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "call-level-1-process", null));

        // Incidents / root cause test process
        processInstanceKeys.add(
            startProcessInstance(
                true, getTenant(TENANT_A), "incidents-process", "{\"testItems\": [1, 2, 3]}"));

        // reverted in Zeebe https://github.com/camunda/camunda/issues/13640
        //        processInstanceKeys.add(startProcessInstance(true,
        // getTenant(TENANT_A), "inclusiveGatewayProcess",
        //            "{\"saladOrdered\": "+ ThreadLocalRandom.current().nextBoolean()+ ",
        // \"pastaOrdered\": "+ ThreadLocalRandom.current().nextBoolean()+ "}"));
      }

      if (version == 2) {
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "interruptingBoundaryEvent", null));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "nonInterruptingBoundaryEvent", null));
        // call activity process
        // these instances must be fine
        processInstanceKeys.add(
            startProcessInstance(
                true,
                getTenant(TENANT_A),
                "call-activity-process",
                "{\"var\": " + ThreadLocalRandom.current().nextInt(10) + "}"));
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "escalationEvents", null));
      }
      if (version < 2) {
        processInstanceKeys.add(
            startProcessInstance(true, getTenant(TENANT_A), "prWithSubprocess", null));
      }

      if (version < 3) {
        processInstanceKeys.add(
            startProcessInstance(
                true,
                getTenant(TENANT_A),
                "complexProcess",
                "{\"clientId\": \"" + ThreadLocalRandom.current().nextInt(10) + "\"}"));
      }

      if (version == 3) {
        processInstanceKeys.add(
            startProcessInstance(
                true,
                getTenant(TENANT_A),
                "complexProcess",
                "{\"goUp\": " + ThreadLocalRandom.current().nextInt(5) + "}"));
        // call activity process
        // these instances will call second version of called process
        processInstanceKeys.add(
            startProcessInstance(
                true,
                getTenant(TENANT_A),
                "call-activity-process",
                "{\"orders\": ["
                    + ThreadLocalRandom.current().nextInt(10)
                    + ", "
                    + ThreadLocalRandom.current().nextInt(10)
                    + "]}"));
      }
      if (version == 4) {
        processInstanceKeys.add(
            startProcessInstance(
                true,
                getTenant(TENANT_A),
                "processAnnualLeave",
                "{\"leave_type\":\"fto\", \"days\":"
                    + ThreadLocalRandom.current().nextInt(15)
                    + "}"));
      }
    }
    if (version == 1) {
      processInstanceKeys.add(
          startProcessInstance(true, getTenant(TENANT_A), "timerProcess", null));
    }
  }

  @Override
  protected void deployVersion2() {
    super.deployVersion2();
    //    deploy processes v.2
    deployProcess(true, getTenant(TENANT_A), "develop/timerProcess_v_2.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/complexProcess_v_2.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/eventBasedGatewayProcess_v_2.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/interruptingBoundaryEvent_v_2.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/nonInterruptingBoundaryEvent_v_2.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/calledProcess.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/escalationEvents_v_2.bpmn");
  }

  @Override
  protected void deployVersion3() {
    super.deployVersion3();
    // deploy processes v.3
    deployProcess(true, getTenant(TENANT_A), "develop/complexProcess_v_3.bpmn");

    deployProcess(true, getTenant(TENANT_A), "develop/calledProcess_v_2.bpmn");
  }

  @Override
  protected void deployVersion4() {
    super.deployVersion4();
    deployProcess(true, getTenant(TENANT_A), "develop/user-task-annual-leave.bpmn");
  }

  private OperationType getType(final int i) {
    return i % 2 == 0 ? OperationType.CANCEL_PROCESS_INSTANCE : OperationType.RESOLVE_INCIDENT;
  }

  private void sendMessages(
      final String messageName,
      final String payload,
      final int count,
      final String correlationKey) {
    for (int i = 0; i < count; i++) {
      client
          .newPublishMessageCommand()
          .messageName(messageName)
          .correlationKey(correlationKey)
          .variables(payload)
          .timeToLive(Duration.ofSeconds(30))
          .messageId(UUID.randomUUID().toString())
          .send()
          .join();
    }
  }

  private void sendMessages(final String messageName, final String payload, final int count) {
    sendMessages(
        messageName, payload, count, String.valueOf(ThreadLocalRandom.current().nextInt(7)));
  }

  private JobWorker progressPlaceOrderTask() {
    return client
        .newWorker()
        .jobType("placeOrder")
        .handler(
            (jobClient, job) -> {
              final int shipping = ThreadLocalRandom.current().nextInt(5) - 1;
              jobClient
                  .newCompleteCommand(job.getKey())
                  .variables("{\"shipping\":" + shipping + "}")
                  .send()
                  .join();
            })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  private JobWorker progressTaskA() {
    return client
        .newWorker()
        .jobType("taskA")
        .handler(
            (jobClient, job) -> {
              final int scenarioCount = ThreadLocalRandom.current().nextInt(2);
              switch (scenarioCount) {
                case 0:
                  // successfully complete task
                  jobClient.newCompleteCommand(job.getKey()).send().join();
                  break;
                case 1:
                  // leave the task A active
                  break;
              }
            })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private JobWorker progressBigProcessTaskA() {
    return client
        .newWorker()
        .jobType("bigProcessTaskA")
        .handler(
            (jobClient, job) -> {
              final Map<String, Object> varMap = job.getVariablesAsMap();
              // increment loop count
              final Integer i = (Integer) varMap.get("i");
              varMap.put("i", i == null ? 1 : i + 1);
              jobClient.newCompleteCommand(job.getKey()).variables(varMap).send().join();
            })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private JobWorker progressBigProcessTaskB() {
    final int[] countBeforeIncident = {0};
    return client
        .newWorker()
        .jobType("bigProcessTaskB")
        .handler(
            (jobClient, job) -> {
              if (countBeforeIncident[0] <= 45) {
                jobClient.newCompleteCommand(job.getKey()).send().join();
                countBeforeIncident[0]++;
              } else {
                if (ThreadLocalRandom.current().nextBoolean()) {
                  // fail task -> create incident
                  jobClient.newFailCommand(job.getKey()).retries(0).send().join();
                } else {
                  jobClient.newCompleteCommand(job.getKey()).send().join();
                }
                countBeforeIncident[0] = 0;
              }
            })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private JobWorker progressErrorTask() {
    return client
        .newWorker()
        .jobType("errorTask")
        .handler(
            (jobClient, job) -> {
              final String errorCode =
                  (String) job.getVariablesAsMap().getOrDefault("errorCode", "error");
              jobClient
                  .newThrowErrorCommand(job.getKey())
                  .errorCode(errorCode)
                  .errorMessage("Job worker throw error with error code: " + errorCode)
                  .send()
                  .join();
            })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private JobWorker progressRetryTask() {
    return client
        .newWorker()
        .jobType("retryTask")
        .handler(
            (jobClient, job) -> {
              final int scenarioCount = ThreadLocalRandom.current().nextInt(4);
              switch (scenarioCount) {
                case 0:
                case 1:
                  // retry
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .variables("{\"retry\": true}")
                      .send()
                      .join();
                  break;
                case 2:
                  // incident
                  jobClient.newFailCommand(job.getKey()).retries(0).send().join();
                  break;
                default:
                  // complete task and process instance
                  jobClient
                      .newCompleteCommand(job.getKey())
                      .variables("{\"retry\": false}")
                      .send()
                      .join();
                  break;
              }
            })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private void createBigProcess(final int loopCardinality, final int numberOfClients) {
    final ObjectMapper objectMapper = new ObjectMapper();
    final ObjectNode object = objectMapper.createObjectNode();
    object.put("loopCardinality", loopCardinality);
    final ArrayNode arrayNode = object.putArray("clients");
    for (int j = 0; j <= numberOfClients; j++) {
      arrayNode.add(j);
    }
    final String jsonString = object.toString();
    startProcessInstance(true, getTenant(TENANT_A), "bigProcess", jsonString);
  }

  public void setClient(final CamundaClient client) {
    this.client = client;
  }
}
