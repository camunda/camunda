/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.write.queue.DefaultExecutionQueue;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.micrometer.core.instrument.MeterRegistry;

public class RdbmsWriterFactory {

  private final RdbmsMapperBundle mapperBundle;
  private final MeterRegistry meterRegistry;

  public RdbmsWriterFactory(
      final RdbmsMapperBundle mapperBundle, final MeterRegistry meterRegistry) {
    this.mapperBundle = mapperBundle;
    this.meterRegistry = meterRegistry;
  }

  public RdbmsWriters createWriter(final RdbmsWriterConfig config) {
    final var metrics = new RdbmsWriterMetrics(meterRegistry, config.partitionId());
    final var executionQueue =
        new DefaultExecutionQueue(
            mapperBundle.sqlSessionFactory(),
            config.partitionId(),
            config.queueSize(),
            config.queueMemoryLimit(),
            metrics);
    return new RdbmsWriters(
        config,
        executionQueue,
        new ExporterPositionService(executionQueue, mapperBundle.exporterPositionMapper()),
        metrics,
        mapperBundle.auditLogMapper(),
        mapperBundle.decisionInstanceMapper(),
        mapperBundle.decisionDefinitionMapper(),
        mapperBundle.decisionRequirementsMapper(),
        mapperBundle.flowNodeInstanceMapper(),
        mapperBundle.incidentMapper(),
        mapperBundle.processInstanceMapper(),
        mapperBundle.processDefinitionMapper(),
        mapperBundle.purgeMapper(),
        mapperBundle.userTaskMapper(),
        mapperBundle.variableMapper(),
        mapperBundle.vendorDatabaseProperties(),
        mapperBundle.jobMapper(),
        mapperBundle.jobMetricsBatchMapper(),
        mapperBundle.sequenceFlowMapper(),
        mapperBundle.usageMetricMapper(),
        mapperBundle.usageMetricTUMapper(),
        mapperBundle.batchOperationMapper(),
        mapperBundle.messageSubscriptionMapper(),
        mapperBundle.correlatedMessageSubscriptionMapper(),
        mapperBundle.clusterVariableMapper(),
        mapperBundle.historyDeletionMapper(),
        mapperBundle.agentHistoryMapper(),
        mapperBundle.agentInstanceMapper(),
        mapperBundle.waitStateMapper());
  }
}
