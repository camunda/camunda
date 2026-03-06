/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import io.camunda.optimize.dto.optimize.FlatProcessInstanceDto;
import io.camunda.optimize.dto.optimize.ImportRequestDto;
import io.camunda.optimize.dto.optimize.ProcessInstanceDto;
import io.camunda.optimize.dto.optimize.query.process.FlatFlowNodeInstanceDto;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.writer.FlowNodeInstanceWriter;
import io.camunda.optimize.service.db.writer.ProcessInstanceWriter;
import io.camunda.optimize.service.util.BackoffCalculator;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A combined import job that processes both {@link ProcessInstanceDto}s and {@link
 * FlatFlowNodeInstanceDto}s in a single batch, generating all import requests and submitting them
 * together.
 */
public class ZeebeProcessInstanceImportJob implements Runnable {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeProcessInstanceImportJob.class);

  private final ProcessInstanceWriter processInstanceWriter;
  private final FlowNodeInstanceWriter flowNodeInstanceWriter;
  private final ConfigurationService configurationService;
  private final Runnable importCompleteCallback;
  private final DatabaseClient databaseClient;
  private final BackoffCalculator backoffCalculator = new BackoffCalculator(1L, 30L);
  private final String sourceExportIndex;

  private List<FlatProcessInstanceDto> flatProcessInstances = List.of();
  private List<FlatFlowNodeInstanceDto> flowNodeInstances = List.of();

  public ZeebeProcessInstanceImportJob(
      final ProcessInstanceWriter processInstanceWriter,
      final FlowNodeInstanceWriter flowNodeInstanceWriter,
      final ConfigurationService configurationService,
      final Runnable importCompleteCallback,
      final DatabaseClient databaseClient,
      final String sourceExportIndex) {
    this.processInstanceWriter = processInstanceWriter;
    this.flowNodeInstanceWriter = flowNodeInstanceWriter;
    this.configurationService = configurationService;
    this.importCompleteCallback = importCompleteCallback;
    this.databaseClient = databaseClient;
    this.sourceExportIndex = sourceExportIndex;
  }

  public void setFlatProcessInstances(final List<FlatProcessInstanceDto> flatProcessInstances) {
    this.flatProcessInstances = flatProcessInstances;
  }

  public void setFlowNodeInstances(final List<FlatFlowNodeInstanceDto> flowNodeInstances) {
    this.flowNodeInstances = flowNodeInstances;
  }

  @Override
  public void run() {
    boolean success = false;
    do {
      try {
        final long start = System.currentTimeMillis();
        persistEntities();
        final long end = System.currentTimeMillis();
        LOG.debug(
            "Executing combined process instance import to database took [{}] ms", end - start);
        success = true;
      } catch (final Exception e) {
        LOG.error("Error while executing combined process instance import to database", e);
        final long sleepTime = backoffCalculator.calculateSleepTime();
        try {
          Thread.sleep(sleepTime);
        } catch (final InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      }
    } while (!success);
    importCompleteCallback.run();
  }

  private void persistEntities() {
    final List<ImportRequestDto> allRequests = getImportRequests();

    if (!allRequests.isEmpty()) {
      databaseClient.executeImportRequestsAsBulk(
          "Zeebe process instances and flow node instances",
          allRequests,
          configurationService.getSkipDataAfterNestedDocLimitReached());
    }
  }

  public List<ImportRequestDto> getImportRequests() {
    final List<ImportRequestDto> allRequests = new ArrayList<>();

    if (!flatProcessInstances.isEmpty()) {
      // flatProcessInstances is always derived from processInstances and has the same size
      allRequests.addAll(
          processInstanceWriter.generateFlatProcessInstanceImports(flatProcessInstances));
    }

    if (!flowNodeInstances.isEmpty()) {
      allRequests.addAll(
          flowNodeInstanceWriter.generateFlatFlowNodeInstanceImports(flowNodeInstances));
    }
    return allRequests;
  }
}
