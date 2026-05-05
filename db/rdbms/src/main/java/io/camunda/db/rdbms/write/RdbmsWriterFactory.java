/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.db.rdbms.write;

import io.camunda.db.rdbms.config.VendorDatabaseProperties;
import io.camunda.db.rdbms.write.queue.DefaultExecutionQueue;
import io.camunda.db.rdbms.write.queue.TransactionRunner;
import io.camunda.db.rdbms.write.service.ExporterPositionService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.apache.ibatis.session.SqlSessionFactory;

public class RdbmsWriterFactory {

  private final Map<String, SqlSessionFactory> sqlSessionFactories;
  private final Map<String, RdbmsMapperBundle> mapperBundles;
  private final Map<String, VendorDatabaseProperties> vendorDatabaseProperties;
  private final MeterRegistry meterRegistry;
  private final TransactionRunner transactionRunner;

  public RdbmsWriterFactory(
      final Map<String, SqlSessionFactory> sqlSessionFactories,
      final Map<String, RdbmsMapperBundle> mapperBundles,
      final Map<String, VendorDatabaseProperties> vendorDatabaseProperties,
      final MeterRegistry meterRegistry,
      final TransactionRunner transactionRunner) {
    this.sqlSessionFactories = sqlSessionFactories;
    this.mapperBundles = mapperBundles;
    this.vendorDatabaseProperties = vendorDatabaseProperties;
    this.meterRegistry = meterRegistry;
    this.transactionRunner = transactionRunner;
  }

  public RdbmsWriters createWriter(final RdbmsWriterConfig config) {
    final var tenantId = config.physicalTenantId();
    final var sqlSessionFactory = lookup(sqlSessionFactories, tenantId, "SqlSessionFactory");
    final var bundle = lookup(mapperBundles, tenantId, "RdbmsMapperBundle");
    final var properties = lookup(vendorDatabaseProperties, tenantId, "VendorDatabaseProperties");

    final var metrics = new RdbmsWriterMetrics(meterRegistry, config.partitionId());
    final var executionQueue =
        new DefaultExecutionQueue(
            sqlSessionFactory,
            config.partitionId(),
            config.queueSize(),
            config.queueMemoryLimit(),
            metrics,
            transactionRunner);
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
        properties,
        bundle.jobMapper(),
        bundle.jobMetricsBatchMapper(),
        bundle.sequenceFlowMapper(),
        bundle.usageMetricMapper(),
        bundle.usageMetricTUMapper(),
        bundle.batchOperationMapper(),
        bundle.messageSubscriptionMapper(),
        bundle.correlatedMessageSubscriptionMapper(),
        bundle.clusterVariableMapper(),
        bundle.historyDeletionMapper());
  }

  private static <T> T lookup(
      final Map<String, T> map, final String tenantId, final String resourceName) {
    final var value = map.get(tenantId);
    if (value == null) {
      throw new IllegalArgumentException(
          "No " + resourceName + " configured for physical tenant " + tenantId);
    }
    return value;
  }
}
