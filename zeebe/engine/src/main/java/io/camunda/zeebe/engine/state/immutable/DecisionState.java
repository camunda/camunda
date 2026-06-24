/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.immutable;

import io.camunda.zeebe.engine.state.deployment.DeployedDrg;
import io.camunda.zeebe.engine.state.deployment.PersistedDecision;
import io.camunda.zeebe.engine.state.deployment.PersistedDecisionRequirements;
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
   * Query decisions by the given decision id and deployment key and return the decision.
   *
   * @param tenantId the tenant the decision belongs to
   * @param decisionId the id of the decision
   * @param deploymentKey the key of the deployment the decision was deployed with
   * @return the decision, or {@link Optional#empty()} if no decision with the given id was deployed
   *     with the given deployment
   */
  Optional<PersistedDecision> findDecisionByIdAndDeploymentKey(
      final String tenantId, DirectBuffer decisionId, long deploymentKey);

  /**
   * Query decisions by the given decision id and version tag and return the decision.
   *
   * @param tenantId the tenant the decision belongs to
   * @param decisionId the id of the decision
   * @param versionTag the version tag of the decision
   * @return the decision, or {@link Optional#empty()} if no decision with the given id and version
   *     tag is deployed
   */
  Optional<PersistedDecision> findDecisionByIdAndVersionTag(
      final String tenantId, DirectBuffer decisionId, String versionTag);

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

  /**
   * Iterates over all persisted decision requirements until the visitor returns false or all
   * decision requirements have been visited. If {@code previousDecisionRequirements} is not null,
   * the iteration skips all decision requirements that appear before it. The visitor is
   * <em>not</em> called with a copy of the decision requirements to avoid needless copies of the
   * relatively large {@link PersistedDecisionRequirements} instances.
   */
  void forEachDecisionRequirements(
      final DecisionRequirementsIdentifier previousDecisionsRequirements,
      final PersistedDecisionRequirementsVisitor visitor);

  /** Completely clears all caches. */
  void clearCache();

  record DecisionRequirementsIdentifier(String tenantId, long decisionRequirementsKey)
      implements ResourceIdentifier {}

  interface PersistedDecisionRequirementsVisitor {
    boolean visit(PersistedDecisionRequirements decisionRequirements);
  }

  interface PersistedDecisionVisitor {
    boolean visit(PersistedDecision decision);
  }
}
