/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.job;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.query.job.EntityType;
import io.camunda.optimize.dto.optimize.query.job.JobRegistryEntryDto;
import io.camunda.optimize.dto.optimize.query.job.JobType;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.reader.DefinitionReader;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.db.writer.ProcessDefinitionWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.exceptions.OptimizeByQueryFailureException;
import io.camunda.optimize.service.report.ReportService;
import io.camunda.optimize.service.util.BackoffCalculator;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProcessDefinitionDeletionJobHandler implements JobHandler {

  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(ProcessDefinitionDeletionJobHandler.class);
  private static final List<Class<? extends Throwable>> RETRYABLE_EXCEPTIONS =
      List.of(
          IOException.class,
          SocketTimeoutException.class,
          ElasticsearchException.class,
          OpenSearchException.class,
          OptimizeByQueryFailureException.class);
  private static final int MAX_ATTEMPTS = 3;
  private static final long MAX_BACKOFF_SECONDS = 5;
  private static final long INITIAL_BACKOFF_MILLIS = 1000;

  private final ProcessDefinitionReader processDefinitionReader;
  private final ProcessInstanceWriter processInstanceWriter;
  private final ProcessDefinitionWriter processDefinitionWriter;
  private final DefinitionReader definitionReader;
  private final ReportService reportService;
  private final DefinitionService definitionService;
  private final Sleeper sleeper;

  @Autowired
  public ProcessDefinitionDeletionJobHandler(
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessInstanceWriter processInstanceWriter,
      final ProcessDefinitionWriter processDefinitionWriter,
      final DefinitionReader definitionReader,
      final ReportService reportService,
      final DefinitionService definitionService) {
    this(
        processDefinitionReader,
        processInstanceWriter,
        processDefinitionWriter,
        definitionReader,
        reportService,
        definitionService,
        Thread::sleep);
  }

  ProcessDefinitionDeletionJobHandler(
      final ProcessDefinitionReader processDefinitionReader,
      final ProcessInstanceWriter processInstanceWriter,
      final ProcessDefinitionWriter processDefinitionWriter,
      final DefinitionReader definitionReader,
      final ReportService reportService,
      final DefinitionService definitionService,
      final Sleeper sleeper) {
    this.processDefinitionReader = processDefinitionReader;
    this.processInstanceWriter = processInstanceWriter;
    this.processDefinitionWriter = processDefinitionWriter;
    this.definitionReader = definitionReader;
    this.reportService = reportService;
    this.definitionService = definitionService;
    this.sleeper = sleeper;
  }

  @Override
  public JobType getJobType() {
    return JobType.DELETE;
  }

  @Override
  public EntityType getEntityType() {
    return EntityType.PROCESS_DEFINITION;
  }

  @Override
  public void handle(final JobRegistryEntryDto job) {
    final String definitionId = job.getEntityId();
    final Optional<ProcessDefinitionOptimizeDto> definition =
        withRetry(
            "look up process definition " + definitionId,
            () -> processDefinitionReader.getProcessDefinition(definitionId, false));
    if (definition.isEmpty()) {
      LOG.info("Process definition with ID {} no longer exists, nothing to delete.", definitionId);
      return;
    }
    final String bpmnProcessId = definition.get().getKey();

    deleteWithRetry(
        "delete process instances for definition " + definitionId,
        () -> processInstanceWriter.deleteInstancesByDefinitionId(bpmnProcessId, definitionId));
    deleteWithRetry(
        "delete process definition " + definitionId,
        () -> processDefinitionWriter.deleteDefinition(definitionId));

    // Scoped to this definition's own tenant: a bpmnProcessId can exist independently per
    // tenant, so a version still live under a different tenant must not block clearing the
    // cached XML for reports whose data is, for this tenant, now entirely gone.
    final boolean isLastRemainingVersion =
        withRetry(
                "check remaining versions of process definition " + bpmnProcessId,
                () ->
                    definitionReader.getDefinitionVersions(
                        DefinitionType.PROCESS,
                        bpmnProcessId,
                        Collections.singleton(definition.get().getTenantId())))
            .isEmpty();

    if (isLastRemainingVersion) {
      deleteWithRetry(
          "clear cached XML for reports referencing " + bpmnProcessId,
          () -> reportService.clearCachedReportXml(bpmnProcessId));
    }
    definitionService.invalidateProcessDefinition(bpmnProcessId);
  }

  private void deleteWithRetry(final String description, final Runnable deletion) {
    withRetry(
        description,
        () -> {
          deletion.run();
          return null;
        });
  }

  private <T> T withRetry(final String description, final Supplier<T> operation) {
    final BackoffCalculator backoffCalculator =
        new BackoffCalculator(MAX_BACKOFF_SECONDS, INITIAL_BACKOFF_MILLIS);
    for (int attempt = 1; ; attempt++) {
      try {
        return operation.get();
      } catch (final Exception e) {
        if (!isRetryable(e) || attempt >= MAX_ATTEMPTS) {
          throw e;
        }
        LOG.warn(
            "Retrying {} (attempt {}/{}) after retryable error: {}",
            description,
            attempt,
            MAX_ATTEMPTS,
            e.getMessage());
        try {
          sleeper.sleep(backoffCalculator.calculateSleepTime());
        } catch (final InterruptedException ie) {
          Thread.currentThread().interrupt();
          throw e;
        }
      }
    }
  }

  // The real error could be wrapped several levels deep
  private boolean isRetryable(final Throwable throwable) {
    for (Throwable current = throwable; current != null; current = current.getCause()) {
      for (final Class<? extends Throwable> retryableClass : RETRYABLE_EXCEPTIONS) {
        if (retryableClass.isInstance(current)) {
          return true;
        }
      }
    }
    return false;
  }

  @FunctionalInterface
  interface Sleeper {
    void sleep(long millis) throws InterruptedException;
  }
}
