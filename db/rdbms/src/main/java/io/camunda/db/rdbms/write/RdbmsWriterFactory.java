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
import java.util.Map;

public class RdbmsWriterFactory {

  private final Map<String, RdbmsMapperBundle> mapperBundles;
  private final MeterRegistry meterRegistry;

  public RdbmsWriterFactory(
      final Map<String, RdbmsMapperBundle> mapperBundles, final MeterRegistry meterRegistry) {
    this.mapperBundles = mapperBundles;
    this.meterRegistry = meterRegistry;
  }

  public RdbmsWriters createWriter(final RdbmsWriterConfig config) {
    final var bundle = mapperBundles.get(config.physicalTenantId());
    if (bundle == null) {
      throw new IllegalArgumentException(
          "No RdbmsMapperBundle registered for physical tenant '"
              + config.physicalTenantId()
              + "'. Known tenants: "
              + mapperBundles.keySet());
    }
    final var metrics = new RdbmsWriterMetrics(meterRegistry, config.partitionId());
    final var executionQueue =
        new DefaultExecutionQueue(
            bundle.sqlSessionFactory(),
            config.partitionId(),
            config.queueSize(),
            config.queueMemoryLimit(),
            metrics);
    return new RdbmsWriters(
        config,
        executionQueue,
        new ExporterPositionService(executionQueue, bundle.exporterPositionMapper()),
        metrics,
        bundle.auditLogMapper(),
        bundle.decisionInstanceMapper(),
        bundle.decisionDefinitionMapper(),
        bundle.decisionRequirementsMapper(),
        bundle.flowNodeInstanceMapper(),
        bundle.incidentMapper(),
        bundle.processInstanceMapper(),
        bundle.processDefinitionMapper(),
        bundle.purgeMapper(),
        bundle.userTaskMapper(),
        bundle.variableMapper(),
        bundle.vendorDatabaseProperties(),
        bundle.jobMapper(),
        bundle.jobMetricsBatchMapper(),
        bundle.sequenceFlowMapper(),
        bundle.usageMetricMapper(),
        bundle.usageMetricTUMapper(),
        bundle.batchOperationMapper(),
        bundle.messageSubscriptionMapper(),
        bundle.correlatedMessageSubscriptionMapper(),
        bundle.clusterVariableMapper(),
        bundle.historyDeletionMapper(),
        bundle.agentInstanceMapper(),
        bundle.waitStateMapper(),
        bundle.processDefinitionVariableNameLookupMapper());
  }
}
