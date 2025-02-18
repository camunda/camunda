/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.rdbms;

import io.camunda.db.rdbms.RdbmsService;
import io.camunda.db.rdbms.write.RdbmsWriter;
import io.camunda.exporter.rdbms.handlers.DecisionDefinitionExportHandler;
import io.camunda.exporter.rdbms.handlers.DecisionInstanceExportHandler;
import io.camunda.exporter.rdbms.handlers.DecisionRequirementsExportHandler;
import io.camunda.exporter.rdbms.handlers.FlowNodeExportHandler;
import io.camunda.exporter.rdbms.handlers.FormExportHandler;
import io.camunda.exporter.rdbms.handlers.GroupExportHandler;
import io.camunda.exporter.rdbms.handlers.MappingExportHandler;
import io.camunda.exporter.rdbms.handlers.ProcessExportHandler;
import io.camunda.exporter.rdbms.handlers.ProcessInstanceExportHandler;
import io.camunda.exporter.rdbms.handlers.RoleExportHandler;
import io.camunda.exporter.rdbms.handlers.TenantExportHandler;
import io.camunda.exporter.rdbms.handlers.UserExportHandler;
import io.camunda.exporter.rdbms.handlers.UserTaskExportHandler;
import io.camunda.exporter.rdbms.handlers.VariableExportHandler;
import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Context;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import java.time.Duration;

/** https://docs.camunda.io/docs/next/components/zeebe/technical-concepts/process-lifecycles/ */
public class RdbmsExporterWrapper implements Exporter {

  /** The partition on which all process deployments are published */
  public static final long PROCESS_DEFINITION_PARTITION = 1L;

  private static final int DEFAULT_FLUSH_INTERVAL = 500;
  private static final int DEFAULT_MAX_QUEUE_SIZE = 1000;

  private final RdbmsService rdbmsService;

  private RdbmsExporter exporter;

  public RdbmsExporterWrapper(final RdbmsService rdbmsService) {
    this.rdbmsService = rdbmsService;
  }

  @Override
  public void configure(final Context context) {
    final var maxQueueSize = readMaxQueueSize(context);
    final int partitionId = context.getPartitionId();
    final RdbmsWriter rdbmsWriter = rdbmsService.createWriter(partitionId, maxQueueSize);

    final var builder =
        new RdbmsExporterConfig.Builder()
            .partitionId(partitionId)
            .flushInterval(readFlushInterval(context))
            .maxQueueSize(maxQueueSize)
            .rdbmsWriter(rdbmsWriter);
    createHandlers(partitionId, rdbmsWriter, builder);

    exporter = new RdbmsExporter(builder.build());
  }

  @Override
  public void open(final Controller controller) {
    exporter.open(controller);
  }

  @Override
  public void close() {
    exporter.close();
  }

  @Override
  public void export(final Record<?> record) {
    exporter.export(record);
  }

  @Override
  public void purge() throws Exception {
    exporter.purge();
  }

  private Duration readFlushInterval(final Context context) {
    final var arguments = context.getConfiguration().getArguments();
    if (arguments != null) {
      final var flushIntervalMillis =
          (Integer) arguments.getOrDefault("flushInterval", DEFAULT_FLUSH_INTERVAL);
      return Duration.ofMillis(flushIntervalMillis);
    } else {
      return Duration.ofMillis(DEFAULT_FLUSH_INTERVAL);
    }
  }

  private int readMaxQueueSize(final Context context) {
    final var arguments = context.getConfiguration().getArguments();
    if (arguments != null) {
      return (Integer) arguments.getOrDefault("maxQueueSize", DEFAULT_MAX_QUEUE_SIZE);
    } else {
      return DEFAULT_MAX_QUEUE_SIZE;
    }
  }

  private static void createHandlers(
      final long partitionId,
      final RdbmsWriter rdbmsWriter,
      final RdbmsExporterConfig.Builder builder) {
    if (partitionId == PROCESS_DEFINITION_PARTITION) {
      builder.withHandler(
          ValueType.PROCESS, new ProcessExportHandler(rdbmsWriter.getProcessDefinitionWriter()));
    }
    builder.withHandler(
        ValueType.AUTHORIZATION,
        new AuthorizationExportHandler(rdbmsWriter.getAuthorizationWriter()));
    builder.withHandler(
        ValueType.DECISION,
        new DecisionDefinitionExportHandler(rdbmsWriter.getDecisionDefinitionWriter()));
    builder.withHandler(
        ValueType.DECISION_REQUIREMENTS,
        new DecisionRequirementsExportHandler(rdbmsWriter.getDecisionRequirementsWriter()));
    builder.withHandler(
        ValueType.DECISION_EVALUATION,
        new DecisionInstanceExportHandler(rdbmsWriter.getDecisionInstanceWriter()));
    builder.withHandler(ValueType.GROUP, new GroupExportHandler(rdbmsWriter.getGroupWriter()));
    builder.withHandler(
        ValueType.INCIDENT, new IncidentExportHandler(rdbmsWriter.getIncidentWriter()));
    builder.withHandler(
        ValueType.INCIDENT,
        new ProcessInstanceIncidentExportHandler(rdbmsWriter.getProcessInstanceWriter()));
    builder.withHandler(
        ValueType.INCIDENT,
        new FlowNodeInstanceIncidentExportHandler(rdbmsWriter.getFlowNodeInstanceWriter()));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new ProcessInstanceExportHandler(rdbmsWriter.getProcessInstanceWriter()));
    builder.withHandler(
        ValueType.PROCESS_INSTANCE,
        new FlowNodeExportHandler(rdbmsWriter.getFlowNodeInstanceWriter()));
    builder.withHandler(ValueType.TENANT, new TenantExportHandler(rdbmsWriter.getTenantWriter()));
    builder.withHandler(
        ValueType.VARIABLE, new VariableExportHandler(rdbmsWriter.getVariableWriter()));
    builder.withHandler(ValueType.ROLE, new RoleExportHandler(rdbmsWriter.getRoleWriter()));
    builder.withHandler(ValueType.USER, new UserExportHandler(rdbmsWriter.getUserWriter()));
    builder.withHandler(
        ValueType.USER_TASK, new UserTaskExportHandler(rdbmsWriter.getUserTaskWriter()));
    builder.withHandler(ValueType.FORM, new FormExportHandler(rdbmsWriter.getFormWriter()));
    builder.withHandler(
        ValueType.MAPPING, new MappingExportHandler(rdbmsWriter.getMappingWriter()));
  }
}
