/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.data.generation;

import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.UserTaskCompleter;
import org.camunda.optimize.data.generation.generators.client.SimpleEngineClient;
import org.camunda.optimize.data.generation.generators.impl.AuthorizationArrangementDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.BookRequestDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.BranchAnalysisDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ChangeContactDataDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ContactInterviewDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.DmnTableDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.DocumentCheckHandlingDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.EmbeddedSubprocessRequestDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ExportInsuranceDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ExtendedOrderDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.HiringProcessDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.InvoiceDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.LeadQualificationDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.MultiInstanceSubprocessRequestDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.MultiParallelDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.OrderConfirmationDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.PickUpHandlingDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ProcessRequestDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ReviewCaseDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.TransshipmentArrangementDataGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
  private long timeoutInHours;
  private boolean removeDeployments;
  private SimpleEngineClient engineClient;

  private List<DataGenerator> dataGenerators;
  private ThreadPoolExecutor importExecutor;
  private BlockingQueue<Runnable> importJobsQueue;

  private ScheduledExecutorService progressReporter;
  private UserTaskCompleter completer;

  public DataGenerationExecutor(long totalInstanceCount, String engineRestEndpoint, long timeoutInHours,
                                boolean removeDeployments) {
    this.totalInstanceCount = totalInstanceCount;
    this.engineRestEndpoint = engineRestEndpoint;
    this.timeoutInHours = timeoutInHours;
    this.removeDeployments = removeDeployments;
  }

  private void init() {
    final int queueSize = 100;
    importJobsQueue = new ArrayBlockingQueue<>(queueSize);
    importExecutor = new ThreadPoolExecutor(
      3, 20, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue, new WaitHandler());

    engineClient = new SimpleEngineClient(engineRestEndpoint);
    if (this.removeDeployments) {
      engineClient.cleanUpDeployments();
    }
    initGenerators(engineClient);
  }

  private void initGenerators(SimpleEngineClient engineClient) {
    dataGenerators = new ArrayList<>();
    dataGenerators.add(new BranchAnalysisDataGenerator(engineClient));
    dataGenerators.add(new BookRequestDataGenerator(engineClient));
    dataGenerators.add(new EmbeddedSubprocessRequestDataGenerator(engineClient));
    dataGenerators.add(new MultiInstanceSubprocessRequestDataGenerator(engineClient));
    dataGenerators.add(new HiringProcessDataGenerator(engineClient));
    dataGenerators.add(new ExtendedOrderDataGenerator(engineClient));
    dataGenerators.add(new ContactInterviewDataGenerator(engineClient));
    dataGenerators.add(new DmnTableDataGenerator(engineClient));
    dataGenerators.add(new LeadQualificationDataGenerator(engineClient));
    dataGenerators.add(new InvoiceDataGenerator(engineClient));
    dataGenerators.add(new OrderConfirmationDataGenerator(engineClient));
    dataGenerators.add(new MultiParallelDataGenerator(engineClient));
    dataGenerators.add(new TransshipmentArrangementDataGenerator(engineClient));
    dataGenerators.add(new PickUpHandlingDataGenerator(engineClient));
    dataGenerators.add(new AuthorizationArrangementDataGenerator(engineClient));
    dataGenerators.add(new ChangeContactDataDataGenerator(engineClient));
    dataGenerators.add(new ProcessRequestDataGenerator(engineClient));
    dataGenerators.add(new ExportInsuranceDataGenerator(engineClient));
    dataGenerators.add(new DocumentCheckHandlingDataGenerator(engineClient));
    dataGenerators.add(new ReviewCaseDataGenerator(engineClient));
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
    completer = new UserTaskCompleter(engineClient);
    completer.startUserTaskCompletion();
    for (DataGenerator dataGenerator : getDataGenerators()) {
      importExecutor.execute(dataGenerator);
    }
    progressReporter = reportDataGenerationProgress(importJobsQueue);
  }

  public void awaitDataGenerationTermination() {
    importExecutor.shutdown();
    try {
      boolean finishedGeneration =
        importExecutor.awaitTermination(timeoutInHours, TimeUnit.HOURS);
      if (!finishedGeneration) {
        logger.error("Could not finish data generation in time. Trying to interrupt!");
        importExecutor.shutdownNow();
      }
    } catch(InterruptedException e) {
      Thread.currentThread().interrupt();
      logger.error("Data generation has been interrupted!", e);
    } finally {
      if (progressReporter != null) {
        stopReportingProgress(progressReporter);
      }
      completer.stopUserTaskCompletion();
      engineClient.close();
    }
  }

  private void stopReportingProgress(ScheduledExecutorService exec) {
    exec.shutdownNow();
  }

  private ScheduledExecutorService reportDataGenerationProgress(BlockingQueue<Runnable> importJobsQueue) {
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
    Integer nGenerators = getTotalDataGeneratorCount();
    exec.scheduleAtFixedRate(() -> {
      Integer finishedCount = (nGenerators - importJobsQueue.size());
      double finishedAmountInPercentage = Math.round((finishedCount.doubleValue() / nGenerators.doubleValue() * 100.0));
      logger.info("Progress of data generation: {}%", finishedAmountInPercentage);
    }, 0, 5, TimeUnit.SECONDS);
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
