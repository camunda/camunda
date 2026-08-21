/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.physicaltenants;

import io.camunda.configuration.DocumentBasedSecondaryStorageDatabase;
import io.camunda.configuration.SecondaryStorage.SecondaryStorageType;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * The identity of the ILM/ISM <em>usage-metrics</em> lifecycle policy a retention-enabled
 * document-based (Elasticsearch / OpenSearch) physical tenant would create: {@code (type,
 * connection, usageMetricsPolicyName)}.
 *
 * <p>This is deliberately a separate identity type from {@link RetentionPolicyIdentity} rather than
 * a widened version of it. The general policy name and the usage-metrics policy name are two
 * independent cluster-global named objects; two tenants can share one without sharing the other.
 * Folding both names into a single composite key would weaken {@link
 * RetentionPolicyIsolationValidation}'s existing rule: tenants A {@code (policy=p,
 * usageMetrics=u1)} and B {@code (policy=p, usageMetrics=u2)} would land in different buckets and
 * their real collision on {@code p} would stop being reported. Keeping the two identities — and the
 * two groupings built from them — independent preserves both checks.
 *
 * @param type the document-based secondary-storage type (elasticsearch or opensearch)
 * @param connection the normalized, sorted connection url(s)
 * @param usageMetricsPolicyName the trimmed usage-metrics lifecycle-policy name
 */
@NullMarked
record UsageMetricsPolicyIdentity(
    SecondaryStorageType type, List<String> connection, String usageMetricsPolicyName) {

  static UsageMetricsPolicyIdentity of(
      final SecondaryStorageType type, final DocumentBasedSecondaryStorageDatabase database) {
    return new UsageMetricsPolicyIdentity(
        type,
        StorageIdentity.connectionOf(database),
        database.getHistory().getUsageMetricsPolicyName().trim());
  }

  /** A human-readable rendering of this identity for error messages. */
  String describe() {
    final String connectionText =
        connection.size() == 1 ? connection.get(0) : connection.toString();
    return String.format(
        "type=%s, connection=%s, usageMetricsPolicyName='%s'",
        type, connectionText, usageMetricsPolicyName);
  }
}
