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
import io.camunda.operate.data.usertest.UserTestDataGenerator;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component("dataGenerator")
@Profile("dev-data")
public class DevelopDataGenerator extends UserTestDataGenerator {

  // TODO OPE-938 make this configurable
  private static final String OPERATE_HOST = "localhost";
  private static final int OPERATE_PORT = 8080;
  private static final String OPERATE_USER = "demo";
  private static final String OPERATE_PASSWORD = "demo";
  private static final String TENANT_A = "tenantA";

  private final List<Long> processInstanceKeys = new ArrayList<>();

  @Autowired private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;
  private StatefulRestTemplate restTemplate;

  @Override
  protected void startGeneratingData() {
    restTemplate = statefulRestTemplateFactory.apply(OPERATE_HOST, OPERATE_PORT);
    super.startGeneratingData();
  }

  @Override
  public void createSpecialDataV1() {
    int orderId = ThreadLocalRandom.current().current().nextInt(10);
    long instanceKey =
        ZeebeTestUtil.startProcessInstance(
            true,
            client,
            getTenant(TENANT_A),
            "interruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = ThreadLocalRandom.current().current().nextInt(10);
    instanceKey =
        ZeebeTestUtil.startProcessInstance(
            true,
            client,
            getTenant(TENANT_A),
            "interruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task2", null);

    orderId = ThreadLocalRandom.current().current().nextInt(10);
    instanceKey =
        ZeebeTestUtil.startProcessInstance(
            true,
            client,
            getTenant(TENANT_A),
            "nonInterruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = ThreadLocalRandom.current().current().nextInt(10);
    instanceKey =
        ZeebeTestUtil.startProcessInstance(
            true,
            client,
            getTenant(TENANT_A),
            "nonInterruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    failTask(instanceKey, "task1", "error");

    orderId = ThreadLocalRandom.current().current().nextInt(10);
    instanceKey =
        ZeebeTestUtil.startProcessInstance(
            true,
            client,
            getTenant(TENANT_A),
            "nonInterruptingBoundaryEvent",
            "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task1", null);
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

    sendMessages("clientMessage", "{\"messageVar\": \"someValue\"}", 20);
    sendMessages("interruptMessageTask", "{\"messageVar2\": \"someValue2\"}", 20);
    sendMessages("dataReceived", "{\"messageVar3\": \"someValue3\"}", 20);
  }

  @Override
  protected void createOperations() {
    restTemplate.loginWhenNeeded(OPERATE_USER, OPERATE_PASSWORD);
    final int operationsCount = ThreadLocalRandom.current().nextInt(20) + 90;
    for (int i = 0; i < operationsCount; i++) {
      final int no = ThreadLocalRandom.current().nextInt(operationsCount);
      final Long processInstanceKey = processInstanceKeys.get(no);
      final OperationType type = getType(i);
      final Map<String, Object> request =
          getCreateBatchOperationRequestBody(processInstanceKey, type);
      final RequestEntity<Map<String, Object>> requestEntity =
          RequestEntity.method(
                  HttpMethod.POST, restTemplate.getURL("/api/process-instances/batch-operation"))
              .contentType(MediaType.APPLICATION_JSON)
              .body(request);
      final ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
      if (!response.getStatusCode().equals(HttpStatus.OK)) {
        throw new OperateRuntimeException(
            String.format("Unable to create operations. REST response: %s", response));
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
    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/complexProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/eventBasedGatewayProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/subProcess.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/interruptingBoundaryEvent_v_1.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/nonInterruptingBoundaryEvent_v_1.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/timerProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/callActivityProcess.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/eventSubProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/bigProcess.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/errorProcess.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/error-end-event.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/terminateEndEvent.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/undefined-task.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/dataStore.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/linkEvents.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/escalationEvents_v_1.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/signalEvent.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/collapsedSubProcess.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/compensationEvents.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/executionListeners.bpmn");

    // reverted in Zeebe https://github.com/camunda/camunda/issues/13640
    // ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A),
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
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
                getTenant(TENANT_A),
                "call-activity-process",
                "{\"var\": " + ThreadLocalRandom.current().nextInt(10) + "}"));

        // eventSubprocess
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
                getTenant(TENANT_A),
                "eventSubprocessProcess",
                "{\"clientId\": \"" + ThreadLocalRandom.current().nextInt(10) + "\"}"));

        // errorProcess
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
                getTenant(TENANT_A),
                "errorProcess",
                "{\"errorCode\": \"boundary\"}"));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
                getTenant(TENANT_A),
                "errorProcess",
                "{\"errorCode\": \"subProcess\"}"));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "errorProcess", "{\"errorCode\": \"unknown\"}"));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "error-end-process", null));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "terminateEndEvent", null));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "collapsedSubProcess", null));

        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "dataStoreProcess", null));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "linkEventProcess", null));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "escalationEvents", null));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "undefined-task-process", null));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "compensationEvents", null));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "executionListeners", null));
        // reverted in Zeebe https://github.com/camunda/camunda/issues/13640
        //        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(true, client,
        // getTenant(TENANT_A), "inclusiveGatewayProcess",
        //            "{\"saladOrdered\": "+ ThreadLocalRandom.current().nextBoolean()+ ",
        // \"pastaOrdered\": "+ ThreadLocalRandom.current().nextBoolean()+ "}"));
      }

      if (version == 2) {
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "interruptingBoundaryEvent", null));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "nonInterruptingBoundaryEvent", null));
        // call activity process
        // these instances must be fine
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
                getTenant(TENANT_A),
                "call-activity-process",
                "{\"var\": " + ThreadLocalRandom.current().nextInt(10) + "}"));
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "escalationEvents", null));
      }
      if (version < 2) {
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true, client, getTenant(TENANT_A), "prWithSubprocess", null));
      }

      if (version < 3) {
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
                getTenant(TENANT_A),
                "complexProcess",
                "{\"clientId\": \"" + ThreadLocalRandom.current().nextInt(10) + "\"}"));
      }

      if (version == 3) {
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
                getTenant(TENANT_A),
                "complexProcess",
                "{\"goUp\": " + ThreadLocalRandom.current().nextInt(5) + "}"));
        // call activity process
        // these instances will call second version of called process
        processInstanceKeys.add(
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
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
            ZeebeTestUtil.startProcessInstance(
                true,
                client,
                getTenant(TENANT_A),
                "processAnnualLeave",
                "{\"leave_type\":\"fto\", \"days\":"
                    + ThreadLocalRandom.current().nextInt(15)
                    + "}"));
      }
    }
    if (version == 1) {
      processInstanceKeys.add(
          ZeebeTestUtil.startProcessInstance(
              true, client, getTenant(TENANT_A), "timerProcess", null));
    }
  }

  @Override
  protected void deployVersion2() {
    super.deployVersion2();
    //    deploy processes v.2
    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/timerProcess_v_2.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/complexProcess_v_2.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/eventBasedGatewayProcess_v_2.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/interruptingBoundaryEvent_v_2.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/nonInterruptingBoundaryEvent_v_2.bpmn");

    ZeebeTestUtil.deployProcess(true, client, getTenant(TENANT_A), "develop/calledProcess.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/escalationEvents_v_2.bpmn");
  }

  @Override
  protected void deployVersion3() {
    super.deployVersion3();
    // deploy processes v.3
    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/complexProcess_v_3.bpmn");

    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/calledProcess_v_2.bpmn");
  }

  @Override
  protected void deployVersion4() {
    super.deployVersion4();
    ZeebeTestUtil.deployProcess(
        true, client, getTenant(TENANT_A), "develop/user-task-annual-leave.bpmn");
  }

  private Map<String, Object> getCreateBatchOperationRequestBody(
      final Long processInstanceKey, final OperationType type) {
    final Map<String, Object> request = new HashMap<>();
    final Map<String, Object> listViewRequest = new HashMap<>();
    listViewRequest.put("running", true);
    listViewRequest.put("active", true);
    listViewRequest.put("ids", new Long[] {processInstanceKey});
    request.put("query", listViewRequest);
    request.put("operationType", type.toString());
    return request;
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
    ZeebeTestUtil.startProcessInstance(true, client, getTenant(TENANT_A), "bigProcess", jsonString);
  }

  public void setClient(final ZeebeClient client) {
    this.client = client;
  }
}
