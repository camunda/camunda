/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.BpmnTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformerSlot;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.SignalTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import java.time.InstantSource;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TransformerVersionReplayDeterminismTest {

  private static ExpressionLanguage el() {
    return ExpressionLanguageFactory.createExpressionLanguage(
        new ZeebeFeelEngineClock(InstantSource.system()));
  }

  @Test
  void shouldSelectHandlerVersionPerStoredMap() {
    // given — one transformer whose SIGNAL v2 is a counting delegate
    final var v2Runs = new AtomicInteger();
    final var transformer =
        new BpmnTransformer(el(), EngineConfiguration.DEFAULT_MAX_NAME_FIELD_LENGTH);
    transformer.registerHandlerVersion(
        TransformerSlot.SIGNAL, 2, () -> new CountingSignalTransformer(v2Runs));
    // a process that contains a Signal element so the SIGNAL slot executes
    final var model =
        Bpmn.createExecutableProcess("p").startEvent().signal("sig").endEvent().done();

    // when — transform once pinned to SIGNAL v2, once pinned to the empty (all-v1) map
    transformer.transformDefinitions(model, Map.of(TransformerSlot.SIGNAL.id(), 2));
    final int afterV2 = v2Runs.get();
    transformer.transformDefinitions(model, Map.of());
    final int afterV1 = v2Runs.get();

    // then — the v2 handler ran only for the pinned transform; the v1 transform did not touch it
    assertThat(afterV2).isGreaterThan(0);
    assertThat(afterV1).isEqualTo(afterV2);
  }

  /** Delegating marker handler — SignalTransformer is final, so we wrap rather than subclass. */
  private static final class CountingSignalTransformer implements ModelElementTransformer<Signal> {
    private final SignalTransformer delegate = new SignalTransformer();
    private final AtomicInteger runs;

    CountingSignalTransformer(final AtomicInteger runs) {
      this.runs = runs;
    }

    @Override
    public Class<Signal> getType() {
      return Signal.class;
    }

    @Override
    public void transform(final Signal element, final TransformContext context) {
      runs.incrementAndGet();
      delegate.transform(element, context);
    }
  }
}
