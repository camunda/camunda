/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.immutable;

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
   * @return the latest version of the decision, or {@link Optional#empty()} if no decision is
   *     deployed with the given id
   */
  Optional<PersistedDecision> findLatestDecisionById(DirectBuffer decisionId);

  /**
   * Query decisions by the given decision key and return the decision.
   *
   * @param decisionKey the key of the decision
   * @return the decision, or {@link Optional#empty()} if no decision is deployed with the given key
   */
  Optional<PersistedDecision> findDecisionByKey(long decisionKey);

  /**
   * Query decision requirements (DRGs) by the given decision requirements id and return the latest
   * version of the DRG.
   *
   * @param decisionRequirementsId the id of the DRG
   * @return the latest version of the DRG, or {@link Optional#empty()} if no DRG is deployed with
   *     the given id
   */
  Optional<PersistedDecisionRequirements> findLatestDecisionRequirementsById(
      DirectBuffer decisionRequirementsId);

  /**
   * Query decision requirements (DRGs) by the given decision requirements key.
   *
   * @param decisionRequirementsKey the key of the DRG
   * @return the DRG, or {@link Optional#empty()} if no DRG is deployed with the given key
   */
  Optional<PersistedDecisionRequirements> findDecisionRequirementsByKey(
      long decisionRequirementsKey);

  /**
   * Query decisions by the given decision requirements (DRG) key.
   *
   * @param decisionRequirementsKey the key of the DRG
   * @return all decisions that belong to the given DRG, or an empty list if no decision belongs to
   *     it
   */
  List<PersistedDecision> findDecisionsByDecisionRequirementsKey(long decisionRequirementsKey);
}
