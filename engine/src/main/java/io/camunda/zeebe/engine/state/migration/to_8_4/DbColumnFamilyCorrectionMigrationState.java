/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_4;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.migration.to_8_4.corrections.DmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector;
import io.camunda.zeebe.engine.state.migration.to_8_4.corrections.SignalNameAndSubscriptionKeyColumnFamilyCorrector;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public final class DbColumnFamilyCorrectionMigrationState {

  private final SignalNameAndSubscriptionKeyColumnFamilyCorrector
      signalNameAndSubscriptionKeyColumnFamilyCorrector;
  private final DmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector
      dmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector;

  public DbColumnFamilyCorrectionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    signalNameAndSubscriptionKeyColumnFamilyCorrector =
        new SignalNameAndSubscriptionKeyColumnFamilyCorrector(zeebeDb, transactionContext);
    dmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector =
        new DmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector(zeebeDb, transactionContext);
  }

  public void correctColumnFamilyPrefix() {
    signalNameAndSubscriptionKeyColumnFamilyCorrector.correctColumnFamilyPrefix();
    dmnDecisionKeyByDecisionIdAndVersionColumnFamilyCorrector.correctColumnFamilyPrefix();
  }
}
