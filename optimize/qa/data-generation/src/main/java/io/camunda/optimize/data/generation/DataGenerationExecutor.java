/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.data.generation;

import static java.util.stream.Collectors.toList;

import io.camunda.optimize.data.generation.generators.DataGenerator;
import io.camunda.optimize.data.generation.generators.dto.DataGenerationInformation;
import io.camunda.optimize.test.util.client.SimpleEngineClient;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ClassUtils;

@Slf4j
public class DataGenerationExecutor {

  private final DataGenerationInformation dataGenerationInformation;

  private SimpleEngineClient engineClient;

  private final List<DataGenerator<?>> allDataGenerators = new ArrayList<>();
  private ThreadPoolExecutor importExecutor;

  private ScheduledExecutorService progressReporter;
  private UserAndGroupGenerator userAndGroupGenerator;

  public DataGenerationExecutor(final DataGenerationInformation dataGenerationInformation) {
    this.dataGenerationInformation = dataGenerationInformation;
    init();
  }

  private void init() {
    final int queueSize = 100;
    final BlockingQueue<Runnable> importJobsQueue = new ArrayBlockingQueue<>(queueSize);
    importExecutor =
        new ThreadPoolExecutor(
            1, 1, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue, new WaitHandler());

    engineClient = new SimpleEngineClient(dataGenerationInformation.getEngineRestEndpoint());
    engineClient.initializeStandardUserAndGroupAuthorizations();

    if (dataGenerationInformation.isRemoveDeployments()) {
      engineClient.cleanUpDeployments();
    }
    initGenerators();
  }

  private void initGenerators() {
    userAndGroupGenerator = new UserAndGroupGenerator(engineClient);
    final List<DataGenerator<?>> processDataGenerators =
        createGenerators(
            dataGenerationInformation.getProcessDefinitionsAndNumberOfVersions(),
            dataGenerationInformation.getProcessInstanceCountToGenerate());
    log.info(
        "Created the following process instance generators: {}",
        processDataGenerators.stream()
            .map(Object::getClass)
            .map(Class::getSimpleName)
            .collect(toList()));
    final List<DataGenerator<?>> decisionDataGenerators =
        createGenerators(
            dataGenerationInformation.getDecisionDefinitionsAndNumberOfVersions(),
            dataGenerationInformation.getDecisionInstanceCountToGenerate());
    log.info(
        "Created the following decision instance generators: {}",
        decisionDataGenerators.stream()
            .map(Object::getClass)
            .map(Class::getSimpleName)
            .collect(toList()));
    allDataGenerators.addAll(processDataGenerators);
    allDataGenerators.addAll(decisionDataGenerators);
  }

  private List<DataGenerator<?>> createGenerators(
      final Map<String, Integer> definitions, final Long instanceCountToGenerate) {
    final List<DataGenerator<?>> dataGenerators = new ArrayList<>();
    try (final ScanResult scanResult =
        new ClassGraph()
            .enableClassInfo()
            .acceptPackages(DataGenerator.class.getPackage().getName())
            .scan()) {
      scanResult.getSubclasses(DataGenerator.class.getName()).stream()
          .filter(g -> definitions.containsKey(g.getSimpleName()))
          .forEach(s -> createAndAddGeneratorInstance(definitions, dataGenerators, s));
    }
    addInstanceCountToGenerators(dataGenerators, instanceCountToGenerate);
    return dataGenerators;
  }

  @SneakyThrows
  private void createAndAddGeneratorInstance(
      final Map<String, Integer> definitions,
      final List<DataGenerator<?>> dataGenerators,
      final ClassInfo s) {
    if (ClassUtils.hasConstructor(
        s.loadClass(), SimpleEngineClient.class, Integer.class, UserAndGroupProvider.class)) {
      dataGenerators.add(
          (DataGenerator<?>)
              s.loadClass()
                  .getConstructor(
                      SimpleEngineClient.class, Integer.class, UserAndGroupProvider.class)
                  .newInstance(
                      engineClient, definitions.get(s.getSimpleName()), userAndGroupGenerator));
    } else {
      dataGenerators.add(
          (DataGenerator<?>)
              s.loadClass()
                  .getConstructor(SimpleEngineClient.class, Integer.class)
                  .newInstance(engineClient, definitions.get(s.getSimpleName())));
    }
  }

