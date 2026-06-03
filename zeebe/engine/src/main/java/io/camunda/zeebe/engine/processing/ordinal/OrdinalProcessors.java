/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.ordinal;

import io.camunda.zeebe.engine.EngineConfiguration;
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

  /*
    TODO: @yohanfernando >> Next immediate tasks
    - Initialisation - rework?
      - Should I send initialise command
    - Implement intents
    - Roll over scheduler - DONE
      - roll over strategy
    - Actual data structure
     - for the leader partition
     - for other partitions
     - got to think about keeping current and previous ones live
     - PROPOSED DATA STRUCTURE
        - Ordinal Management Record
          - ordinal key
          - status
          - total
          # FUTURE:
          - latest completed date
          - ILM issued date
          - Delete pending date
          - total created vs completed ???
        - Ordinal Active State
          - active => ordinalKey
        - Ordinal State (all partitions)
          - ordinalKey (FK) + partitionId
          - status
          - create counter
          - complete counter
          - latest completed date

    TODO: @yohanfernando >> Long term:
    - ILM
    - Multiple roll over strategies
    - Report counts back
    - record last closed PI date
  */

  public static void addOrdinalProcessors(
      final TypedRecordProcessors typedRecordProcessors,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final CommandDistributionBehavior commandDistributionBehavior,
      final EngineConfiguration config,
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
        .withListener(new OrdinalInitializer(ordinalState))
        .withListener(
            new OrdinalRolloverScheduler(
                ordinalState, config.getOrdinalRolloverEvaluationInterval()));
  }
}
