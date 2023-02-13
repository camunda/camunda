/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.DecisionState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DecisionRequirementsRecord;

public interface MutableDecisionState extends DecisionState {

  /**
   * Put the given decision in the state. Update the latest version of the decision if it is newer.
   *
   * @param record the record of the decision
   */
  void storeDecisionRecord(DecisionRecord record);

  /**
   * Put the given decision requirements (DRG) in the state. Update the latest version of the DRG if
   * it is newer.
   *
   * @param record the record of the DRG
   */
  void storeDecisionRequirements(DecisionRequirementsRecord record);

  /**
   * Deletes a decision from the state. Updates the latest version of the decision if the deleted
   * version is the latest version and a previous version is available.
   *
   * @param record the record of the decision
   */
  void deleteDecision(DecisionRecord record);
}
