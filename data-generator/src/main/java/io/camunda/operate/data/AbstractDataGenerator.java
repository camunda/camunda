/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.data;

import io.camunda.operate.data.usertest.UserTestDataGenerator;
import io.camunda.operate.store.ZeebeStore;
import io.camunda.zeebe.client.api.worker.JobWorker;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import io.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import io.camunda.zeebe.client.ZeebeClient;
import static io.camunda.operate.util.ThreadUtil.sleepFor;

public abstract class AbstractDataGenerator implements DataGenerator {

  private static final Logger logger = LoggerFactory.getLogger(AbstractDataGenerator.class);

  private boolean shutdown = false;

  @Autowired
  protected ZeebeClient client;

  @Autowired
  private ZeebeStore zeebeStore;

  @Autowired
  private OperateProperties operateProperties;

  protected boolean manuallyCalled = false;

  protected ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);

  @PreDestroy
  public void shutdown() {
    logger.info("Shutdown DataGenerator");
    shutdown = true;
    if(scheduler!=null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
      }
    }
  }

  @Override
  public void createZeebeDataAsync(boolean manuallyCalled) {
    scheduler.execute(() -> {
      Boolean zeebeDataCreated = null;
      while (zeebeDataCreated == null && !shutdown) {
        try {
          zeebeDataCreated = createZeebeData(manuallyCalled);
        } catch (Exception ex) {
          logger.error(String.format("Error occurred when creating demo data: %s. Retrying...", ex.getMessage()), ex);
          sleepFor(2000);
        }
      }
    });
  }

  public boolean createZeebeData(boolean manuallyCalled) {
    this.manuallyCalled = manuallyCalled;

    if (!shouldCreateData(manuallyCalled)) {
      return false;
    }

    return true;
  }

  public boolean shouldCreateData(boolean manuallyCalled) {
    if (!manuallyCalled) {    //when called manually, always create the data
      boolean exists = zeebeStore.zeebeIndicesExists(operateProperties.getZeebeElasticsearch().getPrefix() + "*");
      if (exists) {
        //data already exists
        logger.debug("Data already exists in Zeebe.");
        return false;
      }
    }
    return true;
  }

  protected JobWorker progressSimpleTask(String taskType) {
    return client.newWorker()
        .jobType(taskType)
        .handler((jobClient, job) ->
        {
          final int scenarioCount = ThreadLocalRandom.current().nextInt(3);
          switch (scenarioCount) {
          case 0:
            //timeout
            break;
          case 1:
            //successfully complete task
            jobClient.newCompleteCommand(job.getKey()).send().join();
            break;
          case 2:
            //fail task -> create incident
            jobClient.newFailCommand(job.getKey()).retries(0).send().join();
            break;
          }
        })
        .name("operate")
        .timeout(Duration.ofSeconds(UserTestDataGenerator.JOB_WORKER_TIMEOUT))
        .open();
  }

  protected JobWorker progressSimpleTask(String taskType, int retriesLeft) {
    return client.newWorker()
      .jobType(taskType)
      .handler((jobClient, job) -> jobClient.newFailCommand(job.getKey()).retries(retriesLeft).send().join())
      .name("operate")
      .timeout(Duration.ofSeconds(UserTestDataGenerator.JOB_WORKER_TIMEOUT))
      .open();
  }
}
