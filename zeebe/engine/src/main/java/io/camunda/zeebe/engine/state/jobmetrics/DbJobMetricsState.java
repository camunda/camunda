/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.jobmetrics;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Implementation of the job metrics state that stores job worker metrics in RocksDB. This state
 * tracks counts and timestamps for various job states at both job type level and per-worker level.
 */
public class DbJobMetricsState implements MutableJobMetricsState {

  private static final String MONITORING_KEY = "ACTIVE";

  private final JobMetricsKey jobMetricsKey;
  private final JobMetricsValue jobMetricsValue;
  private final ColumnFamily<JobMetricsKey, JobMetricsValue> jobMetricsColumnFamily;

  private final DbString monitoringKey;
  private final JobMetricsMonitoringValue monitoringValue;
  private final ColumnFamily<DbString, JobMetricsMonitoringValue> monitoringColumnFamily;

  public DbJobMetricsState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    jobMetricsKey = new JobMetricsKey();
    jobMetricsValue = new JobMetricsValue();
    jobMetricsColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_METRICS, transactionContext, jobMetricsKey, jobMetricsValue);

    monitoringKey = new DbString();
    monitoringValue = new JobMetricsMonitoringValue();
    monitoringColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.JOB_METRICS_MONITORING,
            transactionContext,
            monitoringKey,
            monitoringValue);
  }

  @Override
  public Optional<JobMetricsValue> getJobMetrics(final String jobType, final String tenantId) {
    jobMetricsKey.setJobType(jobType);
    jobMetricsKey.setTenantId(tenantId);
    return Optional.ofNullable(jobMetricsColumnFamily.get(jobMetricsKey, JobMetricsValue::new));
  }

  @Override
  public boolean exists(final String jobType, final String tenantId) {
    jobMetricsKey.setJobType(jobType);
    jobMetricsKey.setTenantId(tenantId);
    return jobMetricsColumnFamily.exists(jobMetricsKey);
  }

  @Override
  public Optional<JobMetricsMonitoringValue> getMonitoringData() {
    monitoringKey.wrapString(MONITORING_KEY);
    return Optional.ofNullable(
        monitoringColumnFamily.get(monitoringKey, JobMetricsMonitoringValue::new));
  }

  @Override
  public void forEachJobMetrics(final BiConsumer<String, JobMetricsValue> visitor) {
    jobMetricsColumnFamily.forEach(
        (key, value) -> {
          final String compositeKey = key.getJobType() + "_" + key.getTenantId();
          visitor.accept(compositeKey, value);
        });
  }

  @Override
  public void incrementJobTypeCounter(
      final String jobType,
      final String tenantId,
      final JobMetricState state,
      final long timestamp) {
    jobMetricsKey.setJobType(jobType);
    jobMetricsKey.setTenantId(tenantId);

    final boolean isNewEntry = !jobMetricsColumnFamily.exists(jobMetricsKey);
    final JobMetricsValue existingValue =
        jobMetricsColumnFamily.get(jobMetricsKey, JobMetricsValue::new);

    if (existingValue != null) {
      existingValue.incrementJobTypeCounter(state, timestamp);
      jobMetricsColumnFamily.update(jobMetricsKey, existingValue);
    } else {
      jobMetricsValue.reset();
      jobMetricsValue.incrementJobTypeCounter(state, timestamp);
      jobMetricsColumnFamily.insert(jobMetricsKey, jobMetricsValue);
    }

    if (isNewEntry) {
      updateMonitoringSize(jobMetricsValue.getLength());
    }
  }

  @Override
  public void incrementWorkerCounter(
      final String jobType,
      final String tenantId,
      final String workerName,
      final JobMetricState state,
      final long timestamp) {
    jobMetricsKey.setJobType(jobType);
    jobMetricsKey.setTenantId(tenantId);

    final boolean isNewEntry = !jobMetricsColumnFamily.exists(jobMetricsKey);
    final JobMetricsValue existingValue =
        jobMetricsColumnFamily.get(jobMetricsKey, JobMetricsValue::new);

    if (existingValue != null) {
      existingValue.incrementWorkerCounter(workerName, state, timestamp);
      jobMetricsColumnFamily.update(jobMetricsKey, existingValue);
    } else {
      jobMetricsValue.reset();
      jobMetricsValue.incrementWorkerCounter(workerName, state, timestamp);
      jobMetricsColumnFamily.insert(jobMetricsKey, jobMetricsValue);
    }

    if (isNewEntry) {
      updateMonitoringSize(jobMetricsValue.getLength());
    }
  }

  @Override
  public void resetAllMetrics() {
    jobMetricsColumnFamily.forEach(
        (key, value) -> {
          jobMetricsColumnFamily.deleteExisting(key);
        });

    monitoringKey.wrapString(MONITORING_KEY);
    if (monitoringColumnFamily.exists(monitoringKey)) {
      monitoringColumnFamily.deleteExisting(monitoringKey);
    }
  }

  private void updateMonitoringSize(final long additionalBytes) {
    monitoringKey.wrapString(MONITORING_KEY);
    final JobMetricsMonitoringValue existing =
        monitoringColumnFamily.get(monitoringKey, JobMetricsMonitoringValue::new);

    if (existing != null) {
      existing.incrementTotalSizeBytes(additionalBytes);
      monitoringColumnFamily.update(monitoringKey, existing);
    } else {
      monitoringValue.reset();
      monitoringValue.setTotalSizeBytes(additionalBytes);
      monitoringColumnFamily.insert(monitoringKey, monitoringValue);
    }
  }
}
