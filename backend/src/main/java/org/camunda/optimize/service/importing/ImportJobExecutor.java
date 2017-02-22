package org.camunda.optimize.service.importing;

import org.camunda.optimize.service.importing.job.ImportJob;
import org.camunda.optimize.service.util.ConfigurationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;

@Component
public class ImportJobExecutor implements Runnable {

  @Autowired
  private ConfigurationService configurationService;

  private Logger logger = LoggerFactory.getLogger(ImportJobExecutor.class);

  private Phaser phaser;
  private BlockingQueue<ImportJob> importJobsQueue;
  private ExecutorService executor;

  @PostConstruct
  public void init() {
    importJobsQueue = new ArrayBlockingQueue<>(configurationService.getMaxJobQueueSize());
    phaser = new Phaser();
    executor = Executors.newFixedThreadPool(configurationService.getImportExecutorThreadCount());
    for ( int i=0; i<configurationService.getImportExecutorThreadCount(); i++) {
      executor.execute(this);
    }
  }

  public void addNewImportJob(ImportJob importJob) throws InterruptedException {
    importJobsQueue.put(importJob);
  }

  public void run() {
    phaser.register();
    try {
      while (true) {
        notifyIfFinished();
        ImportJob importJob = importJobsQueue.take();
        importJob.fetchMissingEntityInformation();
        importJob.executeImport();
      }
    } catch (InterruptedException e) {
      logger.error("Import job executor was interrupted while job import!", e);
      Thread.currentThread().interrupt();
    }
  }

  private void notifyIfFinished() {
    boolean isFinished = importJobsQueue.isEmpty();
    if(isFinished) {
      phaser.arrive();
    }
  }

  public void waitUntilExecutorIsIdle() {
    phaser.register();
    phaser.awaitAdvance(phaser.arriveAndDeregister());
  }

}