  private void addInstanceCountToGenerators(
      final List<DataGenerator<?>> dataGenerators, final Long instanceCountToGenerate) {
    final int nGenerators = dataGenerators.size();
    final int stepSize = instanceCountToGenerate.intValue() / nGenerators;
    final int missingCount = instanceCountToGenerate.intValue() % nGenerators;
    dataGenerators.forEach(generator -> generator.setInstanceCountToGenerate(stepSize));
    dataGenerators.get(0).addToInstanceCount(missingCount);
  }

  public void executeDataGeneration() {
    userAndGroupGenerator.generateGroups();
    userAndGroupGenerator.generateUsers();
    for (final DataGenerator<?> dataGenerator : allDataGenerators) {
      importExecutor.execute(dataGenerator);
    }
    progressReporter = reportDataGenerationProgress();
  }

  public void awaitDataGenerationTermination() {
    importExecutor.shutdown();
    try {
      final boolean finishedGeneration =
          importExecutor.awaitTermination(Integer.MAX_VALUE, TimeUnit.HOURS);
      if (!finishedGeneration) {
        log.error("Could not finish data generation in time. Trying to interrupt!");
        importExecutor.shutdownNow();
      }
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      log.error("Data generation has been interrupted!", e);
    } finally {
      if (progressReporter != null) {
        stopReportingProgress(progressReporter);
      }
    }
  }

  private void stopReportingProgress(final ScheduledExecutorService exec) {
    exec.shutdownNow();
  }

  private ScheduledExecutorService reportDataGenerationProgress() {
    final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    final Runnable reportFunc =
        () -> {
          final RuntimeMXBean rb = ManagementFactory.getRuntimeMXBean();
          log.info(
              "Progress report for running data generators (total: {} generators)",
              allDataGenerators.size());
          int totalInstancesToGenerate = 0;
          int finishedInstances = 0;
          for (final DataGenerator<?> dataGenerator : allDataGenerators) {
            totalInstancesToGenerate += dataGenerator.getInstanceCountToGenerate();
            finishedInstances += dataGenerator.getStartedInstanceCount();
            if (dataGenerator.getStartedInstanceCount() > 0
                && dataGenerator.getInstanceCountToGenerate()
                != dataGenerator.getStartedInstanceCount()) {
              log.info(
                  "[{}/{}] {}",
                  dataGenerator.getStartedInstanceCount(),
                  dataGenerator.getInstanceCountToGenerate(),
                  dataGenerator.getClass().getSimpleName().replaceAll("DataGenerator", ""));
            }
          }
          final double finishedAmountInPercentage =
              Math.round((double) finishedInstances / (double) totalInstancesToGenerate * 1000.0)
                  / 10.0;
          final long timeETAFromNow =
              Math.round(
                  ((double) rb.getUptime() / finishedAmountInPercentage)
                      * (100.0 - finishedAmountInPercentage));
          final Date timeETA = new Date(System.currentTimeMillis() + timeETAFromNow);
          log.info(
              "Overall data generation progress: {}% ({}/{}) ETA: {}",
              finishedAmountInPercentage, finishedInstances, totalInstancesToGenerate, timeETA);
        };

    exec.scheduleAtFixedRate(reportFunc, 0, 30, TimeUnit.SECONDS);
    reportFunc.run();
    return exec;
  }

  private static final class WaitHandler implements RejectedExecutionHandler {

    @Override
    public void rejectedExecution(final Runnable r, final ThreadPoolExecutor executor) {
      try {
        executor.getQueue().put(r);
      } catch (final InterruptedException e) {
        log.error("interrupted generation", e);
      }
    }
  }
}
