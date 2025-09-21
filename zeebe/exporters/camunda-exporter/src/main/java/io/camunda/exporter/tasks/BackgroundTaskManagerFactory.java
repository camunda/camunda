/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.exporter.ExporterMetadata;
import io.camunda.exporter.ExporterResourceProvider;
import io.camunda.exporter.config.ConnectionTypes;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.exporter.metrics.CamundaExporterMetrics;
import io.camunda.exporter.notifier.HttpClientWrapper;
import io.camunda.exporter.notifier.IncidentNotifier;
import io.camunda.exporter.notifier.M2mTokenManager;
import io.camunda.exporter.tasks.archiver.ApplyRolloverPeriodJob;
import io.camunda.exporter.tasks.archiver.ArchiverRepository;
import io.camunda.exporter.tasks.archiver.BatchOperationArchiverJob;
import io.camunda.exporter.tasks.archiver.ElasticsearchArchiverRepository;
import io.camunda.exporter.tasks.archiver.OpenSearchArchiverRepository;
import io.camunda.exporter.tasks.archiver.ProcessInstanceToBeArchivedCountJob;
import io.camunda.exporter.tasks.archiver.ProcessInstancesArchiverJob;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateRepository;
import io.camunda.exporter.tasks.batchoperations.BatchOperationUpdateTask;
import io.camunda.exporter.tasks.batchoperations.ElasticsearchBatchOperationUpdateRepository;
import io.camunda.exporter.tasks.batchoperations.OpensearchBatchOperationUpdateRepository;
import io.camunda.exporter.tasks.incident.ElasticsearchIncidentUpdateRepository;
import io.camunda.exporter.tasks.incident.IncidentUpdateRepository;
import io.camunda.exporter.tasks.incident.IncidentUpdateTask;
import io.camunda.exporter.tasks.incident.OpenSearchIncidentUpdateRepository;
import io.camunda.search.connect.es.ElasticsearchConnector;
import io.camunda.search.connect.os.OpensearchConnector;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate;
import io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate;
import io.camunda.webapps.schema.descriptors.template.IncidentTemplate;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.descriptors.template.OperationTemplate;
import io.camunda.webapps.schema.descriptors.template.PostImporterQueueTemplate;
import io.camunda.zeebe.exporter.common.cache.ExporterEntityCacheImpl;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.util.error.FatalErrorHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.opensearch.client.opensearch.OpenSearchAsyncClient;
import org.opensearch.client.opensearch.generic.OpenSearchGenericClient;
import org.slf4j.Logger;

public final class BackgroundTaskManagerFactory {
  private final int partitionId;
  private final String exporterId;
  private final ExporterConfiguration config;
  private final ExporterResourceProvider resourceProvider;
  private final CamundaExporterMetrics metrics;
  private final Logger logger;
  private final ExporterMetadata metadata;
  private final ObjectMapper objectMapper;

  private ScheduledThreadPoolExecutor executor;
  private ArchiverRepository archiverRepository;
  private IncidentUpdateRepository incidentRepository;
  private BatchOperationUpdateRepository batchOperationUpdateRepository;
  private final ExporterEntityCacheImpl<Long, CachedProcessEntity> processCache;

  public BackgroundTaskManagerFactory(
      final int partitionId,
      final String exporterId,
      final ExporterConfiguration config,
      final ExporterResourceProvider resourceProvider,
      final CamundaExporterMetrics metrics,
      final Logger logger,
      final ExporterMetadata metadata,
      final ObjectMapper objectMapper,
      final ExporterEntityCacheImpl<Long, CachedProcessEntity> processCache) {
    this.partitionId = partitionId;
    this.exporterId = exporterId;
    this.config = config;
    this.resourceProvider = resourceProvider;
    this.metrics = metrics;
    this.logger = logger;
    this.metadata = metadata;
    this.objectMapper = objectMapper;
    this.processCache = processCache;
  }

