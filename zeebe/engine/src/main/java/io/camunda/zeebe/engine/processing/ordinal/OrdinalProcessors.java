/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinal;

import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.OrdinalState;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.OrdinalIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

// TODO: @yohanfernando >> chain processors
public final class OrdinalProcessors {
  private OrdinalProcessors() {}

  public static void addOrdinalProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior,
      final OrdinalState ordinalState) {

    // TODO: @yohanfernando >> require command processors for
    //  a) activate
    //  b) complete and all other lifecycle commands
    //  c) issue new (pending) ordinal
    //  d) roll over
    //  AND any other action this would do
    typedRecordProcessors
        .onCommand(
            ValueType.ORDINAL,
            OrdinalIntent.ACTIVATE,
            new OrdinalActivateProcessor(writers, keyGenerator, commandDistributionBehavior))
        // TODO: @yohanfernando >> Require listeners for
        //  a) initialisation
        //  b) roll over check (scheduled)
        //  c) ILM check (scheduled)
        .withListener(new OrdinalInitializer(ordinalState));
  }
}
