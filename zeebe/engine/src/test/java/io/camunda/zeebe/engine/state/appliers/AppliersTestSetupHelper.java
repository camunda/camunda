/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.intent.Intent;

class AppliersTestSetupHelper {

  private final EventAppliers eventAppliers;

  AppliersTestSetupHelper(final MutableProcessingState processingState) {
    eventAppliers = new EventAppliers();
    eventAppliers.registerEventAppliers(processingState);
  }

  /**
   * Applies the event of the given intent to the state.
   *
   * @implNote applies the event using the latest version of the record.
   * @param intent the intent of the event to apply
   * @param recordValue data of the event to apply
   */
  void applyEventToState(
      final long key, final Intent intent, final UnifiedRecordValue recordValue) {
    final int latestVersion = eventAppliers.getLatestVersion(intent);
    eventAppliers.applyState(key, intent, recordValue, latestVersion);
  }
}
