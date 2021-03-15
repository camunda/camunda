/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public final class ProcessTest {

  private StartProcess helper;

  @Before
  public void setup() {
    helper = new StartProcess("1");
  }

  @Test
  public void shouldStartInOrder() throws Exception {
    // given
    final List<Integer> order = new ArrayList<>();
    helper.addStep("first step", () -> order.add(1));
    helper.addStep("second step", () -> order.add(2));

    // when
    helper.start();

    // then
    assertThat(order).containsExactly(1, 2);
  }

  @Test
  public void shouldCloseInReverseOrder() throws Exception {
    // given
    final List<Integer> order = new ArrayList<>();
    final List<Integer> closeOrder = new ArrayList<>();
    helper.addStep(
        "first step",
        () -> {
          order.add(1);
          return () -> closeOrder.add(1);
        });
    helper.addStep(
        "second step",
        () -> {
          order.add(2);
          return () -> closeOrder.add(2);
        });
    final CloseProcess closeProcess = helper.start();

    // when
    closeProcess.closeReverse();

    // then
    assertThat(order).containsExactly(1, 2);
    assertThat(closeOrder).containsExactly(2, 1);
  }

  @Test
  public void shouldCloseResourcesAfterStartFailure() {
    // given
    final List<Integer> order = new ArrayList<>();
    final List<Integer> closeOrder = new ArrayList<>();
    helper.addStep(
        "first step",
        () -> {
          order.add(1);
          return () -> closeOrder.add(1);
        });
    helper.addStep(
        "failing step",
        () -> {
          order.add(2);
          return () -> closeOrder.add(2);
        });
    helper.addStep(
        "third step",
        () -> {
          throw new Exception("expected");
        });
    helper.addStep(
        "fourth step",
        () -> {
          order.add(4);
          return () -> closeOrder.add(3);
        });

    // when
    assertThatThrownBy(() -> helper.start()).isInstanceOf(Exception.class);

    // then
    assertThat(order).containsExactly(1, 2);
    assertThat(closeOrder).containsExactly(2, 1);
  }

  @Test
  public void shouldCloseRemainingStepsOnCloseFailure() throws Exception {
    // given
    final List<Integer> order = new ArrayList<>();
    final List<Integer> closeOrder = new ArrayList<>();
    helper.addStep(
        "first step",
        () -> {
          order.add(1);
          return () -> closeOrder.add(1);
        });
    helper.addStep(
        "failing step",
        () -> {
          order.add(2);
          return () -> {
            throw new Exception("expected");
          };
        });
    helper.addStep(
        "third step",
        () -> {
          order.add(3);
          return () -> closeOrder.add(3);
        });
    final CloseProcess closeProcess = helper.start();

    // when
    closeProcess.closeReverse();

    // then
    assertThat(order).containsExactly(1, 2, 3);
    assertThat(closeOrder).containsExactly(3, 1);
  }
}
