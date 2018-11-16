/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.operate.data.usertest;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.camunda.operate.data.AbstractDataGenerator;
import org.camunda.operate.data.util.NameGenerator;
import org.camunda.operate.util.IdUtil;
import org.camunda.operate.util.ZeebeTestUtil;
import org.camunda.operate.zeebe.payload.PayloadUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import io.zeebe.client.api.clients.JobClient;
import io.zeebe.client.api.response.ActivatedJob;
import io.zeebe.client.api.subscription.JobHandler;
import io.zeebe.client.api.subscription.JobWorker;
import io.zeebe.client.cmd.ClientException;

@Component("dataGenerator")
@Profile("usertest-data")
public class UserTestDataGenerator extends AbstractDataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(UserTestDataGenerator.class);

  public static final int JOB_WORKER_TIMEOUT = 5;

  protected Random random = new Random();

  private ScheduledExecutorService scheduler;

  protected List<Long> workflowInstanceKeys = new ArrayList<>();
  protected List<Long> doNotTouchWorkflowInstanceKeys = new ArrayList<>();

  protected List<JobWorker> jobWorkers = new ArrayList<>();

  @Autowired
  protected PayloadUtil payloadUtil;

  @Override
  public boolean createZeebeData(boolean manuallyCalled) {
    if (!super.createZeebeData(manuallyCalled)) {
      return false;
    }

    logger.debug("Test data will be generated");

    deployVersion1();

    createSpecialDataV1();

    startWorkflowInstances(1);

    deployVersion2();

    createSpecialDataV2();

    startWorkflowInstances(2);

    deployVersion3();

    startWorkflowInstances(3);

    progressWorkflowInstances();

    return true;

  }

  private void createSpecialDataV1() {
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(startLoanProcess()));

    final String instanceId2 = startLoanProcess();
    completeTask(instanceId2, "reviewLoanRequest", null);
    failTask(instanceId2, "checkSchufa");
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId2));

    final String instanceId3 = startLoanProcess();
    completeTask(instanceId3, "reviewLoanRequest", null);
    completeTask(instanceId3, "checkSchufa", null);
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceId3);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId3));

    final String instanceId4 = startLoanProcess();
    completeTask(instanceId4, "reviewLoanRequest", null);
    completeTask(instanceId4, "checkSchufa", null);
    completeTask(instanceId4, "sendTheLoanDecision", null);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId4));

    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(startOrderProcess()));

    final String instanceId5 = startOrderProcess();
    completeTask(instanceId5, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    failTask(instanceId5, "shipArticles");
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId5));

    final String instanceId6 = startOrderProcess();
    completeTask(instanceId6, "checkPayment", "{\"paid\":false,\"paidAmount\":0.0}");
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceId6);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId6));

    final String instanceId7 = startOrderProcess();
    completeTask(instanceId7, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    completeTask(instanceId7, "shipArticles", "{\"orderStatus\":\"SHIPPED\"}");
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId7));

    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(startFlightRegistrationProcess()));

    final String instanceId8 = startFlightRegistrationProcess();
    completeTask(instanceId8, "registerPassenger", null);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId8));

    final String instanceId9 = startFlightRegistrationProcess();
    completeTask(instanceId9, "registerPassenger", null);
    failTask(instanceId9, "registerCabinBag");
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId9));

    final String instanceId10 = startFlightRegistrationProcess();
    completeTask(instanceId10, "registerPassenger", null);
    completeTask(instanceId10, "registerCabinBag", "{\"luggage\":true}");
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceId10);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId10));

    final String instanceId11 = startFlightRegistrationProcess();
    completeTask(instanceId11, "registerPassenger", null);
    completeTask(instanceId11, "registerCabinBag", "{\"luggage\":false}");
    completeTask(instanceId11, "printOutBoardingPass", null);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId11));

  }

  private void createSpecialDataV2() {

    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(startOrderProcess()));

    final String instanceId5 = startOrderProcess();
    completeTask(instanceId5, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    failTask(instanceId5, "checkItems");
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId5));

    final String instanceId6 = startOrderProcess();
    completeTask(instanceId6, "checkPayment", "{\"paid\":false,\"paidAmount\":0.0}");
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceId6);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId6));

    final String instanceId7 = startOrderProcess();
    completeTask(instanceId7, "checkPayment", "{\"paid\":true,\"paidAmount\":300.0,\"orderStatus\": \"PAID\"}");
    completeTask(instanceId7, "checkItems", "{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}" );
    completeTask(instanceId7, "shipArticles", "{\"orderStatus\":\"SHIPPED\"}");
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId7));

    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(startFlightRegistrationProcess()));

    final String instanceId8 = startFlightRegistrationProcess();
    completeTask(instanceId8, "registerPassenger", null);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId8));

    final String instanceId9 = startFlightRegistrationProcess();
    completeTask(instanceId9, "registerPassenger", null);
    failTask(instanceId9, "registerCabinBag");
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId9));

    final String instanceId10 = startFlightRegistrationProcess();
    completeTask(instanceId10, "registerPassenger", null);
    completeTask(instanceId10, "registerCabinBag", "{\"luggage\":true}");
    ZeebeTestUtil.cancelWorkflowInstance(client, instanceId10);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId10));

    final String instanceId11 = startFlightRegistrationProcess();
    completeTask(instanceId11, "registerPassenger", null);
    completeTask(instanceId11, "registerCabinBag", "{\"luggage\":true}");
    completeTask(instanceId11, "determineLuggageWeight", "{\"luggageWeight\":21}");
    completeTask(instanceId11, "registerLuggage", null);
    completeTask(instanceId11, "printOutBoardingPass", null);
    doNotTouchWorkflowInstanceKeys.add(IdUtil.extractKey(instanceId11));

  }

  public void completeTask(String workflowInstanceId, String jobType, String payload) {
    final CompleteJobHandler completeJobHandler = new CompleteJobHandler(payload, workflowInstanceId);
    JobWorker jobWorker = client.jobClient().newWorker()
      .jobType(jobType)
      .handler(completeJobHandler)
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .pollInterval(Duration.ofMillis(100))
      .open();
    int attempts = 0;
    while (!completeJobHandler.isTaskCompleted() && attempts < 10) {
      try {
        Thread.sleep(200);
        attempts++;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (attempts == 10) {
      logger.debug("Could not complete the task {} for workflow instance id {}", jobType, workflowInstanceId);
    }
    jobWorker.close();
  }

  public void failTask(String workflowInstanceId, String jobType) {
    final FailJobHandler failJobHandler = new FailJobHandler(workflowInstanceId);
    JobWorker jobWorker = client.jobClient().newWorker()
      .jobType(jobType)
      .handler(failJobHandler)
      .name("operate")
      .timeout(Duration.ofSeconds(3))
      .pollInterval(Duration.ofMillis(100))
      .open();
    int attempts = 0;
    while (!failJobHandler.isTaskFailed() && attempts < 10) {
      try {
        Thread.sleep(200);
        attempts++;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    if (attempts == 10) {
      logger.debug("Could not fail the task {} for workflow instance id {}", jobType, workflowInstanceId);
    }
    jobWorker.close();
  }

  protected void progressWorkflowInstances() {
    List<JobWorker> jobWorkers = new ArrayList<>();

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

    //start more instances after 1 minute
    scheduler = Executors.newScheduledThreadPool(2);
    scheduler.schedule(() ->
        startWorkflowInstances(1)
      , 1, TimeUnit.MINUTES);

    scheduler.schedule(() -> {
      for (JobWorker jobWorker: jobWorkers) {
        jobWorker.close();
      }
    }, 90, TimeUnit.SECONDS);

    //there is a bug in Zeebe, when cancel happens concurrently with job worker running -> we're canceling after the job workers are stopped
    scheduler.schedule(() ->
        cancelSomeInstances(),
      100, TimeUnit.SECONDS);

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
      if (random.nextInt(15) == 1) {
        try {
          client.workflowClient().newCancelInstanceCommand(workflowInstanceKey).send().join();
        } catch (ClientException ex) {
          logger.error("Error occurred when cancelling workflow instance:", ex);
        }
      }
      iterator.remove();
    }
  }

  protected JobWorker progressOrderProcessCheckPayment() {
    return client.jobClient()
      .newWorker()
      .jobType("checkPayment")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenario = random.nextInt(5);
        switch (scenario){
        case 0:
          //fail
          throw new RuntimeException("Payment system not available.");
        case 1:
          Double total = null;
          Double paidAmount = null;
          try {
            final Map<String, Object> variables = payloadUtil.parsePayload(job.getPayload());
            total = (Double)variables.get("total");
            paidAmount = (Double)variables.get("paidAmount");
          } catch (IOException e) {
            e.printStackTrace();
          }
          if (total != null) {
            if (paidAmount != null) {
              paidAmount = paidAmount + ((total-paidAmount)/2);
            } else {
              paidAmount = total / 2;
            }
          }
          jobClient.newCompleteCommand(job.getKey()).payload("{\"paid\":false,\"paidAmount\":" + (paidAmount == null ? .0 : paidAmount) + "}").send().join();
          break;
        case 2:
        case 3:
        case 4:
          total = null;
          try {
            final Map<String, Object> variables = payloadUtil.parsePayload(job.getPayload());
            total = (Double)variables.get("total");
          } catch (IOException e) {
            e.printStackTrace();
          }
          jobClient.newCompleteCommand(job.getKey()).payload("{\"paid\":true,\"paidAmount\":" + (total == null ? .0 : total) + ",\"orderStatus\": \"PAID\"}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressOrderProcessCheckItems() {
    return client.jobClient().newWorker()
      .jobType("checkItems")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenario = random.nextInt(4);
        switch (scenario) {
        case 0:
        case 1:
        case 2:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"smthIsMissing\":false,\"orderStatus\":\"AWAITING_SHIPMENT\"}").send().join();
          break;
        case 3:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"smthIsMissing\":true}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressOrderProcessShipArticles() {
    return client.jobClient().newWorker()
      .jobType("shipArticles")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenario = random.nextInt(2);
        switch (scenario) {
        case 0:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"orderStatus\":\"SHIPPED\"}").send().join();
          break;
        case 1:
          jobClient.newFailCommand(job.getKey()).retries(0).send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressFlightRegistrationRegisterCabinBag() {
    return client.jobClient().newWorker()
      .jobType("registerCabinBag")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenario = random.nextInt(4);
        switch (scenario) {
        case 0:
        case 1:
        case 2:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"luggage\":false}").send().join();
          break;
        case 3:
          jobClient.newCompleteCommand(job.getKey()).payload("{\"luggage\":true}").send().join();
          break;
        }
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressFlightRegistrationDetermineWeight() {
    return client.jobClient().newWorker()
      .jobType("determineLuggageWeight")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        jobClient.newCompleteCommand(job.getKey()).payload("{\"luggageWeight\":" + (random.nextInt(10) + 20) + "}").send().join();
      })
      .name("operate")
      .timeout(Duration.ofSeconds(JOB_WORKER_TIMEOUT))
      .open();
  }

  private JobWorker progressSimpleTask(String taskType) {
    return client.jobClient().newWorker()
      .jobType(taskType)
      .handler((jobClient, job) ->
      {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
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
    return client.jobClient().newWorker()
      .jobType("reviewLoanRequest")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).payload("{\"loanRequestOK\": " + random.nextBoolean() + "}").send().join();
          break;
        case 1:
          //leave the task A active
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

  private JobWorker progressCheckSchufaTask() {
    return client.jobClient().newWorker()
      .jobType("checkSchufa")
      .handler((jobClient, job) -> {
        if (!canProgress(job.getHeaders().getWorkflowInstanceKey()))
          return;
        final int scenarioCount = random.nextInt(3);
        switch (scenarioCount) {
        case 0:
          //successfully complete task
          jobClient.newCompleteCommand(job.getKey()).payload("{\"schufaOK\": " + random.nextBoolean() + "}").send().join();
          break;
        case 1:
          //leave the task A active
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

  private boolean canProgress(long key) {
    return !doNotTouchWorkflowInstanceKeys.contains(key);
  }

  protected void deployVersion1() {
    //deploy workflows v.1
    ZeebeTestUtil.deployWorkflow(client, "usertest/orderProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "usertest/loanProcess_v_1.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "usertest/registerPassenger_v_1.bpmn");

  }

  protected void startWorkflowInstances(int version) {
    final int instancesCount = random.nextInt(50) + 50;
    for (int i = 0; i < instancesCount; i++) {
      if (version < 2) {

        String instanceId = startLoanProcess();

        workflowInstanceKeys.add(IdUtil.extractKey(instanceId));
      }
      if (version < 3) {

        String instanceId = startOrderProcess();

        workflowInstanceKeys.add(IdUtil.extractKey(instanceId));

        instanceId = startFlightRegistrationProcess();

        workflowInstanceKeys.add(IdUtil.extractKey(instanceId));
      }

    }
  }

  private String startFlightRegistrationProcess() {
    return ZeebeTestUtil.startWorkflowInstance(client, "flightRegistration",
      "{\n"
        + "  \"firstName\": \"" + NameGenerator.getRandomFirstName() + "\",\n"
        + "  \"lastName\": \"" + NameGenerator.getRandomLastName() + "\",\n"
        + "  \"passNo\": \"PS" + (random.nextInt(1000000) + (random.nextInt(9) + 1) * 1000000)  + "\",\n"
        + "  \"ticketNo\": \"" + random.nextInt(1000) + "\"\n"
        + "}");
  }

  private String startOrderProcess() {
    float price1 = Math.round(random.nextFloat() * 100000) / 100;
    float price2 = Math.round(random.nextFloat() * 10000) / 100;
    String instanceId = ZeebeTestUtil.startWorkflowInstance(client, "orderProcess", "{\n"
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
      + "  \"paidAmount\": 0,\n"
      + "  \"orderStatus\": \"NEW\"\n"
      + "}");
    return instanceId;
  }

  private String startLoanProcess() {
    String instanceId = ZeebeTestUtil.startWorkflowInstance(client, "loanProcess",
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
    return instanceId;
  }

  protected void deployVersion2() {
    //deploy workflows v.2
    ZeebeTestUtil.deployWorkflow(client, "usertest/orderProcess_v_2.bpmn");

    ZeebeTestUtil.deployWorkflow(client, "usertest/registerPassenger_v_2.bpmn");

  }

  protected void deployVersion3() {
  }

  private static class CompleteJobHandler implements JobHandler {
    private final String payload;
    private final String workflowInstanceId;
    private boolean taskCompleted = false;

    public CompleteJobHandler(String payload, String workflowInstanceId) {
      this.payload = payload;
      this.workflowInstanceId = workflowInstanceId;
    }

    @Override
    public void handle(JobClient jobClient, ActivatedJob job) {
      if (!taskCompleted && IdUtil.extractKey(workflowInstanceId) == job.getHeaders().getWorkflowInstanceKey()) {
        if (payload == null) {
          jobClient.newCompleteCommand(job.getKey()).payload(job.getPayload()).send().join();
        } else {
          jobClient.newCompleteCommand(job.getKey()).payload(payload).send().join();
        }
        taskCompleted = true;
      }
    }

    public boolean isTaskCompleted() {
      return taskCompleted;
    }
  }

  private static class FailJobHandler implements JobHandler {
    private final String workflowInstanceId;
    private boolean taskFailed = false;

    public FailJobHandler(String workflowInstanceId) {
      this.workflowInstanceId = workflowInstanceId;
    }

    @Override
    public void handle(JobClient jobClient, ActivatedJob job) {
      if (!taskFailed && IdUtil.extractKey(workflowInstanceId) == job.getHeaders().getWorkflowInstanceKey()) {
        jobClient.newFailCommand(job.getKey()).retries(0).send().join();
        taskFailed = true;
      }
    }

    public boolean isTaskFailed() {
      return taskFailed;
    }
  }
}
