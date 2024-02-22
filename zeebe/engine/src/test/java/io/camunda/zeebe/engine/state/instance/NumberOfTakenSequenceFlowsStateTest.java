/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.instance;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import org.agrona.DirectBuffer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public final class NumberOfTakenSequenceFlowsStateTest {

  private static final long FLOW_SCOPE_KEY = 1L;
  private static final long OTHER_FLOW_SCOPE_KEY = 2L;
  private static final DirectBuffer GATEWAY_ELEMENT_ID = wrapString("gateway-1");
  private static final DirectBuffer OTHER_GATEWAY_ELEMENT_ID = wrapString("gateway-2");
  private static final DirectBuffer SEQUENCE_FLOW_ELEMENT_ID = wrapString("flow-1");
  private static final DirectBuffer OTHER_SEQUENCE_FLOW_ELEMENT_ID = wrapString("flow-2");

  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableElementInstanceState elementInstanceState;
  private MutableProcessingState processingState;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    elementInstanceState = processingState.getElementInstanceState();
  }

  @Test
  public void shouldReturnZeroIfNoSequenceFlowIsTaken() {
    // given
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);

    // then
    final var number =
        elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(number).isZero();
  }

  @Test
  public void shouldIncrementNumber() {
    // given
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // then
    final var number =
        elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(number).isEqualTo(2);

    final var numberOfOtherScope =
        elementInstanceState.getNumberOfTakenSequenceFlows(
            OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(numberOfOtherScope).isZero();

    final var numberOfOtherGateway =
        elementInstanceState.getNumberOfTakenSequenceFlows(
            FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID);
    assertThat(numberOfOtherGateway).isZero();
  }

  @Test
  public void shouldReturnNumberPerTakenSequenceFlows() {
    // given
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // then
    final var number =
        elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(number).isEqualTo(2);
  }

  @Test
  public void shouldDecrementNumbers() {
    // given
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.decrementNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);

    // then
    final var number =
        elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(number).isZero();

    final var numberOfOtherScope =
        elementInstanceState.getNumberOfTakenSequenceFlows(
            OTHER_FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(numberOfOtherScope).isEqualTo(1);

    final var numberOfOtherGateway =
        elementInstanceState.getNumberOfTakenSequenceFlows(
            FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID);
    assertThat(numberOfOtherGateway).isEqualTo(1);
  }

  @Test
  public void shouldDecrementNumbersButKeepRemaining() {
    // given
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.decrementNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);

    // then
    final var number =
        elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(number).isEqualTo(1);
  }

  @Test
  public void shouldRemoveNumbersWhenDecrementing() {
    // given
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, OTHER_SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.decrementNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);

    // then
    final var number =
        elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(number).isZero();

    assertThat(processingState.isEmpty(ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS))
        .describedAs("Expected the entries to be removed")
        .isTrue();
  }

  @Test
  public void shouldRemoveNumbersWhenRemovingTheScope() {
    // given
    elementInstanceState.newInstance(
        FLOW_SCOPE_KEY, new ProcessInstanceRecord(), ProcessInstanceIntent.ELEMENT_ACTIVATED);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);
    elementInstanceState.incrementNumberOfTakenSequenceFlows(
        FLOW_SCOPE_KEY, OTHER_GATEWAY_ELEMENT_ID, SEQUENCE_FLOW_ELEMENT_ID);

    // when
    elementInstanceState.removeInstance(FLOW_SCOPE_KEY);

    // then
    final var number =
        elementInstanceState.getNumberOfTakenSequenceFlows(FLOW_SCOPE_KEY, GATEWAY_ELEMENT_ID);
    assertThat(number).isZero();

    assertThat(processingState.isEmpty(ZbColumnFamilies.NUMBER_OF_TAKEN_SEQUENCE_FLOWS))
        .describedAs("Expected the entries to be removed")
        .isTrue();
  }
}
