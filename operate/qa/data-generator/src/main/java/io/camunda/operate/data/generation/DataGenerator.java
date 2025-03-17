/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.data.generation;

import static io.camunda.operate.qa.util.VariablesUtil.createALotOfVarsPayload;
import static io.camunda.operate.qa.util.VariablesUtil.createBigVarsWithSuffix;
import static io.camunda.operate.util.ThreadUtil.sleepFor;

import io.camunda.client.CamundaClient;
import io.camunda.operate.data.generation.DataGeneratorConfig.DataGeneratorThread;
import io.camunda.config.operate.ImportProperties;
import io.camunda.operate.qa.util.ZeebeTestUtil;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

/** It is considered that Zeebe and Elasticsearch are running. */
@Component
@Configuration
public class DataGenerator {

  public static final String PARENT_PROCESS_ID = "parentProcess";
  public static final String CHILD_PROCESS_ID = "childProcess";
  private static final Logger LOGGER = LoggerFactory.getLogger(DataGenerator.class);
  @Autowired private DataGeneratorProperties dataGeneratorProperties;

  @Autowired private CamundaClient camundaClient;

  @Autowired
  @Qualifier("dataGeneratorThreadPoolExecutor")
  private ThreadPoolTaskExecutor dataGeneratorTaskExecutor;

  private final Set<String> bpmnProcessIds = new HashSet<>();
  private final Random random = new Random();

  public void createData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    LOGGER.info("Starting generating data...");

    deployProcesses();

    startProcessInstances();
    completeAllTasks("task1");
    createIncidents("task2");

    // wait for task "endTask" of long-running process and complete it
    ZeebeTestUtil.completeTask(camundaClient, "endTask", "data-generator", null, 1);
    LOGGER.info("Task endTask completed.");

