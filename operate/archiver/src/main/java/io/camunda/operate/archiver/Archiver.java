/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.archiver;

import io.camunda.operate.Metrics;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.templates.BatchOperationTemplate;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.schema.templates.ListViewTemplate;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;
import io.camunda.operate.util.CollectionUtil;
import io.camunda.operate.zeebe.PartitionHolder;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

@Component
@DependsOn("schemaStartup")
public class Archiver {
  protected static final String INDEX_NAME_PATTERN = "%s%s";
  private static final Logger LOGGER = LoggerFactory.getLogger(Archiver.class);

  protected final BeanFactory beanFactory;
  protected final OperateProperties operateProperties;
  protected final PartitionHolder partitionHolder;
  protected final ThreadPoolTaskScheduler archiverExecutor;
  protected final ArchiverRepository archiverRepository;
  protected final ListViewTemplate processInstanceTemplate;
  protected final List<ProcessInstanceDependant> processInstanceDependants;
  protected final DecisionInstanceTemplate decisionInstanceTemplate;
  protected final BatchOperationTemplate batchOperationTemplate;
  protected final Metrics metrics;

  @Autowired
  public Archiver(
      final BeanFactory beanFactory,
      final OperateProperties operateProperties,
      final PartitionHolder partitionHolder,
      @Qualifier("archiverThreadPoolExecutor") final ThreadPoolTaskScheduler archiverExecutor,
      final ArchiverRepository archiverRepository,
      final ListViewTemplate processInstanceTemplate,
      final List<ProcessInstanceDependant> processInstanceDependants,
      final DecisionInstanceTemplate decisionInstanceTemplate,
      final BatchOperationTemplate batchOperationTemplate,
      final Metrics metrics) {
    this.beanFactory = beanFactory;
    this.operateProperties = operateProperties;
    this.partitionHolder = partitionHolder;
    this.archiverExecutor = archiverExecutor;
    this.archiverRepository = archiverRepository;
    this.processInstanceTemplate = processInstanceTemplate;
    this.processInstanceDependants = processInstanceDependants;
    this.decisionInstanceTemplate = decisionInstanceTemplate;
    this.batchOperationTemplate = batchOperationTemplate;
    this.metrics = metrics;
  }

  @PostConstruct
  public void startArchiving() {
    if (operateProperties.getArchiver().isRolloverEnabled()) {
      LOGGER.info("INIT: Start archiving data...");

      // split the list of partitionIds to parallelize
      final List<Integer> partitionIds = partitionHolder.getPartitionIds();
      LOGGER.info("Starting archiver for partitions: {}", partitionIds);
      final int threadsCount = operateProperties.getArchiver().getThreadsCount();
      if (threadsCount > partitionIds.size()) {
        LOGGER.warn(
            "Too many archiver threads are configured, not all of them will be in use. Number of threads: {}, number of partitions to parallelize by: {}",
            threadsCount,
            partitionIds.size());
      }

      final boolean archiveById = operateProperties.getArchiver().isArchiveByIdEnabled();
      if (archiveById) {
        LOGGER.info("Archive-by-ID mode enabled (opt-in).");
      }

      for (int i = 0; i < threadsCount; i++) {
        final List<Integer> partitionIdsSubset =
            CollectionUtil.splitAndGetSublist(partitionIds, threadsCount, i);
        if (!partitionIdsSubset.isEmpty()) {
          final AbstractArchiverJob processInstancesArchiverJob =
              archiveById
                  ? beanFactory.getBean(
                      ProcessInstancesByIdArchiverJob.class, this, partitionIdsSubset)
                  : beanFactory.getBean(
                      ProcessInstancesArchiverJob.class,
                      this,
                      partitionIdsSubset,
                      processInstanceTemplate,
                      processInstanceDependants,
                      metrics,
                      archiverRepository);
          archiverExecutor.execute(processInstancesArchiverJob);

          final var standaloneDecisionArchiverJob =
              beanFactory.getBean(
                  StandaloneDecisionArchiverJob.class,
                  this,
                  partitionIdsSubset,
                  decisionInstanceTemplate,
                  metrics,
                  archiverRepository);
          archiverExecutor.execute(standaloneDecisionArchiverJob);
        }
        if (partitionIdsSubset.contains(1)) {
          final var batchOperationArchiverJob =
              beanFactory.getBean(
                  BatchOperationArchiverJob.class,
                  this,
                  batchOperationTemplate,
                  metrics,
                  archiverRepository);
          archiverExecutor.execute(batchOperationArchiverJob);
        }
      }
    }
  }

  public CompletableFuture<Void> moveDocuments(
      final String sourceIndexName,
      final String idFieldName,
      final String finishDate,
      final List<Object> ids) {
    final var destinationIndexName = getDestinationIndexName(sourceIndexName, finishDate);
    return archiverRepository
        .reindexDocuments(sourceIndexName, destinationIndexName, idFieldName, ids)
        .thenCompose(
            (ignore) -> {
              archiverRepository.setIndexLifeCycle(destinationIndexName);
              return archiverRepository.deleteDocuments(sourceIndexName, idFieldName, ids);
            });
  }

  public String getDestinationIndexName(final String sourceIndexName, final String finishDate) {
    return String.format(INDEX_NAME_PATTERN, sourceIndexName, finishDate);
  }

  public ArchiverRepository getArchiverRepository() {
    return archiverRepository;
  }
}
