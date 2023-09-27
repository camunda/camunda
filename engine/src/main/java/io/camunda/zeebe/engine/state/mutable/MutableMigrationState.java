/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.mutable;

import io.camunda.zeebe.engine.state.immutable.MigrationState;
import io.camunda.zeebe.engine.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.state.immutable.PendingProcessMessageSubscriptionState;

public interface MutableMigrationState extends MigrationState {

  void migrateMessageSubscriptionSentTime(
      final MutableMessageSubscriptionState messageSubscriptionState,
      final PendingMessageSubscriptionState transientState);

  void migrateProcessMessageSubscriptionSentTime(
      final MutableProcessMessageSubscriptionState persistentSate,
      final PendingProcessMessageSubscriptionState transientState);

  void migrateTemporaryVariables(
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final MutableElementInstanceState elementInstanceState);

  void migrateDecisionsPopulateDecisionVersionByDecisionIdAndDecisionKey();

  void migrateDrgPopulateDrgVersionByDrgIdAndKey();

  void migrateElementInstancePopulateProcessInstanceByDefinitionKey();

  void migrateProcessDefinitionVersions();

  void migrateProcessStateForMultiTenancy();

  void migrateDecisionStateForMultiTenancy();

  /**
   * Changes the state of a migration to FINISHED to indicate it has been executed.
   *
   * @param identifier the identifier of the migration
   */
  void markMigrationFinished(final String identifier);
}
