/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.cluster.migration;

import java.util.Map;
import org.jspecify.annotations.NullMarked;

/**
 * Reports whether a single upgrade-readiness condition is met, per physical tenant
 *
 * <p>Some conditions are local to this node (e.g. schema-version checks against centralized, shared
 * secondary storage); others need to resolve state distributed across partitions and replicas.
 */
@NullMarked
public interface MigrationStatusProvider {

  /**
   * @return the stable identifier for this condition.
   */
  String conditionName();

  /**
   * @return the current status of this condition, keyed by physical tenant ID. Must not throw;
   *     report {@link MigrationState#UNKNOWN} for a given tenant instead of omitting it whenever a
   *     failure is scoped to that tenant.
   */
  Map<String, MigrationConditionStatus> getMigrationStatus();
}
