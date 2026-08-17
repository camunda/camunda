/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.job;

import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobStatus;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.AbstractScheduledService;
import io.camunda.optimize.service.db.reader.JobRegistryReader;
import io.camunda.optimize.service.db.writer.JobRegistryWriter;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.util.configuration.ConfigurationReloadable;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.JobRegistryDispatcherConfiguration;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.stereotype.Component;

/**
 * Polls the job registry for {@code QUEUED} entries and dispatches each to the {@link JobHandler}
 * registered for its (jobType, entityType) pair, marking the entry {@code COMPLETED} or {@code
 * FAILED} once the handler returns or throws. Only one Optimize instance in a cluster should have
 * this enabled.
 */
@Component
public class JobDispatcher extends AbstractScheduledService implements ConfigurationReloadable {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(JobDispatcher.class);

  private final ConfigurationService configurationService;
  private final JobRegistryReader jobRegistryReader;
  private final JobRegistryWriter jobRegistryWriter;
  private final Map<Pair<JobType, EntityType>, JobHandler> handlersByTypeAndEntity;

  // Created lazily on first dispatch, not at construction: most Optimize instances in a cluster
  // run with the dispatcher disabled, and this avoids reserving a thread pool that instance never
  // uses.
  private ExecutorService executorService;

  public JobDispatcher(
      final ConfigurationService configurationService,
      final JobRegistryReader jobRegistryReader,
      final JobRegistryWriter jobRegistryWriter,
      final List<JobHandler> jobHandlers) {
    this.configurationService = configurationService;
    this.jobRegistryReader = jobRegistryReader;
    this.jobRegistryWriter = jobRegistryWriter;
    handlersByTypeAndEntity =
        jobHandlers.stream()
            .collect(
                Collectors.toMap(
                    handler -> Pair.of(handler.getJobType(), handler.getEntityType()),
                    Function.identity(),
                    (first, second) -> {
                      throw new OptimizeConfigurationException(
                          "Multiple JobHandlers registered for jobType "
                              + first.getJobType()
                              + " and entityType "
                              + first.getEntityType());
                    }));
  }

  @PostConstruct
  public void init() {
    LOG.info("Initializing JobDispatcher");
    getDispatcherConfiguration().validate();
    if (getDispatcherConfiguration().isEnabled()) {
      startJobDispatching();
    } else {
      stopJobDispatching();
    }
  }

  public synchronized void startJobDispatching() {
    LOG.info("Starting job dispatching");
    startScheduling();
  }

  public synchronized void stopJobDispatching() {
    LOG.info("Stopping job dispatching");
    stopScheduling();
  }

  @PreDestroy
  public synchronized void destroy() {
    stopJobDispatching();
    if (executorService != null) {
      executorService.shutdown();
    }
  }

  @Override
  protected void run() {
    dispatchNextBatch();
  }

  @Override
  protected Trigger createScheduleTrigger() {
    return new PeriodicTrigger(
        Duration.ofSeconds(getDispatcherConfiguration().getIntervalSeconds()));
  }

  public void dispatchNextBatch() {
    final List<JobRegistryEntryDto> queuedJobs =
        jobRegistryReader.findOldestQueuedJobs(getDispatcherConfiguration().getBatchSize());
    if (queuedJobs.isEmpty()) {
      return;
    }
    final Collection<JobRegistryEntryDto> jobsToDispatch = oldestPerEntity(queuedJobs);
    LOG.info("Dispatching {} queued job registry entries", jobsToDispatch.size());
    jobsToDispatch.stream()
        .map(job -> CompletableFuture.runAsync(() -> dispatchJob(job), getExecutorService()))
        .forEach(CompletableFuture::join);
  }

  private synchronized ExecutorService getExecutorService() {
    if (executorService == null) {
      executorService = Executors.newFixedThreadPool(getDispatcherConfiguration().getThreadCount());
    }
    return executorService;
  }

  /**
   * Keeps only the oldest entry per (jobType, entityType, entityId) in this batch. Entries do not
   * get their own concurrent handler run while another entry for the same entity is already being
   * handled; the rest stay {@code QUEUED} and are picked up in a later batch, once the in-flight
   * one has updated the entity's status.
   *
   * <p>This assumes single-flight-per-entity handling for every job type. If a job type is ever
   * added where multiple queued entries for the same entity are expected to be handled
   * independently and concurrently, this would need to become an opt-in/opt-out property of {@link
   * JobType} rather than applying to all job types.
   */
  private Collection<JobRegistryEntryDto> oldestPerEntity(
      final List<JobRegistryEntryDto> queuedJobs) {
    return queuedJobs.stream()
        .collect(
            Collectors.toMap(
                job -> Pair.of(job.getJobType(), Pair.of(job.getEntityType(), job.getEntityId())),
                Function.identity(),
                (oldest, newer) -> oldest,
                LinkedHashMap::new))
        .values();
  }

  private void dispatchJob(final JobRegistryEntryDto job) {
    try {
      handleQueuedJob(job);
    } catch (final Exception e) {
      LOG.error(
          "Unexpected error dispatching job {} (jobType {}, entityId {})",
          job.getId(),
          job.getJobType(),
          job.getEntityId(),
          e);
    }
  }

  private void handleQueuedJob(final JobRegistryEntryDto job) {
    final JobHandler handler =
        handlersByTypeAndEntity.get(Pair.of(job.getJobType(), job.getEntityType()));
    if (handler == null) {
      LOG.error(
          "No JobHandler registered for jobType {} and entityType {}, marking job {} as failed",
          job.getJobType(),
          job.getEntityType(),
          job.getId());
      jobRegistryWriter.updateJobStatus(
          job.getId(),
          JobStatus.FAILED,
          "No handler registered for jobType "
              + job.getJobType()
              + " and entityType "
              + job.getEntityType());
      return;
    }
    try {
      handler.handle(job);
    } catch (final Exception e) {
      LOG.error(
          "Failed to handle job {} (jobType {}, entityType {}, entityId {})",
          job.getId(),
          job.getJobType(),
          job.getEntityType(),
          job.getEntityId(),
          e);
      jobRegistryWriter.updateJobStatus(job.getId(), JobStatus.FAILED, e.getMessage());
      return;
    }
    jobRegistryWriter.updateJobStatus(job.getId(), JobStatus.COMPLETED, null);
    LOG.info(
        "Completed job {} (jobType {}, entityType {}, entityId {})",
        job.getId(),
        job.getJobType(),
        job.getEntityType(),
        job.getEntityId());
  }

  @Override
  public void reloadConfiguration(final ApplicationContext context) {
    init();
  }

  private JobRegistryDispatcherConfiguration getDispatcherConfiguration() {
    return configurationService.getJobRegistryDispatcherConfiguration();
  }
}
