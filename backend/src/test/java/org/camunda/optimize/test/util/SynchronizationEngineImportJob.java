package org.camunda.optimize.test.util;

import org.camunda.optimize.service.es.ElasticsearchImportJobExecutor;

import java.util.concurrent.CountDownLatch;

public class SynchronizationEngineImportJob implements Runnable {

  private ElasticsearchImportJobExecutor elasticsearchImportJobExecutor;
  private CountDownLatch countDownLatch;

  public SynchronizationEngineImportJob(ElasticsearchImportJobExecutor elasticsearchImportJobExecutor,
                                        CountDownLatch countDownLatch) {
    this.elasticsearchImportJobExecutor = elasticsearchImportJobExecutor;
    this.countDownLatch = countDownLatch;
  }

  @Override
  public void run() {
    SynchronizationElasticsearchImportJob importJob =
      new SynchronizationElasticsearchImportJob(countDownLatch);
    try {
      elasticsearchImportJobExecutor.executeImportJob(importJob);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
  }
}
