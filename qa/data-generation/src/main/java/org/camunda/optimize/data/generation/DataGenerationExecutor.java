/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ScanResult;
import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class DataGenerationExecutor {

  private Logger logger = LoggerFactory.getLogger(getClass());

  private Long totalInstanceCount;
  private String engineRestEndpoint;
  private boolean removeDeployments;
  private SimpleEngineClient engineClient;
  private HashMap<String, Integer> definitions;


  private List<DataGenerator> dataGenerators;
  private ThreadPoolExecutor importExecutor;
  private BlockingQueue<Runnable> importJobsQueue;

  private ScheduledExecutorService progressReporter;

  public DataGenerationExecutor(final long totalInstanceCount,
                                final String engineRestEndpoint,
                                final boolean removeDeployments,
                                HashMap<String, Integer> definitions) {
    this.totalInstanceCount = totalInstanceCount;
    this.engineRestEndpoint = engineRestEndpoint;
    this.removeDeployments = removeDeployments;
    this.definitions = definitions;
  }

  private void init() {
    final int queueSize = 100;
    importJobsQueue = new ArrayBlockingQueue<>(queueSize);
    importExecutor = new ThreadPoolExecutor(
      1, 1, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue, new WaitHandler());

    engineClient = new SimpleEngineClient(engineRestEndpoint);
    engineClient.initializeStandardUserAuthorizations();

    if (this.removeDeployments) {
      engineClient.cleanUpDeployments();
    }
    initGenerators(engineClient);
  }

  private void initGenerators(SimpleEngineClient engineClient) {
    dataGenerators = new ArrayList<>();
    try (ScanResult scanResult = new ClassGraph()
      .enableClassInfo()
      .whitelistPackages(DataGenerator.class.getPackage().getName())
      .scan()) {
      scanResult.getSubclasses(DataGenerator.class.getName()).stream()
        .filter(g -> definitions.keySet().contains(g.getSimpleName()))
        .forEach(s -> {
        try {
          dataGenerators.add((DataGenerator) s.loadClass()
            .getConstructor(SimpleEngineClient.class, Integer.class)
            .newInstance(engineClient, definitions.get(s.getSimpleName())));
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    }
    setInstanceNumberToGenerateForEachGenerator();
  }

  private void setInstanceNumberToGenerateForEachGenerator() {
    int nGenerators = dataGenerators.size();
    int stepSize = totalInstanceCount.intValue() / nGenerators;
    int missingCount = totalInstanceCount.intValue() % nGenerators;
    dataGenerators.forEach(
      generator -> generator.setInstanceCountToGenerate(stepSize)
    );
    dataGenerators.get(0).addToInstanceCount(missingCount);
  }

  public void executeDataGeneration() {
    init();
    for (DataGenerator dataGenerator : getDataGenerators()) {
      importExecutor.execute(dataGenerator);
    }
    progressReporter = reportDataGenerationProgress(importJobsQueue);
  }

  public void awaitDataGenerationTermination() {
    importExecutor.shutdown();
    try {
      boolean finishedGeneration = importExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.HOURS);

      if (!finishedGeneration) {
        logger.error("Could not finish data generation in time. Trying to interrupt!");
        importExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Data generation has been interrupted!", e);
    } finally {
      if (progressReporter != null) {
        stopReportingProgress(progressReporter);
      }
      engineClient.close();
    }
  }

  private void stopReportingProgress(ScheduledExecutorService exec) {
    exec.shutdownNow();
  }

  private ScheduledExecutorService reportDataGenerationProgress(BlockingQueue<Runnable> importJobsQueue) {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    Runnable reportFunc = () -> {
      RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
      logger.info("Progress report for running data generators (total: {} generators)", getTotalDataGeneratorCount());
      int totalInstancesToGenerate = 0;
      int finishedInstances = 0;
      for (DataGenerator dataGenerator : getDataGenerators()) {
        totalInstancesToGenerate += dataGenerator.getInstanceCountToGenerate();
        finishedInstances += dataGenerator.getStartedInstanceCount();
        if (dataGenerator.getStartedInstanceCount() > 0
          && dataGenerator.getInstanceCountToGenerate() != dataGenerator.getStartedInstanceCount()) {
          logger.info(
            "[{}/{}] {}",
            dataGenerator.getStartedInstanceCount(),
            dataGenerator.getInstanceCountToGenerate(),
            dataGenerator.getClass().getSimpleName().replaceAll("DataGenerator", "")
          );
        }
      }
      double finishedAmountInPercentage =
        Math.round((double) finishedInstances / (double) totalInstancesToGenerate * 1000.0) / 10.0;
      long timeETAFromNow =
        Math.round(((double) rb.getUptime() / finishedAmountInPercentage) * (100.0 - finishedAmountInPercentage));
      Date timeETA = new Date(System.currentTimeMillis() + timeETAFromNow);
      logger.info(
        "Overall data generation progress: {}% ({}/{}) ETA: {}",
        finishedAmountInPercentage,
        finishedInstances,
        totalInstancesToGenerate,
        timeETA
      );
    };

    exec.scheduleAtFixedRate(reportFunc, 0, 30, TimeUnit.SECONDS);
    reportFunc.run();
    return exec;
  }

  private class WaitHandler implements RejectedExecutionHandler {
    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
      try {
        executor.getQueue().put(r);
      } catch (InterruptedException e) {
        logger.error("interrupted generation", e);
      }
    }

  }

  private int getTotalDataGeneratorCount() {
    return dataGenerators.size();
  }

  private List<DataGenerator> getDataGenerators() {
    return dataGenerators;
  }
}