  public BackgroundTaskManager build() {
    executor = buildExecutor();

    // initialize all repositories based on connection type to reuse clients
    final var connectionType = ConnectionTypes.from(config.getConnect().getType());
    if (connectionType == ConnectionTypes.OPENSEARCH) {
      final var connector = new OpensearchConnector(config.getConnect());
      final var asyncClient = connector.createAsyncClient();
      final var genericClient =
          new OpenSearchGenericClient(asyncClient._transport(), asyncClient._transportOptions());

      archiverRepository = createArchiverRepository(asyncClient, genericClient);
      incidentRepository = createIncidentUpdateRepository(asyncClient);
      batchOperationUpdateRepository = createBatchOperationRepository(asyncClient);
    } else {
      final var connector = new ElasticsearchConnector(config.getConnect());
      final ElasticsearchAsyncClient asyncClient = connector.createAsyncClient();

      archiverRepository = createArchiverRepository(asyncClient);
      incidentRepository = createIncidentUpdateRepository(asyncClient);
      batchOperationUpdateRepository = createBatchOperationRepository(asyncClient);
    }

    final List<RunnableTask> tasks = buildTasks();

    return new BackgroundTaskManager(
        partitionId,
        archiverRepository,
        incidentRepository,
        batchOperationUpdateRepository,
        logger,
        executor,
        tasks,
        Duration.ofSeconds(5));
  }

