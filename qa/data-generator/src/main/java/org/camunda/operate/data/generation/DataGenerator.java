/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.camunda.operate.data.generation.DataGenerationApp.DataGeneratorThread;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import static org.camunda.operate.util.ThreadUtil.sleepFor;

/**
 * It is considered that Zeebe and Elasticsearch are running.
 */
@Component
@Configuration
public class DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);

  @Autowired
  private DataGeneratorProperties dataGeneratorProperties;

  @Autowired
  private ZeebeClient zeebeClient;

  @Autowired
  @Qualifier("dataGeneratorThreadPoolExecutor")
  private ThreadPoolTaskExecutor dataGeneratorTaskExecutor;

  private Set<String> bpmnProcessIds = new HashSet<>();
  private Random random = new Random();

  public void createData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting generating data...");

    deployProcesses();

    startProcessInstances();
    completeAllTasks("task1");
    createIncidents("task2");


    //wait for task "endTask" of long-running process and complete it
    ZeebeTestUtil.completeTask(zeebeClient, "endTask", "data-generator", null, 1);
    logger.info("Task endTask completed.");

    logger.info("Data generation completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");
  }

  private void createIncidents(String jobType) {
    final int incidentCount = dataGeneratorProperties.getIncidentCount();
    ZeebeTestUtil.failTask(zeebeClient, jobType, "worker", incidentCount);
    logger.info("{} incidents created", dataGeneratorProperties.getIncidentCount());
  }

  private void completeAllTasks(String jobType) {
    completeTasks(jobType, dataGeneratorProperties.getProcessInstanceCount());
    logger.info("{} jobs task1 completed", dataGeneratorProperties.getProcessInstanceCount());
  }

  private void completeTasks(String jobType, int count) {
    ZeebeTestUtil.completeTask(zeebeClient, jobType, "data-generator", "{\"varOut\": \"value2\"}", count);
  }

  private void startProcessInstances() {

    final BlockingQueue<Future> requestFutures = new ArrayBlockingQueue<>(dataGeneratorProperties.getQueueSize());
    ResponseChecker responseChecker = startWaitingForResponses(requestFutures);

    List<InstancesStarter> instancesStarters = sendStartProcessInstanceCommands(requestFutures);

    stopWaitingForResponses(responseChecker);

    instancesStarters.forEach(InstancesStarter::close);

  }

  private ResponseChecker startWaitingForResponses(BlockingQueue<Future> requestFutures) {
    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();
    return responseChecker;
  }

  private void stopWaitingForResponses(ResponseChecker responseChecker) {
    //wait till all instances started
    while (responseChecker.getResponseCount() < dataGeneratorProperties.getProcessInstanceCount()) {
      sleepFor(2000);
    }
    responseChecker.close();
    logger.info("{} process instances started", responseChecker.getResponseCount());
  }

  private List<InstancesStarter> sendStartProcessInstanceCommands(BlockingQueue<Future> requestFutures) {
    //separately start one instance with multi-instance subprocess
    startBigProcessInstance();

    List<InstancesStarter> instancesStarters = new ArrayList<>();
    final int threadCount = dataGeneratorTaskExecutor.getMaxPoolSize();
    final AtomicInteger counter = new AtomicInteger(0);
    for (int i = 0; i < threadCount; i++) {
      InstancesStarter instancesStarter = new InstancesStarter(requestFutures, counter);
      dataGeneratorTaskExecutor.submit(instancesStarter);
      instancesStarters.add(instancesStarter);
    }
    return instancesStarters;
  }

  private void startBigProcessInstance() {
    String payload =
        "{\"items\": [" + IntStream.range(1, 3000).boxed().map(Object::toString).collect(
            Collectors.joining(",")) + "]}";
    ZeebeTestUtil
        .startProcessInstance(zeebeClient, "sequential-noop", payload);
  }

  @PreDestroy
  public void shutdown(){
    zeebeClient.close();
    dataGeneratorTaskExecutor.shutdown();
  }

  private String getRandomBpmnProcessId() {
    return getBpmnProcessId(random.nextInt(dataGeneratorProperties.getProcessCount()));
  }

  private void deployProcesses() {
    for (int i = 0; i< dataGeneratorProperties.getProcessCount(); i++) {
      String bpmnProcessId = getBpmnProcessId(i);
      ZeebeTestUtil.deployProcess(zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
      bpmnProcessIds.add(bpmnProcessId);
    }
    //deploy process with multi-instance subprocess
    ZeebeTestUtil.deployProcess(zeebeClient, "sequential-noop.bpmn");
    logger.info("{} processes deployed", dataGeneratorProperties.getProcessCount());
  }

  private String getBpmnProcessId(int i) {
    return "process" + i;
  }

  private BpmnModelInstance createModel(String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
    .startEvent("start")
      .serviceTask("task1").zeebeJobType("task1")
        .zeebeInput("=var1", "varIn")
        .zeebeOutput("=varOut", "var2")
      .serviceTask("task2").zeebeJobType("task2")
      .serviceTask("task3").zeebeJobType("task3")
      .serviceTask("task4").zeebeJobType("task4")
      .serviceTask("task5").zeebeJobType("task5")
    .endEvent()
    .done();
  }

  class ResponseChecker extends Thread {

    private final BlockingQueue<Future> futures;
    private volatile boolean shuttingDown = false;

    private int responseCount = 0;

    public ResponseChecker(BlockingQueue<Future> futures) {
      this.futures = futures;
    }

    @Override
    public void run() {
      while (!shuttingDown) {
        try {
          futures.take().get();
          responseCount++;
          if (responseCount % 1000 == 0) {
            logger.info("{} process instances started", responseCount);
          }
        } catch (InterruptedException e) {
          // ignore and retry
        } catch (ExecutionException e) {
          logger.warn("Request failed", e);
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

    private AtomicInteger count;

    public InstancesStarter(BlockingQueue<Future> futures, AtomicInteger count) {
      this.futures = futures;
      this.count = count;
    }

    @Override
    public void run() {
      zeebeClient = resolveZeebeClient();
      int localCount = 0;
      while (count.getAndIncrement() <= dataGeneratorProperties.getProcessInstanceCount()  && ! shuttingDown) {
        try {
          futures.put(ZeebeTestUtil.startProcessInstanceAsync(zeebeClient, getRandomBpmnProcessId(), "{\"var1\": \"value1\"}"));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        localCount++;
        if (localCount % 1000 == 0) {
          logger.info("{} start process instance requests were sent", localCount);
        }
      }
    }

    private ZeebeClient resolveZeebeClient() {
      return ((DataGeneratorThread)Thread.currentThread()).getZeebeClient();
    }

    public void close() {
      shuttingDown = true;
    }

  }
}
