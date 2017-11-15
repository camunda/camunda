package org.camunda.optimize.test.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class SynchronizationElasticsearchImportJob implements Runnable {

  private final CountDownLatch countDownLatch;

  private Logger logger = LoggerFactory.getLogger(getClass());

  public SynchronizationElasticsearchImportJob(CountDownLatch countDownLatch) {
    this.countDownLatch = countDownLatch;
  }

  @Override
  public void run() {
    logger.debug("Synchronization job was successfully executed. Counting down latch.");
    countDownLatch.countDown();
  }
}
