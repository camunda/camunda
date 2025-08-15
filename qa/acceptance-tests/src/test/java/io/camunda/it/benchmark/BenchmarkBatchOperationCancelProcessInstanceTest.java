/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.benchmark;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.getScopedVariables;
import static io.camunda.it.util.TestHelper.startScopedProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.BatchOperationState;
import io.camunda.client.api.search.response.BatchOperation;
import io.camunda.client.impl.CamundaClientBuilderImpl;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This benchmark tests the performance of canceling a large number of process instances using a
 * batch operation. It expects a running camunda standalone instance on the local machine with the
 * default port configuration.
 *
 * <p>It deploys a process definition, starts a specified number of process instances, and then
 * initiates a batch operation to cancel all those instances. The benchmark measures the time taken
 * to create the batch operation, export chunks, and the total duration.
 */
@Disabled("only for manual local execution")
public class BenchmarkBatchOperationCancelProcessInstanceTest {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(BenchmarkBatchOperationCancelProcessInstanceTest.class);

  private final CamundaClient camundaClient = new CamundaClientBuilderImpl().usePlaintext().build();

  @Test
  void shouldCancelProcessInstancesWithBatch() throws Exception {
    final int numberOfProcesses = 10000; // Number of processes to deploy
    final int numberOfRuns = 3; // Number of runs to execute

    deployResource(camundaClient, "process/service_tasks_v1.bpmn");
    waitForProcessesToBeDeployed(camundaClient, 1);

    LOGGER.info(
        "Starting benchmark with {} processes and {} runs", numberOfProcesses, numberOfRuns);

    final List<BenchmarkResult> results = new ArrayList<>();

    for (int i = 0; i < numberOfRuns; i++) {
      LOGGER.info("Starting run {} of {}", i + 1, numberOfRuns);
      results.add(runSingleBenchmarkRun(numberOfProcesses));
      LOGGER.info("Finished run {} of {}", i + 1, numberOfRuns);
    }

    LOGGER.info("Benchmark completed successfully");
    LOGGER.info("======== Results ========");
    for (int i = 0; i < results.size(); i++) {
      final var result = results.get(i);
      LOGGER.info(
          "Run {}: Create BO Duration: {} ms, Export Chunks Duration: {} ms, Total Duration: {} ms",
          i + 1,
          result.createBoDuration(),
          result.exportChunksDuration(),
          result.totalDuration());
    }
  }

  private BenchmarkResult runSingleBenchmarkRun(final int numProcessInstances) throws Exception {
    final var testScopeId = UUID.randomUUID().toString();
    // given

    LOGGER.info("Creating {} processes for benchmark", numProcessInstances);
    for (int i = 0; i < numProcessInstances; i++) {
      startScopedProcessInstance(
          camundaClient,
          "service_tasks_v1",
          testScopeId,
          Map.of("xyz", "bar" + i, "path", "111" + i));
      if (i % 1000 == 0) {
        LOGGER.info("Created {} processes for benchmark", i);
      }
    }
    LOGGER.info("Created {} processes for benchmark", numProcessInstances);
    LOGGER.info("Sleep for 5 seconds to give the exporter some time to catch up");
    Thread.sleep(5000);

    // when

    LOGGER.info("=========================================================");
    LOGGER.info("Now starting new batch operation to cancel all process instances");

    final var startTime = System.currentTimeMillis();

    LOGGER.info("=========================================================");
    final var batchOperationKey =
        camundaClient
            .newCreateBatchOperationCommand()
            .processInstanceCancel()
            .filter(b -> b.variables(getScopedVariables(testScopeId)))
            .send()
            .join()
            .getBatchOperationKey();

    LOGGER.info("Batch operation started with key {}", batchOperationKey);

    var createBatchOperationEndTime = 0L;
    var exportChunksEndTime = 0L;
    while (true) {
      final var batchOperation = findBatchOperation(batchOperationKey);

      if (batchOperation == null) {
        Thread.sleep(1000);
        continue;
      }

      if (createBatchOperationEndTime == 0L) {
        createBatchOperationEndTime = System.currentTimeMillis();
      }

      if ((batchOperation.getOperationsCompletedCount()) > 0 && exportChunksEndTime == 0L) {
        exportChunksEndTime = System.currentTimeMillis();
      }

      if (batchOperation.getStatus() == BatchOperationState.COMPLETED) {
        LOGGER.info("Batch operation {} has been completed", batchOperationKey);
        break;
      } else {
        LOGGER.info(
            "Batch operation {} is still running - {} of {} items completed",
            batchOperationKey,
            batchOperation.getOperationsCompletedCount(),
            batchOperation.getOperationsTotalCount());
      }

      Thread.sleep(1000);
    }

    final var endTime = System.currentTimeMillis();
    if (exportChunksEndTime == 0L) {
      exportChunksEndTime = endTime;
    }

    LOGGER.info("Batch operation {} completed in {} ms", batchOperationKey, endTime - startTime);

    return new BenchmarkResult(
        String.valueOf(batchOperationKey),
        numProcessInstances,
        createBatchOperationEndTime - startTime,
        exportChunksEndTime - createBatchOperationEndTime,
        endTime - startTime);
  }

  private BatchOperation findBatchOperation(final String batchOperationKey) {
    return camundaClient
        .newBatchOperationSearchRequest()
        .filter(f -> f.batchOperationKey(batchOperationKey))
        .send()
        .join()
        .items()
        .stream()
        .findFirst()
        .orElse(null);
  }

  record BenchmarkResult(
      String batchOperationKey,
      int numberOfInstances,
      long createBoDuration,
      long exportChunksDuration,
      long totalDuration) {}
}
