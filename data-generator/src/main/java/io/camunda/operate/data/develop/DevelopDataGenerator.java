/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data.develop;

import static org.elasticsearch.xcontent.XContentFactory.jsonBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.data.usertest.UserTestDataGenerator;
import io.camunda.operate.entities.OperationType;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.schema.templates.FlowNodeInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.util.ZeebeTestUtil;
import io.camunda.operate.util.rest.StatefulRestTemplate;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.worker.JobWorker;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiFunction;
import jakarta.annotation.PostConstruct;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.xcontent.XContentBuilder;
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

  //TODO OPE-938 make this configurable
  private static final String OPERATE_HOST = "localhost";
  private static final int OPERATE_PORT = 8080;
  private static final String OPERATE_USER = "demo";
  private static final String OPERATE_PASSWORD = "demo";

  private List<Long> processInstanceKeys = new ArrayList<>();

  @Autowired
  private BiFunction<String, Integer, StatefulRestTemplate> statefulRestTemplateFactory;
  private StatefulRestTemplate restTemplate;

  @Autowired
  private RestHighLevelClient esClient;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private FlowNodeInstanceTemplate flowNodeInstanceTemplate;

  @Autowired
  private ListViewTemplate listViewTemplate;

  private Random random = new Random();

  @PostConstruct
  private void initRestTemplate() {
    restTemplate = statefulRestTemplateFactory.apply(OPERATE_HOST, OPERATE_PORT);
  }

  @Override
  public void createSpecialDataV1() {
    int orderId = random.nextInt(10);
    long instanceKey = ZeebeTestUtil
      .startProcessInstance(client, "interruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startProcessInstance(client, "interruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("interruptTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task2", null);

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startProcessInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startProcessInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    failTask(instanceKey, "task1", "error");

    orderId = random.nextInt(10);
    instanceKey = ZeebeTestUtil
      .startProcessInstance(client, "nonInterruptingBoundaryEvent", "{\"orderId\": \"" + orderId + "\"\n}");
    doNotTouchProcessInstanceKeys.add(instanceKey);
    sendMessages("messageTask1", "{\"messageVar\": \"someValue\"\n}", 1, String.valueOf(orderId));
    completeTask(instanceKey, "task1", null);

  }

  @Override
  protected void progressProcessInstances() {

    super.progressProcessInstances();

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

    //call activity process
    jobWorkers.add(progressSimpleTask("called-task"));

    //eventSubprocess
    jobWorkers.add(progressSimpleTask("parentProcessTask"));
    jobWorkers.add(progressSimpleTask("subprocessTask"));
    jobWorkers.add(progressSimpleTask("subSubprocessTask"));
    jobWorkers.add(progressSimpleTask("eventSupbprocessTask"));

    //big process
    jobWorkers.add(progressBigProcessTaskA());
    jobWorkers.add(progressBigProcessTaskB());

    //error process
    jobWorkers.add(progressErrorTask());

    //link event process
    jobWorkers.add(progressRetryTask());
    //escalation events process
    jobWorkers.add(progressPlaceOrderTask());

    sendMessages("clientMessage", "{\"messageVar\": \"someValue\"}", 20);
    sendMessages("interruptMessageTask", "{\"messageVar2\": \"someValue2\"}", 20);
    sendMessages("dataReceived", "{\"messageVar3\": \"someValue3\"}", 20);

  }

  @Override
  protected void createOperations() {
    restTemplate.loginWhenNeeded(OPERATE_USER, OPERATE_PASSWORD);
    final int operationsCount = random.nextInt(20) + 90;
    for (int i=0; i<operationsCount; i++) {
      final int no = random.nextInt(operationsCount);
      final Long processInstanceKey = processInstanceKeys.get(no);
      final OperationType type = getType(i);
      Map<String, Object> request = getCreateBatchOperationRequestBody(processInstanceKey, type);
      RequestEntity<Map<String, Object>> requestEntity = RequestEntity.method(HttpMethod.POST, restTemplate.getURL("/api/process-instances/batch-operation"))
          .contentType(MediaType.APPLICATION_JSON).body(request);
      final ResponseEntity<String> response = restTemplate.exchange(requestEntity, String.class);
      if (!response.getStatusCode().equals(HttpStatus.OK)) {
        throw new OperateRuntimeException(String.format("Unable to create operations. REST response: %s", response));
      }
    }
  }

  private Map<String, Object> getCreateBatchOperationRequestBody(Long processInstanceKey, OperationType type) {
    Map<String, Object> request = new HashMap<>();
    Map<String, Object> listViewRequest = new HashMap<>();
    listViewRequest.put("running", true);
    listViewRequest.put("active", true);
    listViewRequest.put("ids", new Long[]{processInstanceKey});
    request.put("query", listViewRequest);
    request.put("operationType" , type.toString());
    return request;
  }

  private OperationType getType(int i) {
    return i % 2 == 0 ? OperationType.CANCEL_PROCESS_INSTANCE : OperationType.RESOLVE_INCIDENT;
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

  private JobWorker progressPlaceOrderTask() {
    return client.newWorker()
      .jobType("placeOrder")
      .handler((jobClient, job) ->
      {
        final int shipping = random.nextInt(5) - 1;
        jobClient.newCompleteCommand(job.getKey()).variables("{\"shipping\":" + shipping + "}").send().join();
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

  private JobWorker progressBigProcessTaskA() {
    return client.newWorker()
        .jobType("bigProcessTaskA")
        .handler((jobClient, job) -> {
          Map<String, Object> varMap = job.getVariablesAsMap();
          //increment loop count
          Integer i = (Integer)varMap.get("i");
          varMap.put("i", i == null ? 1 : i+1);
          jobClient.newCompleteCommand(job.getKey()).variables(varMap).send().join();
        })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private JobWorker progressBigProcessTaskB() {
    final int[] countBeforeIncident = {0};
    return client.newWorker()
        .jobType("bigProcessTaskB")
        .handler((jobClient, job) -> {
          if (countBeforeIncident[0] <= 45) {
            jobClient.newCompleteCommand(job.getKey()).send().join();
            countBeforeIncident[0]++;
          } else {
            if (random.nextBoolean()) {
              //fail task -> create incident
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
    return client.newWorker()
        .jobType("errorTask")
        .handler((jobClient, job) -> {
          String errorCode = (String) job.getVariablesAsMap().getOrDefault("errorCode", "error");
          jobClient.newThrowErrorCommand(job.getKey())
              .errorCode(errorCode)
              .errorMessage("Job worker throw error with error code: " + errorCode)
              .send().join();
        })
        .name("operate")
        .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
        .open();
  }

  private JobWorker progressRetryTask() {
    return client.newWorker()
        .jobType("retryTask")
        .handler((jobClient, job) ->
        {
          final int scenarioCount = random.nextInt(4);
          switch (scenarioCount) {
          case 0:
          case 1:
            //retry
            jobClient.newCompleteCommand(job.getKey()).variables("{\"retry\": true}").send().join();
            break;
          case 2:
            //incident
            jobClient.newFailCommand(job.getKey()).retries(0).send().join();
            break;
          default:
            //complete task and process instance
            jobClient.newCompleteCommand(job.getKey()).variables("{\"retry\": false}").send().join();
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

    //deploy processes v.1
    ZeebeTestUtil.deployProcess(client, "develop/complexProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/eventBasedGatewayProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/subProcess.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/interruptingBoundaryEvent_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/nonInterruptingBoundaryEvent_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/timerProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/callActivityProcess.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/eventSubProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/bigProcess.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/errorProcess.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/error-end-event.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/terminateEndEvent.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/undefined-task.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/dataStore.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/linkEvents.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/escalationEvents_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/signalEvent.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/inclusiveGateway.bpmn");
  }

  @Override
  protected void startProcessInstances(int version) {
    super.startProcessInstances(version);
    if (version == 1) {
      createBigProcess(40, 1000);
    }
    final int instancesCount = random.nextInt(15) + 15;
    for (int i = 0; i < instancesCount; i++) {

      if (version == 1) {
        //eventBasedGatewayProcess v.1
        sendMessages("newClientMessage", "{\"clientId\": \"" + random.nextInt(10) + "\"\n}", 1);

        //call activity process
        //these instances will have incident on call activity
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "call-activity-process", "{\"var\": " + random.nextInt(10) + "}"));

        //eventSubprocess
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "eventSubprocessProcess", "{\"clientId\": \"" + random.nextInt(10) + "\"}"));

        // errorProcess
        processInstanceKeys.add(ZeebeTestUtil
            .startProcessInstance(client, "errorProcess", "{\"errorCode\": \"boundary\"}"));
        processInstanceKeys.add(ZeebeTestUtil
            .startProcessInstance(client, "errorProcess", "{\"errorCode\": \"subProcess\"}"));
        processInstanceKeys.add(ZeebeTestUtil
            .startProcessInstance(client, "errorProcess", "{\"errorCode\": \"unknown\"}"));
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "error-end-process", null));
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "terminateEndEvent", null));
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "dataStoreProcess", null));
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "linkEventProcess", null));
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "escalationEvents", null));
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "inclusiveGatewayProcess",
            "{\"saladOrdered\": "+ random.nextBoolean()+ ", \"pastaOrdered\": "+ random.nextBoolean()+ "}"));
      }

      if (version == 2) {
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "interruptingBoundaryEvent", null));
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "nonInterruptingBoundaryEvent", null));
        //call activity process
        //these instances must be fine
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "call-activity-process", "{\"var\": " + random.nextInt(10) + "}"));
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "escalationEvents", null));
      }
      if (version < 2) {
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "prWithSubprocess", null));
      }

      if (version < 3) {
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "complexProcess", "{\"clientId\": \"" + random.nextInt(10) + "\"}"));
      }

      if (version == 3) {
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "complexProcess", "{\"goUp\": " + random.nextInt(5) + "}"));
        //call activity process
        //these instances will call second version of called process
        processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "call-activity-process",
            "{\"orders\": [" + random.nextInt(10) + ", " + random.nextInt(10) + "]}"));
      }

    }
    if (version == 1) {
      processInstanceKeys.add(ZeebeTestUtil.startProcessInstance(client, "timerProcess", null));
      startSignalEventsProcess(instancesCount);
    }
  }

  private void createBigProcess(int loopCardinality, int numberOfClients) {
    XContentBuilder builder = null;
    try {
      builder = jsonBuilder()
        .startObject()
          .field("loopCardinality", loopCardinality)
          .field("clients")
          .startArray();
      for (int j = 0; j <= numberOfClients; j++) {
        builder
            .value(j);
      }
      builder
          .endArray()
        .endObject();
      ZeebeTestUtil.startProcessInstance(client, "bigProcess", Strings.toString(builder));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void deployVersion2() {
    super.deployVersion2();
//    deploy processes v.2
    ZeebeTestUtil.deployProcess(client, "develop/timerProcess_v_2.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/complexProcess_v_2.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/eventBasedGatewayProcess_v_2.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/interruptingBoundaryEvent_v_2.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/nonInterruptingBoundaryEvent_v_2.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/calledProcess.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/escalationEvents_v_2.bpmn");

  }

  @Override
  protected void deployVersion3() {
    super.deployVersion3();
    //deploy processes v.3
    ZeebeTestUtil.deployProcess(client, "develop/complexProcess_v_3.bpmn");

    ZeebeTestUtil.deployProcess(client, "develop/calledProcess_v_2.bpmn");

  }

  private void startSignalEventsProcess(int count) {
    ZeebeTestUtil.sendSignal(client, "startSignal1", "{\"signalNumber\": " + random.nextInt(100) + "}", count);
  }

  public void setClient(ZeebeClient client) {
    this.client = client;
  }

}
