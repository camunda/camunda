/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.data.generation;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CompleteJobCommandStep1;
import io.camunda.zeebe.client.api.command.DeployResourceCommandStep1;
import io.camunda.zeebe.client.api.worker.JobWorker;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

@Slf4j
@Component
public class DataGenerator {

  private static final String TASK_JOB_TYPE = "taskToComplete";
  private final AtomicInteger completedTaskCount = new AtomicInteger(0);

  private final ZeebeClient zeebeClient;

  private final ThreadPoolTaskExecutor dataGeneratorTaskExecutor;
  private Integer instanceCount;
  private Integer definitionCount;

  public DataGenerator(final ZeebeClient zeebeClient,
                       @Qualifier("dataGeneratorThreadPoolExecutor") final ThreadPoolTaskExecutor dataGeneratorTaskExecutor,
                       @Value("${DATA_INSTANCE_COUNT:1000000}") final Integer instanceCount,
                       @Value("${DATA_PROCESS_DEFINITION_COUNT:100}") final Integer definitionCount) {
    this.zeebeClient = zeebeClient;
    this.dataGeneratorTaskExecutor = dataGeneratorTaskExecutor;
    this.instanceCount = instanceCount;
    this.definitionCount = definitionCount;
  }

  public void createData() {
    final OffsetDateTime dataGenerationStart = OffsetDateTime.now();
    log.info("Starting to generate Zeebe data...");

    final JobWorker taskCompletionWorker = startTaskCompletionWorker();
    deployProcessesAndStartInstances();
    waitForTaskCompletionWorker(taskCompletionWorker);

    log.info(
      "Data generation completed in: {} minutes.",
      ChronoUnit.MINUTES.between(dataGenerationStart, OffsetDateTime.now())
    );

    // Wait a few more minutes to ensure zeebe record export has finished
    try {
      log.info("Waiting 5 mins for zeebe record export..");
      Thread.sleep(300000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @PreDestroy
  public void shutdown() {
    zeebeClient.close();
    dataGeneratorTaskExecutor.shutdown();
  }

  private JobWorker startTaskCompletionWorker() {
    log.info("Starting task completion worker...");
    return zeebeClient.newWorker()
      .jobType(TASK_JOB_TYPE)
      .handler((jobClient, job) -> {
        try {
          CompleteJobCommandStep1 completeJobCommandStep1 = jobClient
            .newCompleteCommand(job.getKey());
          completeJobCommandStep1.send().join();
          log.debug("Task completed jobKey [{}]", job.getKey());
          completedTaskCount.incrementAndGet();
          if (completedTaskCount.get() % 1000 == 0) {
            log.info("{} jobs with jobkey [{}] completed.", completedTaskCount.get(), job.getKey());
          }
        } catch (Exception ex) {
          log.error(ex.getMessage(), ex);
          throw ex;
        }
      })
      .name("zeebe-data-generator")
      .timeout(Duration.ofSeconds(2))
      .open();
  }

  private void waitForTaskCompletionWorker(final JobWorker taskCompletionWorker) {
    log.info("Waiting for serviceTasks to be completed..");
    while (completedTaskCount.get() < instanceCount) { // one task per model needs completed
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    taskCompletionWorker.close();
  }

  private void deployProcessesAndStartInstances() {
    final Map<String, Integer> instanceCountsByDef = calculateInstanceCountsPerDefinition();

    instanceCountsByDef.keySet().forEach(this::deployProcess);

    instanceCountsByDef.forEach((processId, instCount) -> {
      InstancesStarter instancesStarter = new InstancesStarter(processId, instCount);
      dataGeneratorTaskExecutor.submit(instancesStarter);
    });

    // wait for all instances to be started
    dataGeneratorTaskExecutor.setAwaitTerminationMillis(Integer.MAX_VALUE);
    dataGeneratorTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
    dataGeneratorTaskExecutor.shutdown();
  }

  private void deployProcess(final String processId) {
    log.info("Deploying process with ID [{}]", processId);
    try {
      DeployResourceCommandStep1 deployProcessCommandStep1 = zeebeClient.newDeployResourceCommand();
      deployProcessCommandStep1.addProcessModel(createModel(processId), processId + ".bpmn");
      ((DeployResourceCommandStep1.DeployResourceCommandStep2) deployProcessCommandStep1)
        .send()
        .join(2, TimeUnit.SECONDS);
    } catch (Exception e) {
      log.error("Error while deploying process with ID [{}]", processId, e);
    }
    log.info("Finished deploying process with ID [{}]", processId);
  }

  private BpmnModelInstance createModel(final String processId) {
    // @formatter:off
    return Bpmn.createExecutableProcess(processId)
      .startEvent("start")
      .serviceTask("task1")
        .zeebeJobType(TASK_JOB_TYPE)
      .endEvent()
      .done();
    // @formatter:on
  }

  private Map<String, Integer> calculateInstanceCountsPerDefinition() {
    final Map<String, Integer> instanceCountsByDef = new HashMap<>();
    final int instanceCountPerDef = instanceCount / definitionCount;
    final int leftOverInstCount = instanceCount % definitionCount;
    IntStream.range(0, definitionCount)
      .forEach(i -> instanceCountsByDef.put("defKey-" + i, instanceCountPerDef));
    instanceCountsByDef.put("defKey-0", instanceCountsByDef.get("defKey-0") + leftOverInstCount);
    return instanceCountsByDef;
  }

  class InstancesStarter implements Runnable {

    private boolean shuttingDown = false;
    private final String processId;
    private final int instanceCount;

    public InstancesStarter(final String processId, final int instanceCount) {
      this.processId = processId;
      this.instanceCount = instanceCount;
    }

    @Override
    public void run() {
      while (!shuttingDown) {
        log.info("Deploying [{}] instances for process [{}]", instanceCount, processId);
        IntStream.range(0, instanceCount).forEach(i -> startProcessInstance());
        log.info("Finished deploying [{}] instances for process [{}]", instanceCount, processId);
        close();
      }
    }

    public void close() {
      shuttingDown = true;
    }

    private void startProcessInstance() {
      try {
        resolveZeebeClient()
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .variables(createVariables())
          .send()
          .join();
      } catch (Exception e) {
        log.error("Error while starting instances for process with ID [{}]", processId, e);
      }
    }

    private Map<String, Object> createVariables() {
      Map<String, Object> variables = new HashMap<>();
      variables.put("person", createObjectVar());
      variables.put("stringVar", "aStringValue");
      variables.put("boolVar", RandomUtils.nextBoolean());
      variables.put("integerVar", RandomUtils.nextInt());
      variables.put("shortVar", (short) RandomUtils.nextInt());
      variables.put("longVar", RandomUtils.nextLong());
      variables.put("doubleVar", RandomUtils.nextDouble());
      variables.put("dateVar", new Date(RandomUtils.nextInt()));
      variables.put(
        "listVar",
        Map.of("listVar", List.of(RandomUtils.nextInt(0, 100), -RandomUtils.nextInt(0, 100)))
      );
      return variables;
    }

    private Map<String, Object> createObjectVar() {
      final Map<String, Object> objectVar = new HashMap<>();
      objectVar.put("prop1", "value1");
      objectVar.put("prop2", 28);
      objectVar.put("prop3", List.of("aValue", "anotherValue"));
      return objectVar;
    }

    private ZeebeClient resolveZeebeClient() {
      return ((DataGenerationConfig.DataGeneratorThread) Thread.currentThread()).getZeebeClient();
    }

  }
}
