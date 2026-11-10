/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.instance;

import static java.util.function.Predicate.not;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.Arrays;
import org.junit.jupiter.api.extension.ExtendWith;

/** Runs the element instance state contract against the RocksDB state. */
@ExtendWith(ProcessingStateExtension.class)
public final class ElementInstanceStateTest extends AbstractElementInstanceStateContractTest {

  private MutableProcessingState processingState;

  @Override
  protected MutableElementInstanceState createElementInstanceState() {
    return processingState.getElementInstanceState();
  }

  @Override
  protected MutableVariableState variableState() {
    return processingState.getVariableState();
  }

  @Override
  protected void assertNoStateLeft() {
    assertNoColumnFamilyLeft(processingState);
  }

  public static void assertNoColumnFamilyLeft(final MutableProcessingState processingState) {
    final var nonEmptyColumns =
        Arrays.stream(ZbColumnFamilies.values())
            .filter(not(ZbColumnFamilies.KEY::equals))
            // ACTIVE_PROCESS_INSTANCE_COUNT tracks the count of active process instances
            .filter(not(ZbColumnFamilies.ACTIVE_PROCESS_INSTANCE_COUNT::equals))
            .filter(not(processingState::isEmpty))
            .toList();

    assertThat(nonEmptyColumns).describedAs("Expected all columns to be empty").isEmpty();
  }
}