    LOGGER.info(
        "Data generation completed in: "
            + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now())
            + " s");
  }

  private void createIncidents(final String jobType) {
    final int incidentCount = dataGeneratorProperties.getIncidentCount();
    ZeebeTestUtil.failTask(camundaClient, jobType, "worker", incidentCount);
    LOGGER.info("{} incidents created", dataGeneratorProperties.getIncidentCount());
  }

  private void completeAllTasks(final String jobType) {
    completeTasks(
        jobType,
        dataGeneratorProperties.getProcessInstanceCount()
            + dataGeneratorProperties.getCallActivityProcessInstanceCount());
    LOGGER.info(
        "{} jobs task1 completed",
        dataGeneratorProperties.getProcessInstanceCount()
            + dataGeneratorProperties.getCallActivityProcessInstanceCount());
  }

  private void completeTasks(final String jobType, final int count) {
    ZeebeTestUtil.completeTask(
        camundaClient, jobType, "data-generator", "{\"varOut\": \"value2\"}", count);
  }

  private void startProcessInstances() {

    final BlockingQueue<Future> requestFutures =
        new ArrayBlockingQueue<>(dataGeneratorProperties.getQueueSize());
    final ResponseChecker responseChecker = startWaitingForResponses(requestFutures);

    sendStartProcessInstanceCommands(requestFutures);

    stopWaitingForResponses(responseChecker);
  }

  private ResponseChecker startWaitingForResponses(final BlockingQueue<Future> requestFutures) {
    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();
    return responseChecker;
  }

  private void stopWaitingForResponses(final ResponseChecker responseChecker) {
    // wait till all instances started
    final int allProcessInstancesCount =
        dataGeneratorProperties.getProcessInstanceCount()
            + dataGeneratorProperties.getCallActivityProcessInstanceCount();
    while (responseChecker.getResponseCount() < allProcessInstancesCount) {
      sleepFor(2000);
    }
    responseChecker.close();
    LOGGER.info("{} process instances started", responseChecker.getResponseCount());
  }

  private List<InstancesStarter> sendStartProcessInstanceCommands(
      final BlockingQueue<Future> requestFutures) {
    // separately start one instance with multi-instance subprocess
    startBigProcessInstance();

    final List<InstancesStarter> instancesStarters = new ArrayList<>();
    final int threadCount = dataGeneratorTaskExecutor.getMaxPoolSize();
    final AtomicInteger simpleProcessCounter = new AtomicInteger(0);
    final AtomicInteger callActivityProcessCounter = new AtomicInteger(0);
    for (int i = 0; i < threadCount; i++) {
      final InstancesStarter instancesStarter =
          new InstancesStarter(requestFutures, simpleProcessCounter, callActivityProcessCounter);
      dataGeneratorTaskExecutor.submit(instancesStarter);
      instancesStarters.add(instancesStarter);
    }
    return instancesStarters;
  }

  private void startBigProcessInstance() {
    final String payload =
        "{\"items\": ["
            + IntStream.range(1, 3000)
                .boxed()
                .map(Object::toString)
                .collect(Collectors.joining(","))
            + "]}";
    ZeebeTestUtil.startProcessInstance(camundaClient, "sequential-noop", payload);
  }

  @PreDestroy
  public void shutdown() {
    camundaClient.close();
    dataGeneratorTaskExecutor.shutdown();
  }

  private String getRandomBpmnProcessId() {
    return getBpmnProcessId(random.nextInt(dataGeneratorProperties.getProcessCount()));
  }

  private void deployProcesses() {
    for (int i = 0; i < dataGeneratorProperties.getProcessCount(); i++) {
      final String bpmnProcessId = getBpmnProcessId(i);
      ZeebeTestUtil.deployProcess(
          camundaClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
      bpmnProcessIds.add(bpmnProcessId);
    }

    // deploy call activity processes
    ZeebeTestUtil.deployProcess(
        camundaClient, createCallActivity1Model(), PARENT_PROCESS_ID + ".bpmn");
    ZeebeTestUtil.deployProcess(
        camundaClient, createCallActivity2Model(), CHILD_PROCESS_ID + ".bpmn");

    // deploy process with multi-instance subprocess
    ZeebeTestUtil.deployProcess(camundaClient, "sequential-noop.bpmn");
    LOGGER.info("{} processes deployed", dataGeneratorProperties.getProcessCount() + 3);
  }

  private String getBpmnProcessId(final int i) {
    return "process" + i;
  }

  private BpmnModelInstance createModel(final String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
        .startEvent("start")
        .subProcess()
        .embeddedSubProcess()
        .startEvent()
        .serviceTask("task1")
        .zeebeJobType("task1")
        .zeebeInput("=var1", "varIn")
        .zeebeOutput("=varOut", "var2")
        .endEvent()
        .subProcessDone()
        .serviceTask("task2")
        .zeebeJobType("task2")
        .serviceTask("task3")
        .zeebeJobType("task3")
        .serviceTask("task4")
        .zeebeJobType("task4")
        .serviceTask("task5")
        .zeebeJobType("task5")
        .endEvent()
        .done();
  }

  private BpmnModelInstance createCallActivity1Model() {
    return Bpmn.createExecutableProcess(PARENT_PROCESS_ID)
        .startEvent("start")
        .callActivity("callActivity1")
        .zeebeProcessId("childProcess")
        .done();
  }

  private BpmnModelInstance createCallActivity2Model() {
    return Bpmn.createExecutableProcess(CHILD_PROCESS_ID)
        .startEvent("start")
        .callActivity("callActivity2")
        .zeebeProcessId(getRandomBpmnProcessId())
        .done();
  }

  class ResponseChecker extends Thread {

    private final BlockingQueue<Future> futures;
    private volatile boolean shuttingDown = false;

    private int responseCount = 0;

    public ResponseChecker(final BlockingQueue<Future> futures) {
      this.futures = futures;
    }

    @Override
    public void run() {
      while (!shuttingDown) {
        try {
          futures.take().get();
        } catch (final ExecutionException e) {
          LOGGER.warn("Request failed", e);
          // we still count this as a response
        } catch (final InterruptedException ex) {
          Thread.currentThread().interrupt();
        }
        responseCount++;
        if (responseCount % 1000 == 0) {
          LOGGER.info("{} process instances started", responseCount);
        }
      }
    }

    public int getResponseCount() {
      return responseCount;
    }

    public void close() {
      shuttingDown = true;
      interrupt();
    }
  }

  class InstancesStarter implements Runnable {

    private final BlockingQueue<Future> futures;

    private boolean shuttingDown = false;

    private final AtomicInteger countSimpleProcess;
    private final AtomicInteger countCallActivityProcess;

    public InstancesStarter(
        final BlockingQueue<Future> futures,
        final AtomicInteger countSimpleProcess,
        final AtomicInteger countCallActivityProcess) {
      this.futures = futures;
      this.countSimpleProcess = countSimpleProcess;
      this.countCallActivityProcess = countCallActivityProcess;
    }

    @Override
    public void run() {
      camundaClient = resolveCamundaClient();
      int localCount = 0;
      while (countSimpleProcess.getAndIncrement()
              < dataGeneratorProperties.getProcessInstanceCount()
          && !shuttingDown) {
        try {
          final String vars;
          if (countSimpleProcess.get() == 1) {
            vars = createALotOfVarsPayload();
          } else if (countSimpleProcess.get() <= 100) {
            // third part of all process instances will get big variables
            vars = createBigVarsWithSuffix(ImportProperties.DEFAULT_VARIABLE_SIZE_THRESHOLD);
          } else {
            vars = "{\"var1\": \"value1\"}";
          }
          futures.put(
              ZeebeTestUtil.startProcessInstanceAsync(
                  camundaClient, getRandomBpmnProcessId(), vars));
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        localCount++;
        if (localCount % 1000 == 0) {
          LOGGER.info("{} start simple process instance requests were sent", localCount);
        }
      }
      localCount = 0;
      while (countCallActivityProcess.getAndIncrement()
              < dataGeneratorProperties.getCallActivityProcessInstanceCount()
          && !shuttingDown) {
        try {
          final String vars = "{\"var1\": \"value1\"}";
          futures.put(
              ZeebeTestUtil.startProcessInstanceAsync(camundaClient, PARENT_PROCESS_ID, vars));
        } catch (final InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        localCount++;
        if (localCount % 1000 == 0) {
          LOGGER.info("{} start call activity process instance requests were sent", localCount);
        }
      }
    }

    private CamundaClient resolveCamundaClient() {
      return ((DataGeneratorThread) Thread.currentThread()).getCamundaClient();
    }

    public void close() {
      shuttingDown = true;
    }
  }
}
