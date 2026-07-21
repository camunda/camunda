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
 * The identity of the ILM/ISM lifecycle policy a retention-enabled document-based (Elasticsearch /
 * OpenSearch) physical tenant would create: {@code (type, connection, policyName)}.
 *
 * <p>Unlike {@link StorageIdentity}, the index prefix is deliberately <em>absent</em>: a lifecycle
 * policy is a cluster-global named object (Elasticsearch {@code _ilm/policy/<name>}, OpenSearch
 * {@code _plugins/_ism/policies/<name>}), not scoped to any index prefix. Two tenants that share a
 * cluster but use distinct index prefixes — the explicitly-allowed "shared cluster, distinct
 * prefix" setup under {@link SecondaryStorageIsolationValidation} — would nevertheless overwrite
 * each other's lifecycle policy if they resolve to the same {@code (type, connection, policyName)}.
 *
 * <p>The {@code connection} is normalized by {@link StorageIdentity#connectionOf} so this rule and
 * the index-isolation rule agree on what "the same cluster" means. The {@code policyName} is only
 * trimmed — <em>not</em> lowercased — because lifecycle-policy names are case-sensitive identifiers
 * on both engines. {@code type} discriminates Elasticsearch from OpenSearch, mirroring {@link
 * StorageIdentity}: the two engines store policies in different subsystems.
 *
 * @param type the document-based secondary-storage type (elasticsearch or opensearch)
 * @param connection the normalized, sorted connection url(s)
 * @param policyName the trimmed lifecycle-policy name
 */
@NullMarked
record RetentionPolicyIdentity(
    SecondaryStorageType type, List<String> connection, String policyName) {

  static RetentionPolicyIdentity of(
      final SecondaryStorageType type, final DocumentBasedSecondaryStorageDatabase database) {
    return new RetentionPolicyIdentity(
        type, StorageIdentity.connectionOf(database), database.getHistory().getPolicyName().trim());
  }

  /** A human-readable rendering of this identity for error messages. */
  String describe() {
    final String connectionText =
        connection.size() == 1 ? connection.get(0) : connection.toString();
    return String.format(
        "type=%s, connection=%s, policyName='%s'", type, connectionText, policyName);
  }
}
