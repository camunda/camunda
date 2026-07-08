/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers.auditlog;

import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import java.util.HashSet;
import java.util.Set;

public final class AuditLogHandlerBuilder {
  final Set<AuditLogHandler<?>> handlers = new HashSet<>();
  private final String indexName;
  private final String auditLogCleanupIndexName;
  private final AuditLogConfiguration auditLog;

  private AuditLogHandlerBuilder(
      final String indexName,
      final String auditLogCleanupIndexName,
      final AuditLogConfiguration auditLog) {
    this.indexName = indexName;
    this.auditLogCleanupIndexName = auditLogCleanupIndexName;
    this.auditLog = auditLog;
  }

  public static AuditLogHandlerBuilder builder(
      final String indexName,
      final String auditLogCleanupIndexName,
      final AuditLogConfiguration auditLog) {
    return new AuditLogHandlerBuilder(indexName, auditLogCleanupIndexName, auditLog);
  }

  public AuditLogHandlerBuilder addHandler(final AuditLogTransformer<?> transformer) {
    handlers.add(new AuditLogHandler<>(indexName, auditLogCleanupIndexName, transformer, auditLog));
    return this;
  }

  public Set<AuditLogHandler<?>> build() {
    return new HashSet<>(handlers);
  }
}
