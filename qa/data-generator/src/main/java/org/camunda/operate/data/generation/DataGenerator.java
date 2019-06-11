/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.data.generation;

import javax.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.camunda.operate.qa.util.ZeebeTestUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import io.zeebe.client.ZeebeClient;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;

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

  private Set<String> bpmnProcessIds = new HashSet<>();
  private Random random = new Random();

  public void createData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    logger.info("Starting generating data...");

    deployWorkflows();

    startWorkflowInstances();
    completeAllTasks("task1");
    createIncidents("task2");

    logger.info("Data generation completed in: " + ChronoUnit.SECONDS.between(dataGenerationStart, OffsetDateTime.now()) + " s");
  }

  private void createIncidents(String jobType) {
    final int incidentCount = dataGeneratorProperties.getIncidentCount();
    ZeebeTestUtil.failTask(zeebeClient, jobType, "worker", incidentCount);
    logger.info("{} incidents created", dataGeneratorProperties.getIncidentCount());
  }

  private void completeAllTasks(String jobType) {
    completeTasks(jobType, dataGeneratorProperties.getWorkflowInstanceCount());
    logger.info("{} jobs task1 completed", dataGeneratorProperties.getWorkflowInstanceCount());
  }

  private void completeTasks(String jobType, int count) {
    ZeebeTestUtil.completeTask(zeebeClient, jobType, "worker", "{\"varOut\": \"value2\"}", count);
  }

  private void startWorkflowInstances() {

    ExecutorService executorService = createExecutorService();

    final BlockingQueue<Future> requestFutures = sendStartWorkflowInstanceCommands(executorService);

    waitForResponses(requestFutures);

    shutdownExecutorService(executorService);

  }

  private void shutdownExecutorService(ExecutorService executorService) {
    executorService.shutdown();
    try {
      executorService.awaitTermination(60, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }

  private void waitForResponses(BlockingQueue<Future> requestFutures) {
    final ResponseChecker responseChecker = new ResponseChecker(requestFutures);
    responseChecker.start();
    //wait till all instances started
    while (responseChecker.getResponseCount() < dataGeneratorProperties.getWorkflowInstanceCount()) {
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    responseChecker.close();
    logger.info("{} workflow instances started", responseChecker.getResponseCount());
  }

  private BlockingQueue<Future> sendStartWorkflowInstanceCommands(ExecutorService executorService) {
    final BlockingQueue<Future> requestFutures = new ArrayBlockingQueue<>(dataGeneratorProperties.getQueueSize());
    AtomicInteger count = new AtomicInteger(0);
    while (count.incrementAndGet() <= dataGeneratorProperties.getWorkflowInstanceCount()) {
      executorService.submit(() -> {
        try {
          requestFutures.put(ZeebeTestUtil.startWorkflowInstanceAsync(zeebeClient, getRandomBpmnProcessId(), "{\"var1\": \"value1\"}"));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
    }
    return requestFutures;
  }

  private ExecutorService createExecutorService() {
    int numberOfThreads = dataGeneratorProperties.getNumberOfThreads();
    return new ThreadPoolExecutor(numberOfThreads, numberOfThreads, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingQueue<>(Integer.MAX_VALUE));
  }

  @PreDestroy
  public void shutdown(){
    zeebeClient.close();
  }

  private String getRandomBpmnProcessId() {
    return getBpmnProcessId(random.nextInt(dataGeneratorProperties.getWorkflowCount()));
  }

  private void deployWorkflows() {
    for (int i = 0; i< dataGeneratorProperties.getWorkflowCount(); i++) {
      String bpmnProcessId = getBpmnProcessId(i);
      ZeebeTestUtil.deployWorkflow(zeebeClient, createModel(bpmnProcessId), bpmnProcessId + ".bpmn");
      bpmnProcessIds.add(bpmnProcessId);
    }
    logger.info("{} workflows deployed", dataGeneratorProperties.getWorkflowCount());
  }

  private String getBpmnProcessId(int i) {
    return "process" + i;
  }

  private BpmnModelInstance createModel(String bpmnProcessId) {
    return Bpmn.createExecutableProcess(bpmnProcessId)
    .startEvent("start")
      .serviceTask("task1").zeebeTaskType("task1")
        .zeebeInput("var1", "varIn")
        .zeebeOutput("varOut", "var2")
      .serviceTask("task2").zeebeTaskType("task2")
      .serviceTask("task3").zeebeTaskType("task3")
      .serviceTask("task4").zeebeTaskType("task4")
      .serviceTask("task5").zeebeTaskType("task5")
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
          if (responseCount % 100 == 0) {
            logger.info("{} workflow instances started", responseCount);
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

    public void setResponseCount(int responseCount) {
      this.responseCount = responseCount;
    }

    public void close() {
      shuttingDown = true;
      interrupt();
    }
  }
}
