/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data.usertest;

import static io.camunda.operate.util.ThreadUtil.sleepFor;

import io.camunda.operate.data.util.DecisionDataUtil;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.zeebe.client.api.command.ClientException;
import io.camunda.zeebe.client.api.command.FailJobCommandStep1;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.client.api.worker.JobWorker;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import io.camunda.operate.data.AbstractDataGenerator;
import io.camunda.operate.data.util.NameGenerator;
import io.camunda.operate.util.PayloadUtil;
import io.camunda.operate.util.ZeebeTestUtil;
import org.elasticsearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("dataGenerator")
@Profile("usertest-data")
public class UserTestDataGenerator extends AbstractDataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(UserTestDataGenerator.class);

  public static final int JOB_WORKER_TIMEOUT = 5;

  protected Random random = new Random();

  protected List<Long> processInstanceKeys = new ArrayList<>();
  protected List<Long> doNotTouchProcessInstanceKeys = new ArrayList<>();

  protected List<JobWorker> jobWorkers = new ArrayList<>();

  @Autowired
  protected PayloadUtil payloadUtil;

  @Autowired
  @Qualifier("esClient")
  private RestHighLevelClient esClient;

  @Autowired
  private DecisionDataUtil testUtil;

  @Override
  public boolean createZeebeData(boolean manuallyCalled) {
    if (!super.createZeebeData(manuallyCalled)) {
      return false;
    }
    logger.debug("Test data will be generated");

    createInputOutputMappingInstances();
    createProcessWithoutInstances();
    createProcessWithInstancesThatHasOnlyIncidents(5 + random.nextInt(17), 5 + random.nextInt(17));
    createProcessWithInstancesWithoutIncidents(5 + random.nextInt(23), 5 + random.nextInt(23));

    createAndStartProcessWithLargeVariableValue();
    createAndStartProcessWithLotOfVariables();

    deployVersion1();

    createSpecialDataV1();

    startProcessInstances(1);

    deployVersion2();

    createSpecialDataV2();

    startProcessInstances(2);

    deployVersion3();

    startProcessInstances(3);

    createOperations();

    progressProcessInstances();

    return true;

  }

  private void createInputOutputMappingInstances() {
    logger.debug("Create input/output mapping process instances");
    ZeebeTestUtil.deployProcess(client, "develop/always-completing-process.bpmn");
    ZeebeTestUtil.deployProcess(client, "develop/input-output-mappings-process.bpmn");
    ZeebeTestUtil.startProcessInstance(client, "Process_b1711b2e-ec8e-4dad-908c-8c12e028f32f", null);
  }

  private void createAndStartProcessWithLargeVariableValue() {
    logger.debug("Deploy and start process with large variable value >32kb");
    ZeebeTestUtil.deployProcess(client, "usertest/single-task.bpmn");
    String jsonString = payloadUtil.readStringFromClasspath("/usertest/large-payload.json");
    ZeebeTestUtil.startProcessInstance(client, "bigVarProcess", jsonString);
  }

  private void createAndStartProcessWithLotOfVariables() {
    StringBuffer vars = new StringBuffer("{");
    for (char letter1 = 'a'; letter1 <= 'z'; letter1++) {
      for (char letter2 = 'a'; letter2 <= 'z'; letter2++) {
        if (vars.length() > 1) {
          vars.append(",\n");
        }
        final String str = Character.toString(letter1) + Character.toString(letter2);
        vars.append("\"")
            .append(str)
            .append("\": \"value_")
            .append(str)
            .append("\"");
      }
    }
    vars.append("}");
    ZeebeTestUtil.startProcessInstance(client, "bigVarProcess", vars.toString());
  }

  public void createSpecialDataV1() {
    doNotTouchProcessInstanceKeys.add(startLoanProcess());

    final long instanceKey2 = startLoanProcess();
    completeTask(instanceKey2, "reviewLoanRequest", null);
    failTask(instanceKey2, "checkSchufa", "Schufa system is not accessible");
    doNotTouchProcessInstanceKeys.add(instanceKey2);

    final long instanceKey3 = startLoanProcess();
    completeTask(instanceKey3, "reviewLoanRequest", null);
    completeTask(instanceKey3, "checkSchufa", null);
    ZeebeTestUtil.cancelProcessInstance(client, instanceKey3);
    doNotTouchProcessInstanceKeys.add(instanceKey3);

    final long instanceKey4 = startLoanProcess();
    completeTask(instanceKey4, "reviewLoanRequest", null);
    completeTask(instanceKey4, "checkSchufa", null);
    completeTask(instanceKey4, "sendTheLoanDecision", null);
    doNotTouchProcessInstanceKeys.add(instanceKey4);

    doNotTouchProcessInstanceKeys.add(startOrderProcess());

    final long instanceKey5 = startOrderProcess();
    completeTask(instanceKey5, "checkPayment", "{\"paid\":true,\"orderStatus\": \"PAID\"}");
    failTask(instanceKey5, "shipArticles", "Cannot connect to server delivery05");
    doNotTouchProcessInstanceKeys.add(instanceKey5);

    final long instanceKey6 = startOrderProcess();
    completeTask(instanceKey6, "checkPayment", "{\"paid\":false}");
    ZeebeTestUtil.cancelProcessInstance(client, instanceKey6);
    doNotTouchProcessInstanceKeys.add(instanceKey6);

    final long instanceKey7 = startOrderProcess();
    completeTask(instanceKey7, "checkPayment", "{\"paid\":true,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey7, "shipArticles", "{\"orderStatus\":\"SHIPPED\"}");
    doNotTouchProcessInstanceKeys.add(instanceKey7);

    doNotTouchProcessInstanceKeys.add(startFlightRegistrationProcess());

    final long instanceKey8 = startFlightRegistrationProcess();
    completeTask(instanceKey8, "registerPassenger", null);
    doNotTouchProcessInstanceKeys.add(instanceKey8);

    final long instanceKey9 = startFlightRegistrationProcess();
    completeTask(instanceKey9, "registerPassenger", null);
    failTask(instanceKey9, "registerCabinBag", "No more stickers available");
    doNotTouchProcessInstanceKeys.add(instanceKey9);

    final long instanceKey10 = startFlightRegistrationProcess();
    completeTask(instanceKey10, "registerPassenger", null);
    completeTask(instanceKey10, "registerCabinBag", "{\"luggage\":true}");
    ZeebeTestUtil.cancelProcessInstance(client, instanceKey10);
    doNotTouchProcessInstanceKeys.add(instanceKey10);

    final long instanceKey11 = startFlightRegistrationProcess();
    completeTask(instanceKey11, "registerPassenger", null);
    completeTask(instanceKey11, "registerCabinBag", "{\"luggage\":false}");
    completeTask(instanceKey11, "printOutBoardingPass", null);
    doNotTouchProcessInstanceKeys.add(instanceKey11);

  }

  public void createSpecialDataV2() {
    final long instanceKey4 = startOrderProcess();
    completeTask(instanceKey4, "checkPayment", "{\"paid\":true,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey4, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    doNotTouchProcessInstanceKeys.add(instanceKey4);

    final long instanceKey5 = startOrderProcess();
    completeTask(instanceKey5, "checkPayment", "{\"paid\":true,\"orderStatus\": \"PAID\"}");
    failTask(instanceKey5, "checkItems", "Order information is not complete");
    doNotTouchProcessInstanceKeys.add(instanceKey5);

    final long instanceKey3 = startOrderProcess();
    completeTask(instanceKey3, "checkPayment", "{\"paid\":true,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey3, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    failTask(instanceKey3, "shipArticles", "Cannot connect to server delivery05");
    doNotTouchProcessInstanceKeys.add(instanceKey3);

    final long instanceKey2 = startOrderProcess();
    completeTask(instanceKey2, "checkPayment", "{\"paid\":true,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey2, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    failTask(instanceKey2, "shipArticles", "Order information is not complete");
    doNotTouchProcessInstanceKeys.add(instanceKey2);

    final long instanceKey1 = startOrderProcess();
    completeTask(instanceKey1, "checkPayment", "{\"paid\":true,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey1, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    failTask(instanceKey1, "shipArticles", "Cannot connect to server delivery05");
    doNotTouchProcessInstanceKeys.add(instanceKey1);

    final long instanceKey7 = startOrderProcess();
    completeTask(instanceKey7, "checkPayment", "{\"paid\":true,\"orderStatus\": \"PAID\"}");
    completeTask(instanceKey7, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    completeTask(instanceKey7, "shipArticles", "{\"orderStatus\":\"SHIPPED\"}");
    doNotTouchProcessInstanceKeys.add(instanceKey7);

    final long instanceKey6 = startOrderProcess();
    completeTask(instanceKey6, "checkPayment", "{\"paid\":false}");
    ZeebeTestUtil.cancelProcessInstance(client, instanceKey6);
    doNotTouchProcessInstanceKeys.add(instanceKey6);

    doNotTouchProcessInstanceKeys.add(startFlightRegistrationProcess());

    final long instanceKey8 = startFlightRegistrationProcess();
    completeTask(instanceKey8, "registerPassenger", null);
    doNotTouchProcessInstanceKeys.add(instanceKey8);

    final long instanceKey9 = startFlightRegistrationProcess();
    completeTask(instanceKey9, "registerPassenger", null);
    failTask(instanceKey9, "registerCabinBag", "Cannot connect to server fly-host");
    doNotTouchProcessInstanceKeys.add(instanceKey9);

    final long instanceKey10 = startFlightRegistrationProcess();
    completeTask(instanceKey10, "registerPassenger", null);
    completeTask(instanceKey10, "registerCabinBag", "{\"luggage\":true}");
    ZeebeTestUtil.cancelProcessInstance(client, instanceKey10);
    doNotTouchProcessInstanceKeys.add(instanceKey10);

    final long instanceKey11 = startFlightRegistrationProcess();
    completeTask(instanceKey11, "registerPassenger", null);
    completeTask(instanceKey11, "registerCabinBag", "{\"luggage\":true}");
    completeTask(instanceKey11, "determineLuggageWeight", "{\"luggageWeight\":21}");
    completeTask(instanceKey11, "registerLuggage", null);
    completeTask(instanceKey11, "printOutBoardingPass", null);
    doNotTouchProcessInstanceKeys.add(instanceKey11);

  }

  public void completeTask(long processInstanceKey, String jobType, String payload) {
    final CompleteJobHandler completeJobHandler = new CompleteJobHandler(payload, processInstanceKey);
    JobWorker jobWorker = client.newWorker()
      .jobType(jobType)
      .handler(completeJobHandler)
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .pollInterval(Duration.ofMillis(100))
      .open();
    int attempts = 0;
    while (!completeJobHandler.isTaskCompleted() && attempts < 10) {
      sleepFor(200);
      attempts++;
    }
    if (attempts == 10) {
      logger.debug("Could not complete the task {} for process instance id {}", jobType, processInstanceKey);
    }
    jobWorker.close();
  }

  public void failTask(long processInstanceKey, String jobType, String errorMessage) {
    final FailJobHandler failJobHandler = new FailJobHandler(processInstanceKey, errorMessage);
    JobWorker jobWorker = client.newWorker()
      .jobType(jobType)
      .handler(failJobHandler)
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .pollInterval(Duration.ofMillis(100))
      .open();
    int attempts = 0;
    while (!failJobHandler.isTaskFailed() && attempts < 10) {
      sleepFor(200);
      attempts++;
    }
    if (attempts == 10) {
      logger.debug("Could not fail the task {} for process instance id {}", jobType, processInstanceKey);
    }
    jobWorker.close();
  }

  protected void progressProcessInstances() {
    jobWorkers.add(progressReviewLoanRequestTask());
    jobWorkers.add(progressCheckSchufaTask());
    jobWorkers.add(progressSimpleTask("sendTheLoanDecision"));

    jobWorkers.add(progressSimpleTask("requestPayment"));
    jobWorkers.add(progressOrderProcessCheckPayment());
    jobWorkers.add(progressOrderProcessShipArticles());

    jobWorkers.add(progressOrderProcessCheckItems());

    jobWorkers.add(progressSimpleTask("requestWarehouse"));

    jobWorkers.add(progressSimpleTask("registerPassenger"));
    jobWorkers.add(progressFlightRegistrationRegisterCabinBag());
    jobWorkers.add(progressSimpleTask("registerLuggage"));
    jobWorkers.add(progressSimpleTask("printOutBoardingPass"));
    jobWorkers.add(progressSimpleTask("registerLuggage"));
    jobWorkers.add(progressFlightRegistrationDetermineWeight());
    jobWorkers.add(progressSimpleTask("processPayment"));

    jobWorkers.add(progressAlwaysFailingTask());

    jobWorkers.add(progressSimpleTask("peterTask"));

    //TODO remove me when DMN is working end-to-end`
    jobWorkers.add(progressSimpleTask("checkItems"));

    jobWorkers.addAll(progressMultiInstanceTasks());

    //start more instances after 1 minute
    scheduler.schedule(() ->
        startProcessInstances(3)
      , 1, TimeUnit.MINUTES);

    scheduler.schedule(() -> {
      for (JobWorker jobWorker: jobWorkers) {
        jobWorker.close();
      }
    }, 1000, TimeUnit.SECONDS);

    //there is a bug in Zeebe, when cancel happens concurrently with job worker running -> we're canceling after the job workers are stopped
    scheduler.schedule(() ->
        cancelSomeInstances(),
      510, TimeUnit.SECONDS);

  }

  private JobWorker progressAlwaysFailingTask() {
    return client.newWorker()
      .jobType("alwaysFailingTask")
      .handler((jobClient, job) ->
      {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        new Throwable().printStackTrace(pw);
        String errorMessage = "Something went wrong. \n" + sw.toString();
        jobClient.newFailCommand(job.getKey())
          .retries(0)
          .errorMessage(errorMessage)
          .send().join();
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private List<JobWorker> progressMultiInstanceTasks() {
    Random random = new Random();
    JobHandler handler = (c, j) -> {
      if (random.nextBoolean()) {
        c.newCompleteCommand(j.getKey()).send().join();
      }
      else {
        c.newFailCommand(j.getKey()).retries(0).send().join();
      }
    };

    List<JobWorker> workers = new ArrayList<>();
    workers.add(client.newWorker()
        .jobType("filter")
        .handler(handler)
        .open());
    workers.add(client.newWorker()
        .jobType("map")
        .handler(handler)
        .open());
    workers.add(client.newWorker()
        .jobType("reduce")
        .handler(handler)
        .open());

    return workers;
  }

  private void cancelSomeInstances() {
    final Iterator<Long> iterator = processInstanceKeys.iterator();
    while (iterator.hasNext()) {
      long processInstanceKey = iterator.next();
      if (random.nextInt(15) == 1) {
        try {
          client.newCancelInstanceCommand(processInstanceKey).send().join();
        } catch (ClientException ex) {
          logger.error("Error occurred when cancelling process instance:", ex);
        }
      }
      iterator.remove();
    }
  }

  protected void createOperations() {

  }

  protected JobWorker progressOrderProcessCheckPayment() {
    return client
      .newWorker()
      .jobType("checkPayment")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getProcessInstanceKey()))
          return;
        final int scenario = random.nextInt(5);
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
          jobClient.newCompleteCommand(job.getKey()).variables("{\"paid\":true,\"orderStatus\": \"PAID\"}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressOrderProcessCheckItems() {
    return client.newWorker()
      .jobType("checkItems")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getProcessInstanceKey()))
          return;
        final int scenario = random.nextInt(4);
        switch (scenario) {
        case 0:
        case 1:
        case 2:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}").send().join();
          break;
        case 3:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"smthIsMissing\":true}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressOrderProcessShipArticles() {
    return client.newWorker()
      .jobType("shipArticles")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getProcessInstanceKey()))
          return;
        final int scenario = random.nextInt(2);
        switch (scenario) {
        case 0:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"orderStatus\":\"SHIPPED\"}").send().join();
          break;
        case 1:
          jobClient.newFailCommand(job.getKey()).retries(0).errorMessage("Cannot connect to server delivery05").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressFlightRegistrationRegisterCabinBag() {
    return client.newWorker()
      .jobType("registerCabinBag")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getProcessInstanceKey()))
          return;
        final int scenario = random.nextInt(4);
        switch (scenario) {
        case 0:
        case 1:
        case 2:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"luggage\":false}").send().join();
          break;
        case 3:
          jobClient.newCompleteCommand(job.getKey()).variables("{\"luggage\":true}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressFlightRegistrationDetermineWeight() {
    return client.newWorker()
      .jobType("determineLuggageWeight")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getProcessInstanceKey()))
          return;
        jobClient.newCompleteCommand(job.getKey()).variables("{\"luggageWeight\":" + (random.nextInt(10) + 20) + "}").send().join();
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
        if (!canProgress(job.getProcessInstanceKey()))
          return;
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //leave the task active -> timeout
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

  private JobWorker progressReviewLoanRequestTask() {
    return client.newWorker()
      .jobType("reviewLoanRequest")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getProcessInstanceKey()))
          return;
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).variables("{\"loanRequestOK\": " + random.nextBoolean() + "}").send().join();
          break;
        case 1:
          //leave the task A active
          break;
        case 2:
          //fail task -> create incident
          jobClient.newFailCommand(job.getKey()).retries(0).errorMessage("Loan request does not contain all the required data").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressCheckSchufaTask() {
    return client.newWorker()
      .jobType("checkSchufa")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getProcessInstanceKey()))
          return;
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).variables("{\"schufaOK\": " + random.nextBoolean() + "}").send().join();
          break;
        case 1:
          //leave the task A active
          break;
        case 2:
          //fail task -> create incident
          jobClient.newFailCommand(job.getKey()).retries(0).errorMessage("Schufa system is not accessible").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private boolean canProgress(long key) {
    return !doNotTouchProcessInstanceKeys.contains(key);
  }

  protected void createProcessWithoutInstances() {
    Long processDefinitionKeyVersion1 = ZeebeTestUtil.deployProcess(client, "usertest/withoutInstancesProcess_v_1.bpmn");
    Long processDefinitionKeyVersion2 = ZeebeTestUtil.deployProcess(client, "usertest/withoutInstancesProcess_v_2.bpmn");
    logger.info("Created process 'withoutInstancesProcess' version 1: {} and version 2: {}", processDefinitionKeyVersion1, processDefinitionKeyVersion2);
  }

  protected void createProcessWithInstancesThatHasOnlyIncidents(int forVersion1,int forVersion2) {
    ZeebeTestUtil.deployProcess(client, "usertest/onlyIncidentsProcess_v_1.bpmn");
    for (int i = 0; i < forVersion1; i++) {
      Long processInstanceKey = ZeebeTestUtil.startProcessInstance(client, "onlyIncidentsProcess", null);
      failTask(processInstanceKey, "alwaysFails", "No memory left.");
    }
    ZeebeTestUtil.deployProcess(client, "usertest/onlyIncidentsProcess_v_2.bpmn");
    for (int i = 0; i < forVersion2; i++) {
      Long processInstanceKey = ZeebeTestUtil.startProcessInstance(client, "onlyIncidentsProcess", null);
      failTask(processInstanceKey, "alwaysFails", "No space left on device.");
      failTask(processInstanceKey, "alwaysFails2", "No space left on device.");
    }
    logger.info("Created process 'onlyIncidentsProcess' with {} instances for version 1 and {} instances for version 2", forVersion1, forVersion2);
  }

  protected void createProcessWithInstancesWithoutIncidents(int forVersion1,int forVersion2) {
    ZeebeTestUtil.deployProcess(client, "usertest/withoutIncidentsProcess_v_1.bpmn");
    for (int i = 0; i < forVersion1; i++) {
      ZeebeTestUtil.startProcessInstance(client, "withoutIncidentsProcess", null);
    }
    ZeebeTestUtil.deployProcess(client, "usertest/withoutIncidentsProcess_v_2.bpmn");
    for (int i = 0; i < forVersion2; i++) {
      Long processInstanceKey = ZeebeTestUtil.startProcessInstance(client, "withoutIncidentsProcess", null);
      completeTask(processInstanceKey, "neverFails", null);
    }
    logger.info("Created process 'withoutIncidentsProcess' with {} instances for version 1 and {} instances for version 2", forVersion1, forVersion2);
  }

  protected void deployVersion1() {
    //deploy processes v.1
    ZeebeTestUtil.deployProcess(client, "usertest/orderProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/loanProcess_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/registerPassenger_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/multiInstance_v_1.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/manual-task.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/intermediate-message-throw-event.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/intermediate-none-event.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/message-end-event.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/invoice.bpmn");

  }

  protected void startProcessInstances(int version) {
    final int instancesCount = random.nextInt(15) + 15;
    for (int i = 0; i < instancesCount; i++) {
      processInstanceKeys.add(startDMNInvoice());
      if (version < 2) {
        processInstanceKeys.add(startLoanProcess());
        processInstanceKeys.add(startManualProcess());
        processInstanceKeys.add(startIntermediateMessageThrowEventProcess());
        processInstanceKeys.add(startIntermediateNoneEventProcess());
        processInstanceKeys.add(startMessageEndEventProcess());
      }
      if (version < 3) {
        processInstanceKeys.add(startOrderProcess());
        processInstanceKeys.add(startFlightRegistrationProcess());
        processInstanceKeys.add(startMultiInstanceProcess());
      }
    }
    if (version == 1) {
      ZeebeTestUtil.startProcessInstance(client, "undefined-task-process", null);
    }
  }

  private long startFlightRegistrationProcess() {
    return ZeebeTestUtil.startProcessInstance(client, "flightRegistration",
      "{\n"
        + "  \"firstName\": \"" + NameGenerator.getRandomFirstName() + "\",\n"
        + "  \"lastName\": \"" + NameGenerator.getRandomLastName() + "\",\n"
        + "  \"passNo\": \"PS" + (random.nextInt(1000000) + (random.nextInt(9) + 1) * 1000000)  + "\",\n"
        + "  \"ticketNo\": \"" + random.nextInt(1000) + "\"\n"
        + "}");
  }

  private long startOrderProcess() {
    float price1 = Math.round(random.nextFloat() * 100000) / 100;
    float price2 = Math.round(random.nextFloat() * 10000) / 100;
    return ZeebeTestUtil.startProcessInstance(client, "orderProcess", "{\n"
      + "  \"clientNo\": \"CNT-1211132-02\",\n"
      + "  \"orderNo\": \"CMD0001-01\",\n"
      + "  \"items\": [\n"
      + "    {\n"
      + "      \"code\": \"123.135.625\",\n"
      + "      \"name\": \"Laptop Lenovo ABC-001\",\n"
      + "      \"quantity\": 1,\n"
      + "      \"price\": " + Double.valueOf(price1) + "\n"
      + "    },\n"
      + "    {\n"
      + "      \"code\": \"111.653.365\",\n"
      + "      \"name\": \"Headset Sony QWE-23\",\n"
      + "      \"quantity\": 2,\n"
      + "      \"price\": " + Double.valueOf(price2) + "\n"
      + "    }\n"
      + "  ],\n"
      + "  \"mwst\": " + Double.valueOf((price1 + price2) * 0.19) + ",\n"
      + "  \"total\": " + Double.valueOf((price1 + price2)) + ",\n"
      + "  \"orderStatus\": \"NEW\"\n"
      + "}");
  }

  private long startLoanProcess() {
    return ZeebeTestUtil.startProcessInstance(client, "loanProcess",
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
  }

  private long startDMNInvoice() {
    final String[] invoiceCategories = new String[]{"Misc", "Travel Expenses", "Software License Costs"};
    if (random.nextInt(3) > 0) {
      return ZeebeTestUtil.startProcessInstance(client, "invoice",
          "{\"amount\": " + (random.nextInt(1200)) + ",\n"
              + "  \"invoiceCategory\": \"" + invoiceCategories[random.nextInt(3)] + "\"\n"
              + "}");
    } else {
      return ZeebeTestUtil.startProcessInstance(client, "invoice", null);
    }
  }

  private long startManualProcess() {
    return ZeebeTestUtil.startProcessInstance(client, "manual-task-process", null);
  }

  private Long startIntermediateNoneEventProcess() {
    return ZeebeTestUtil.startProcessInstance(client, "intermediate-none-event-process", null);
  }

  private Long startIntermediateMessageThrowEventProcess() {
    return ZeebeTestUtil.startProcessInstance(client, "intermediate-message-throw-event-process", null);
  }

  private Long startMessageEndEventProcess() {
    return ZeebeTestUtil.startProcessInstance(client, "message-end-event-process", null);
  }

  private long startMultiInstanceProcess() {
    return ZeebeTestUtil.startProcessInstance(client, "multiInstanceProcess", "{\"items\": [1, 2, 3]}");
  }

  protected void deployVersion2() {
    //deploy processes v.2
    ZeebeTestUtil.deployProcess(client, "usertest/orderProcess_v_2.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/registerPassenger_v_2.bpmn");

    ZeebeTestUtil.deployProcess(client, "usertest/multiInstance_v_2.bpmn");

    ZeebeTestUtil.deployDecision(client, "usertest/invoiceBusinessDecisions_v_1.dmn");
  }

  protected void deployVersion3() {

    ZeebeTestUtil.deployDecision(client, "usertest/invoiceBusinessDecisions_v_2.dmn");

  }

  private static class CompleteJobHandler implements JobHandler {
    private final String payload;
    private final long processInstanceKey;
    private boolean taskCompleted = false;

    public CompleteJobHandler(String payload, long processInstanceKey) {
      this.payload = payload;
      this.processInstanceKey = processInstanceKey;
    }

    @Override
    public void handle(JobClient jobClient, ActivatedJob job) {
      if (!taskCompleted && processInstanceKey == job.getProcessInstanceKey()) {
        if (payload == null) {
          jobClient.newCompleteCommand(job.getKey()).variables(job.getVariables()).send().join();
        } else {
          jobClient.newCompleteCommand(job.getKey()).variables(payload).send().join();
        }
        taskCompleted = true;
      }
    }

    public boolean isTaskCompleted() {
      return taskCompleted;
    }
  }

  private static class FailJobHandler implements JobHandler {
    private final long processInstanceKey;
    private final String errorMessage;
    private boolean taskFailed = false;

    public FailJobHandler(long processInstanceKey, String errorMessage) {
      this.processInstanceKey = processInstanceKey;
      this.errorMessage = errorMessage;
    }

    @Override
    public void handle(JobClient jobClient, ActivatedJob job) {
      if (!taskFailed && processInstanceKey == job.getProcessInstanceKey()) {
        FinalCommandStep failCmd = jobClient.newFailCommand(job.getKey()).retries(0);
        if (errorMessage != null) {
          failCmd = ((FailJobCommandStep1.FailJobCommandStep2) failCmd).errorMessage(errorMessage);
        }
        failCmd.send().join();
        taskFailed = true;
      }
    }

    public boolean isTaskFailed() {
      return taskFailed;
    }
  }
}
