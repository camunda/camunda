package org.camunda.optimize.data.generation;

import org.camunda.optimize.data.generation.generators.DataGenerator;
import org.camunda.optimize.data.generation.generators.impl.AuthorizationArrangementDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ChangeContactDataDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ContactInterviewDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.DocumentCheckHandlingDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ExportInsuranceDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ExtendedOrderDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.HiringProcessDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.InvoiceDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.LeadQualificationDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.MultiParallelDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.OrderConfirmationDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.PickUpHandlingDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ProcessRequestDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.ReviewCaseDataGenerator;
import org.camunda.optimize.data.generation.generators.impl.SimpleServiceTaskDataGenerator;
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
  private SimpleEngineClient engineClient;

  private List<DataGenerator> dataGenerators;
  private ThreadPoolExecutor importExecutor;
  private BlockingQueue<Runnable> importJobsQueue;

  private ScheduledExecutorService progressReporter;

  DataGenerationExecutor(long totalInstanceCount, String engineRestEndpoint) {
    this.totalInstanceCount = totalInstanceCount;
    this.engineRestEndpoint = engineRestEndpoint;
  }

  private void init() {
    final int queueSize = 100;
    importJobsQueue = new ArrayBlockingQueue<>(queueSize);
    importExecutor = new ThreadPoolExecutor(
      3, 20, Long.MAX_VALUE, TimeUnit.DAYS, importJobsQueue, new WaitHandler());

    engineClient = new SimpleEngineClient(engineRestEndpoint);
    initGenerators(engineClient);
  }

  private void initGenerators(SimpleEngineClient engineClient) {
    dataGenerators = new ArrayList<>();
    dataGenerators.add(new HiringProcessDataGenerator(engineClient));
    dataGenerators.add(new ExtendedOrderDataGenerator(engineClient));
    dataGenerators.add(new ContactInterviewDataGenerator(engineClient));
    dataGenerators.add(new SimpleServiceTaskDataGenerator(engineClient, "SimpleServiceTaskProcess1"));
    dataGenerators.add(new SimpleServiceTaskDataGenerator(engineClient, "SimpleServiceTaskProcess2"));
    dataGenerators.add(new SimpleServiceTaskDataGenerator(engineClient, "SimpleServiceTaskProcess3"));
    dataGenerators.add(new SimpleServiceTaskDataGenerator(engineClient, "SimpleServiceTaskProcess4"));
    dataGenerators.add(new SimpleServiceTaskDataGenerator(engineClient, "SimpleServiceTaskProcess5"));
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
    for (DataGenerator dataGenerator : getDataGenerators()) {
      importExecutor.execute(dataGenerator);
    }
    progressReporter = reportDataGenerationProgress(importJobsQueue);
  }

  public void awaitDataGenerationTermination() throws InterruptedException {
    importExecutor.shutdown();
    importExecutor.awaitTermination(16L, TimeUnit.HOURS);
    if (progressReporter != null) {
      stopReportingProgress(progressReporter);
    }
    engineClient.close();
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
