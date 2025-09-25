/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.mutable;

import io.camunda.zeebe.engine.common.state.immutable.MigrationState;
import io.camunda.zeebe.engine.common.state.immutable.PendingMessageSubscriptionState;
import io.camunda.zeebe.engine.common.state.immutable.PendingProcessMessageSubscriptionState;

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

  void migrateProcessStateForMultiTenancy();

  void migrateDecisionStateForMultiTenancy();

  void migrateMessageStateForMultiTenancy();

  void migrateMessageStartEventSubscriptionForMultiTenancy();

  void migrateMessageEventSubscriptionForMultiTenancy();

  void migrateProcessMessageSubscriptionForMultiTenancy();

  void migrateJobStateForMultiTenancy();

  void migrateSignalSubscriptionStateForMultiTenancy();

  void setMigratedByVersion(String version);

  void correctColumnFamilyPrefix();

  void migrateOrderedCommandDistribution();

  void migrateIdempotentCommandDistribution();
}
