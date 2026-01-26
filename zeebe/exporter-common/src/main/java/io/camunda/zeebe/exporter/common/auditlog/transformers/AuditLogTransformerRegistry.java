/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog.transformers;

import io.camunda.zeebe.util.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Registry providing a single source of truth for all audit log transformers. This prevents
 * duplication of transformer lists across exporters and tests.
 *
 * <p>Transformers are categorized into two groups:
 *
 * <ul>
 *   <li><b>Partition-specific transformers:</b> Only run on partition 1
 *       (PROCESS_DEFINITION_PARTITION) for definition-related records like processes, decisions,
 *       forms, and identity records.
 *   <li><b>All-partition transformers:</b> Run on every partition for instance-related records like
 *       process instances, user tasks, and variables.
 * </ul>
 */
public final class AuditLogTransformerRegistry {

  private AuditLogTransformerRegistry() {
    // Utility class
  }

  /**
   * Creates new instances of transformers that should only run on partition 1
   * (PROCESS_DEFINITION_PARTITION). These handle definition-related records.
   *
   * @return list of new transformer instances for partition 1
   */
  public static List<AuditLogTransformer<?>> createPartitionSpecificTransformers() {
    return getSourcePartitionTransformerSuppliers().stream()
        .map(Supplier::get)
        .collect(Collectors.toList());
  }

  /**
   * Creates new instances of transformers that run on all partitions. These handle instance-related
   * records.
   *
   * @return list of new transformer instances for all partitions
   */
  public static List<AuditLogTransformer<?>> createAllPartitionTransformers() {
    return getAllPartitionTransformerSuppliers().stream()
        .map(Supplier::get)
        .collect(Collectors.toList());
  }

  /**
   * Creates new instances of all transformers. This is useful for tests that need to verify all
   * transformers are properly configured.
   *
   * @return list of all new transformer instances
   */
  @VisibleForTesting
  public static List<AuditLogTransformer<?>> createAllTransformers() {
    return getAllTransformerSuppliers().stream().map(Supplier::get).collect(Collectors.toList());
  }

  /**
   * Returns suppliers for transformers that should only run on partition 1. Suppliers are used to
   * create fresh instances when needed.
   *
   * @return list of transformer suppliers for partition 1
   */
  public static List<Supplier<AuditLogTransformer<?>>> getSourcePartitionTransformerSuppliers() {
    return List.of(
        AuthorizationAuditLogTransformer::new,
        BatchOperationCreationAuditLogTransformer::new,
        DecisionRequirementsRecordAuditLogTransformer::new,
        DecisionAuditLogTransformer::new,
        FormAuditLogTransformer::new,
        GroupAuditLogTransformer::new,
        GroupEntityAuditLogTransformer::new,
        MappingRuleAuditLogTransformer::new,
        ProcessAuditLogTransformer::new,
        ResourceAuditLogTransformer::new,
        RoleAuditLogTransformer::new,
        RoleEntityAuditLogTransformer::new,
        TenantAuditLogTransformer::new,
        TenantEntityAuditLogTransformer::new,
        UserAuditLogTransformer::new);
  }

  /**
   * Returns suppliers for transformers that run on all partitions. Suppliers are used to create
   * fresh instances when needed.
   *
   * @return list of transformer suppliers for all partitions
   */
  public static List<Supplier<AuditLogTransformer<?>>> getAllPartitionTransformerSuppliers() {
    return List.of(
        BatchOperationLifecycleManagementAuditLogTransformer::new,
        DecisionEvaluationAuditLogTransformer::new,
        IncidentResolutionAuditLogTransformer::new,
        ProcessInstanceCancelAuditLogTransformer::new,
        ProcessInstanceCreationAuditLogTransformer::new,
        ProcessInstanceMigrationAuditLogTransformer::new,
        ProcessInstanceModificationAuditLogTransformer::new,
        UserTaskAuditLogTransformer::new,
        VariableAddUpdateAuditLogTransformer::new);
  }

  /**
   * Returns suppliers for all transformers. Suppliers are used to create fresh instances when
   * needed.
   *
   * @return list of all transformer suppliers
   */
  private static List<Supplier<AuditLogTransformer<?>>> getAllTransformerSuppliers() {
    final List<Supplier<AuditLogTransformer<?>>> allSuppliers = new ArrayList<>();
    allSuppliers.addAll(getSourcePartitionTransformerSuppliers());
    allSuppliers.addAll(getAllPartitionTransformerSuppliers());
    return Collections.unmodifiableList(allSuppliers);
  }
}
