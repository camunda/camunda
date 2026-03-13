/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.BatchModificationCfg;
import io.camunda.zeebe.config.StarterCfg;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.HashMap;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchModificationStarter extends App {

  private static final Logger LOG = LoggerFactory.getLogger(BatchModificationStarter.class);
  private static final Logger THROTTLED_LOGGER = new ThrottledLogger(LOG, Duration.ofSeconds(5));

  private final StarterCfg starterCfg;
  private final BatchModificationCfg batchModificationCfg;

  BatchModificationStarter(final AppCfg config) {
    super(config);
    starterCfg = config.getStarter();
    batchModificationCfg = config.getBatchModification();
  }

  @Override
  public void run() {
    final CamundaClient client = createCamundaClient();

    printTopology(client);
    deployProcess(client);

    final int instanceCount = batchModificationCfg.getInstanceCount();
    LOG.info("Creating {} process instances of '{}'", instanceCount, starterCfg.getProcessId());

    createProcessInstances(client, instanceCount);

    LOG.info(
        "All {} process instances created. Waiting for instances to be available for search.",
        instanceCount);

    waitForInstancesToBeSearchable(client, instanceCount);

    LOG.info(
        "All {} process instances are searchable. Triggering batch modification: move '{}' -> '{}'",
        instanceCount,
        batchModificationCfg.getSourceElement(),
        batchModificationCfg.getTargetElement());

    triggerBatchModification(client);

    LOG.info("Batch modification triggered. BatchModificationStarter finished.");
    client.close();
  }

  private void createProcessInstances(final CamundaClient client, final int instanceCount) {
    final String variablesString = readVariables(starterCfg.getPayloadPath());
    final HashMap<String, Object> baseVariables = deserializeVariables(variablesString);

    final AtomicInteger createdCount = new AtomicInteger(0);
    final CountDownLatch completionLatch = new CountDownLatch(instanceCount);
    final long intervalNanos = Math.floorDiv(Duration.ofSeconds(1).toNanos(), starterCfg.getRate());

    final ScheduledExecutorService executorService =
        Executors.newScheduledThreadPool(starterCfg.getThreads());

    executorService.scheduleAtFixedRate(
        () -> {
          final int count = createdCount.incrementAndGet();
          if (count > instanceCount) {
            return;
          }

          try {
            final var vars = new HashMap<>(baseVariables);
            vars.put(starterCfg.getBusinessKey(), count);

            final CompletionStage<ProcessInstanceEvent> future =
                client
                    .newCreateInstanceCommand()
                    .bpmnProcessId(starterCfg.getProcessId())
                    .latestVersion()
                    .variables(vars)
                    .send();

            future.whenComplete(
                (result, error) -> {
                  if (error != null) {
                    THROTTLED_LOGGER.warn("Error creating process instance #{}", count, error);
                    createdCount.decrementAndGet();
                  }
                  completionLatch.countDown();
                });
          } catch (final Exception e) {
            THROTTLED_LOGGER.error("Error creating process instance #{}", count, e);
            createdCount.decrementAndGet();
            completionLatch.countDown();
          }
        },
        0,
        intervalNanos,
        TimeUnit.NANOSECONDS);

    try {
      completionLatch.await();
    } catch (final InterruptedException e) {
      LOG.error("Interrupted while waiting for process instance creation", e);
      Thread.currentThread().interrupt();
    }

    executorService.shutdown();
    LOG.info("Finished creating {} process instances", instanceCount);
  }

  private void waitForInstancesToBeSearchable(final CamundaClient client, final int expectedCount) {
    while (true) {
      try {
        final var response =
            client
                .newProcessInstanceSearchRequest()
                .filter(
                    f ->
                        f.processDefinitionId(starterCfg.getProcessId())
                            .state(ProcessInstanceState.ACTIVE))
                .page(p -> p.limit(0))
                .send()
                .join();

        final long totalCount = response.page().totalItems();
        if (totalCount >= expectedCount) {
          LOG.info(
              "All {} process instances are now searchable (found {})", expectedCount, totalCount);
          break;
        }

        THROTTLED_LOGGER.info(
            "Waiting for process instances to be searchable: {}/{}", totalCount, expectedCount);
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Error querying process instances, retrying", e);
      }

      try {
        Thread.sleep(5000);
      } catch (final InterruptedException e) {
        LOG.error("Interrupted while waiting for instances to be searchable", e);
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  private void triggerBatchModification(final CamundaClient client) {
    try {
      final var result =
          client
              .newCreateBatchOperationCommand()
              .modifyProcessInstance()
              .addMoveInstruction(
                  batchModificationCfg.getSourceElement(), batchModificationCfg.getTargetElement())
              .filter(f -> f.processDefinitionId(starterCfg.getProcessId()))
              .send()
              .join();

      LOG.info(
          "Batch modification started with batch operation key: {}", result.getBatchOperationKey());
    } catch (final Exception e) {
      LOG.error("Failed to trigger batch modification", e);
    }
  }

  private void deployProcess(final CamundaClient client) {
    while (true) {
      try {
        client
            .newDeployResourceCommand()
            .addResourceFromClasspath(starterCfg.getBpmnXmlPath())
            .send()
            .join();
        LOG.info("Process '{}' deployed successfully", starterCfg.getBpmnXmlPath());
        break;
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to deploy process, retrying", e);
        try {
          Thread.sleep(200);
        } catch (final InterruptedException ex) {
          // ignore
        }
      }
    }
  }

  private CamundaClient createCamundaClient() {
    return newClientBuilder().numJobWorkerExecutionThreads(0).build();
  }

  @SuppressWarnings("unchecked")
  private static HashMap<String, Object> deserializeVariables(final String variablesString) {
    try {
      return new com.fasterxml.jackson.databind.ObjectMapper()
          .readValue(
              variablesString,
              new com.fasterxml.jackson.core.type.TypeReference<HashMap<String, Object>>() {});
    } catch (final com.fasterxml.jackson.core.JsonProcessingException e) {
      LOG.error("Failed to parse variables '{}'.", variablesString, e);
      throw new RuntimeException(e);
    }
  }

  public static void main(final String[] args) {
    createApp(BatchModificationStarter::new);
  }
}
