/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.data;

import static io.camunda.operate.util.ThreadUtil.sleepFor;
import static io.camunda.webapps.schema.entities.AbstractExporterEntity.DEFAULT_TENANT_ID;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.operate.data.usertest.UserTestDataGenerator;
import io.camunda.operate.store.ZeebeStore;
import io.camunda.operate.zeebe.ZeebeESConstants;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;

@DependsOn("searchEngineSchemaInitializer")
public abstract class AbstractDataGenerator implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDataGenerator.class);

  @Autowired
  @Qualifier("camundaClient")
  protected CamundaClient client;

  protected boolean manuallyCalled = false;
  protected ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(3);
  @Autowired private SecurityConfiguration securityConfiguration;
  private boolean shutdown = false;
  @Autowired private ZeebeStore zeebeStore;

  @PostConstruct
  private void startDataGenerator() {
    startGeneratingData();
  }

  protected void startGeneratingData() {
    LOGGER.debug("INIT: Generate demo data...");
    try {
      createZeebeDataAsync(false);
    } catch (final Exception ex) {
      LOGGER.debug("Demo data could not be generated. Cause: {}", ex.getMessage());
      LOGGER.error("Error occurred when generating demo data.", ex);
    }
  }

  @PreDestroy
  public void shutdown() {
    LOGGER.info("Shutdown DataGenerator");
    shutdown = true;
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(200, TimeUnit.MILLISECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (final InterruptedException e) {
        scheduler.shutdownNow();
      }
    }
  }

  @Override
  public void createZeebeDataAsync(final boolean manuallyCalled) {
    scheduler.execute(
        () -> {
          Boolean zeebeDataCreated = null;
          while (zeebeDataCreated == null && !shutdown) {
            try {
              zeebeDataCreated = createZeebeData(manuallyCalled);
            } catch (final Exception ex) {
              LOGGER.error(
                  String.format(
                      "Error occurred when creating demo data: %s. Retrying...", ex.getMessage()),
                  ex);
              sleepFor(2000);
            }
          }
        });
  }

  public boolean createZeebeData(final boolean manuallyCalled) {
    this.manuallyCalled = manuallyCalled;

    if (!shouldCreateData(manuallyCalled)) {
      return false;
    }

    return true;
  }

  public boolean shouldCreateData(final boolean manuallyCalled) {
    if (!manuallyCalled) { // when called manually, always create the data
      final String zeebeIndexPrefix = zeebeStore.getZeebeIndexPrefix();
      final boolean exists =
          zeebeStore.zeebeIndicesExists(zeebeIndexPrefix + "*" + ZeebeESConstants.DEPLOYMENT + "*");
      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists in Zeebe.");
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("checkstyle:MissingSwitchDefault")
  protected JobWorker progressSimpleTask(final String taskType) {
    return client
        .newWorker()
        .jobType(taskType)
        .handler(
            (jobClient, job) -> {
              final int scenarioCount = ThreadLocalRandom.current().nextInt(3);
              switch (scenarioCount) {
                case 0:
                  // timeout
                  break;
                case 1:
                  // successfully complete task
                  jobClient.newCompleteCommand(job.getKey()).send().join();
                  break;
                case 2:
                  // fail task -> create incident
                  jobClient.newFailCommand(job.getKey()).retries(0).send().join();
                  break;
              }
            })
        .name("operate")
        .timeout(Duration.ofSeconds(UserTestDataGenerator.JOB_WORKER_TIMEOUT))
        .open();
  }

  protected JobWorker progressSimpleTask(final String taskType, final int retriesLeft) {
    return client
        .newWorker()
        .jobType(taskType)
        .handler(
            (jobClient, job) ->
                jobClient.newFailCommand(job.getKey()).retries(retriesLeft).send().join())
        .name("operate")
        .timeout(Duration.ofSeconds(UserTestDataGenerator.JOB_WORKER_TIMEOUT))
        .open();
  }

  protected String getTenant(final String tenantId) {
    if (securityConfiguration.getMultiTenancy().isEnabled()) {
      return tenantId;
    }
    return DEFAULT_TENANT_ID;
  }
}
