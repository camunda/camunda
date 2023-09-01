/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public interface DecisionState {

  /**
   * Query decisions by the given decision id and return the latest version of the decision.
   *
   * @param decisionId the id of the decision
   * @param tenantId the tenant the decision belongs to
   * @return the latest version of the decision, or {@link Optional#empty()} if no decision is
   *     deployed with the given id
   */
  Optional<PersistedDecision> findLatestDecisionByIdAndTenant(
      DirectBuffer decisionId, final String tenantId);

  /**
   * Query decisions by the given decision key and return the decision.
   *
   * @param tenantId the tenant the decision belongs to
   * @param decisionKey the key of the decision
   * @return the decision, or {@link Optional#empty()} if no decision is deployed with the given key
   */
  Optional<PersistedDecision> findDecisionByTenantAndKey(final String tenantId, long decisionKey);

  /**
   * Query decision requirements (DRGs) by the given decision requirements id and return the latest
   * version of the DRG.
   *
   * @param tenantId the tenant the DRG belongs to
   * @param decisionRequirementsId the id of the DRG
   * @return the latest version of the DRG, or {@link Optional#empty()} if no DRG is deployed with
   *     the given id
   */
  Optional<DeployedDrg> findLatestDecisionRequirementsByTenantAndId(
      final String tenantId, DirectBuffer decisionRequirementsId);

  /**
   * Query decision requirements (DRGs) by the given decision requirements key.
   *
   * @param tenantId the tenant the DRG belongs to
   * @param decisionRequirementsKey the key of the DRG
   * @return the DRG, or {@link Optional#empty()} if no DRG is deployed with the given key
   */
  Optional<DeployedDrg> findDecisionRequirementsByTenantAndKey(
      final String tenantId, long decisionRequirementsKey);

  /**
   * Query decisions by the given decision requirements (DRG) key.
   *
   * @param tenantId the tenant the DRG belongs to
   * @param decisionRequirementsKey the key of the DRG
   * @return all decisions that belong to the given DRG, or an empty list if no decision belongs to
   *     it
   */
  List<PersistedDecision> findDecisionsByTenantAndDecisionRequirementsKey(
      final String tenantId, long decisionRequirementsKey);

  /** Completely clears all caches. */
  void clearCache();
}