  private OpensearchBatchOperationUpdateRepository createBatchOperationRepository(
      final OpenSearchAsyncClient asyncClient) {
    final var operationTemplate =
        resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class);
    final var batchOperationTemplate =
        resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class);
    return new OpensearchBatchOperationUpdateRepository(
        asyncClient,
        executor,
        batchOperationTemplate.getFullQualifiedName(),
        operationTemplate.getFullQualifiedName(),
        logger);
  }

  private OpenSearchIncidentUpdateRepository createIncidentUpdateRepository(
      final OpenSearchAsyncClient asyncClient) {
    final var listViewTemplate =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    final var flowNodeTemplate =
        resourceProvider.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class);
    final var incidentTemplate =
        resourceProvider.getIndexTemplateDescriptor(IncidentTemplate.class);
    final var postImporterTemplate =
        resourceProvider.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);
    final var operationTemplate =
        resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class);
    final var batchOperationTemplate =
        resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class);
    return new OpenSearchIncidentUpdateRepository(
        partitionId,
        postImporterTemplate.getAlias(),
        incidentTemplate.getAlias(),
        listViewTemplate.getAlias(),
        listViewTemplate.getFullQualifiedName(),
        flowNodeTemplate.getAlias(),
        operationTemplate.getAlias(),
        asyncClient,
        executor,
        logger);
  }

  private @NotNull OpenSearchArchiverRepository createArchiverRepository(
      final OpenSearchAsyncClient asyncClient, final OpenSearchGenericClient genericClient) {
    return new OpenSearchArchiverRepository(
        partitionId,
        config.getHistory(),
        resourceProvider,
        asyncClient,
        genericClient,
        executor,
        metrics,
        logger);
  }

  private ElasticsearchBatchOperationUpdateRepository createBatchOperationRepository(
      final ElasticsearchAsyncClient asyncClient) {
    final var operationTemplate =
        resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class);
    final var batchOperationTemplate =
        resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class);
    return new ElasticsearchBatchOperationUpdateRepository(
        asyncClient,
        executor,
        operationTemplate.getFullQualifiedName(),
        batchOperationTemplate.getFullQualifiedName(),
        logger);
  }

  private @NotNull ElasticsearchIncidentUpdateRepository createIncidentUpdateRepository(
      final ElasticsearchAsyncClient asyncClient) {
    final var listViewTemplate =
        resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class);
    final var flowNodeTemplate =
        resourceProvider.getIndexTemplateDescriptor(FlowNodeInstanceTemplate.class);
    final var incidentTemplate =
        resourceProvider.getIndexTemplateDescriptor(IncidentTemplate.class);
    final var postImporterTemplate =
        resourceProvider.getIndexTemplateDescriptor(PostImporterQueueTemplate.class);
    final var operationTemplate =
        resourceProvider.getIndexTemplateDescriptor(OperationTemplate.class);
    return new ElasticsearchIncidentUpdateRepository(
        partitionId,
        postImporterTemplate.getAlias(),
        incidentTemplate.getAlias(),
        listViewTemplate.getAlias(),
        listViewTemplate.getFullQualifiedName(),
        flowNodeTemplate.getAlias(),
        operationTemplate.getAlias(),
        asyncClient,
        executor,
        logger);
  }

  private @NotNull ElasticsearchArchiverRepository createArchiverRepository(
      final ElasticsearchAsyncClient asyncClient) {
    return new ElasticsearchArchiverRepository(
        partitionId, config.getHistory(), resourceProvider, asyncClient, executor, metrics, logger);
  }

  private List<RunnableTask> buildTasks() {
    final List<RunnableTask> tasks = new ArrayList<>();

    tasks.add(buildIncidentMarkerTask());
    tasks.add(buildProcessInstanceArchiverJob());
    if (config.getHistory().isTrackArchivalMetricsForProcessInstance()) {
      tasks.add(buildProcessInstanceToBeArchivedCountJob());
    }
    if (partitionId == START_PARTITION_ID) {
      tasks.add(buildBatchOperationArchiverJob());
      tasks.add(new ApplyRolloverPeriodJob(archiverRepository));
      tasks.add(buildBatchOperationUpdateTask());
    }

    executor.setCorePoolSize(tasks.size());
    return tasks;
  }

  private ReschedulingTask buildProcessInstanceToBeArchivedCountJob() {
    final var processInstanceToBeArchivedCountJob =
        new ProcessInstanceToBeArchivedCountJob(metrics, archiverRepository, logger);

    return new ReschedulingTask(
        processInstanceToBeArchivedCountJob,
        0,
        ProcessInstanceToBeArchivedCountJob.DELAY_BETWEEN_RUNS,
        ProcessInstanceToBeArchivedCountJob.MAX_DELAY_BETWEEN_RUNS,
        executor,
        logger);
  }

  private ReschedulingTask buildIncidentMarkerTask() {

    final M2mTokenManager m2mTokenManager =
        new M2mTokenManager(config.getNotifier(), HttpClientWrapper.newHttpClient(), objectMapper);

    final IncidentNotifier incidentNotifier =
        new IncidentNotifier(
            m2mTokenManager,
            processCache,
            config.getNotifier(),
            HttpClientWrapper.newHttpClient(),
            executor,
            objectMapper);

    final var postExport = config.getPostExport();
    return new ReschedulingTask(
        new IncidentUpdateTask(
            metadata,
            incidentRepository,
            postExport.isIgnoreMissingData(),
            postExport.getBatchSize(),
            executor,
            incidentNotifier,
            logger),
        1,
        postExport.getDelayBetweenRuns(),
        postExport.getMaxDelayBetweenRuns(),
        executor,
        logger);
  }

  private ReschedulingTask buildBatchOperationUpdateTask() {
    final var postExport = config.getPostExport();
    return new ReschedulingTask(
        new BatchOperationUpdateTask(batchOperationUpdateRepository, logger, executor),
        1,
        postExport.getDelayBetweenRuns(),
        postExport.getMaxDelayBetweenRuns(),
        executor,
        logger);
  }

  private ReschedulingTask buildProcessInstanceArchiverJob() {
    final var dependantTemplates = new ArrayList<ProcessInstanceDependant>();
    resourceProvider.getIndexTemplateDescriptors().stream()
        .filter(ProcessInstanceDependant.class::isInstance)
        .map(ProcessInstanceDependant.class::cast)
        .forEach(dependantTemplates::add);

    return buildReschedulingArchiverTask(
        new ProcessInstancesArchiverJob(
            archiverRepository,
            resourceProvider.getIndexTemplateDescriptor(ListViewTemplate.class),
            dependantTemplates,
            metrics,
            logger,
            executor));
  }

  private ReschedulingTask buildBatchOperationArchiverJob() {
    return buildReschedulingArchiverTask(
        new BatchOperationArchiverJob(
            archiverRepository,
            resourceProvider.getIndexTemplateDescriptor(BatchOperationTemplate.class),
            metrics,
            logger,
            executor));
  }

  private ReschedulingTask buildReschedulingArchiverTask(final BackgroundTask task) {
    return new ReschedulingTask(
        task,
        config.getHistory().getRolloverBatchSize(),
        config.getHistory().getDelayBetweenRuns(),
        config.getHistory().getMaxDelayBetweenRuns(),
        executor,
        logger);
  }

  private ScheduledThreadPoolExecutor buildExecutor() {
    final var threadFactory =
        Thread.ofPlatform()
            .name("exporter-" + exporterId + "-p" + partitionId + "-tasks-", 0)
            .uncaughtExceptionHandler(FatalErrorHandler.uncaughtExceptionHandler(logger))
            .factory();
    final var executor = new ScheduledThreadPoolExecutor(0, threadFactory);
    executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    executor.setRemoveOnCancelPolicy(true);
    executor.allowCoreThreadTimeOut(true);
    executor.setKeepAliveTime(1, TimeUnit.MINUTES);

    return executor;
  }
}
