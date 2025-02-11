/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer.zeebe;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.util.FakeExpressionLanguage;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeExecutionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ExecutionListenerTransformerTest {

  @Test
  void shouldNotAddListenersIfNoneProvided() {
    // given
    final var process = new ExecutableProcess("process");

    // when
    final Collection<ZeebeExecutionListener> listeners = List.of();
    new ExecutionListenerTransformer().transform(process, listeners, new FakeExpressionLanguage());

    // then
    Assertions.assertThat(process.hasExecutionListeners()).isFalse();
  }

  @Test
  void shouldAddProvidedListener() {
    // given
    final var process = new ExecutableProcess("process");

    // when
    final var listener = new FakeZeebeExecutionListener("start", "type", "3");
    new ExecutionListenerTransformer()
        .transform(process, List.of(listener), new FakeExpressionLanguage());

    // then
    Assertions.assertThat(process.hasExecutionListeners()).isTrue();
  }

  @Nested
  class ShouldIgnoreMisconfiguredListeners {

    @Test
    void forNullListener() {
      // given
      final var process = new ExecutableProcess("process");

      // when
      final var listeners = new ArrayList<ZeebeExecutionListener>();
      listeners.add(null);
      new ExecutionListenerTransformer()
          .transform(process, listeners, new FakeExpressionLanguage());

      // then
      Assertions.assertThat(process.hasExecutionListeners()).isFalse();
    }

    @Test
    void forMisconfiguredListenerWithoutEventType() {
      // given
      final var process = new ExecutableProcess("process");

      // when
      final var listeners = new ArrayList<ZeebeExecutionListener>();
      listeners.add(new FakeZeebeExecutionListener(null, "type", "3"));
      new ExecutionListenerTransformer()
          .transform(process, listeners, new FakeExpressionLanguage());

      // then
      Assertions.assertThat(process.hasExecutionListeners()).isFalse();
    }

    @Test
    void forMisconfiguredListenerWithoutType() {
      // given
      final var process = new ExecutableProcess("process");

      // when
      final var listeners = new ArrayList<ZeebeExecutionListener>();
      listeners.add(new FakeZeebeExecutionListener("start", null, "3"));
      new ExecutionListenerTransformer()
          .transform(process, listeners, new FakeExpressionLanguage());

      // then
      Assertions.assertThat(process.hasExecutionListeners()).isFalse();
    }

    @Test
    void forMisconfiguredListenerWithoutRetries() {
      // given
      final var process = new ExecutableProcess("process");

      // when
      final var listeners = new ArrayList<ZeebeExecutionListener>();
      listeners.add(new FakeZeebeExecutionListener("start", "type", null));
      new ExecutionListenerTransformer()
          .transform(process, listeners, new FakeExpressionLanguage());

      // then
      Assertions.assertThat(process.hasExecutionListeners()).isFalse();
    }
  }
}
