/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.transformer.SignalTransformer;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.Signal;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class BpmnTransformerVersioningTest {

  private static ExpressionLanguage expressionLanguage() {
    return ExpressionLanguageFactory.createExpressionLanguage(
        new ZeebeFeelEngineClock(InstantSource.system()), ExpressionLanguageMetrics.noop());
  }

  private static BpmnModelInstance signalModel() {
    return Bpmn.createExecutableProcess("p")
        .startEvent()
        .intermediateCatchEvent("catch")
        .signal("sig")
        .endEvent()
        .done();
  }

  @Test
  void shouldTransformIdenticallyWithEmptyVersions() {
    // given
    final var model =
        Bpmn.createExecutableProcess("p")
            .startEvent("s")
            .serviceTask("t", t -> t.zeebeJobType("w"))
            .endEvent("e")
            .done();
    final var transformer = new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE);

    // when
    final List<ExecutableProcess> result = transformer.transformDefinitions(model);

    // then — the default (v1) pipeline produces the expected element types
    assertThat(result).hasSize(1);
    final ExecutableFlowElement task =
        result.get(0).getElementById("t", ExecutableFlowElement.class);
    assertThat(task.getElementType()).isEqualTo(BpmnElementType.SERVICE_TASK);
  }

  @Test
  void shouldUseRegisteredV2WhenSlotVersionPinned() {
    // given — a delegating v2 SIGNAL handler that records it ran
    final var ran = new AtomicBoolean(false);
    final var transformer = new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE);
    transformer.registerHandlerVersion(
        TransformerSlot.SIGNAL, 2, () -> new RecordingSignalTransformer(ran));

    // when — replay pins SIGNAL to v2 via the stored slot-id map
    transformer.transformDefinitions(signalModel(), Map.of(TransformerSlot.SIGNAL, 2));

    // then
    assertThat(ran).isTrue();
  }

  @Test
  void shouldUseLatestRegisteredVersionsWhenNoMapGiven() {
    // given — v2 registered; the no-map overload is the deploy-time (latest) pipeline
    final var ran = new AtomicBoolean(false);
    final var transformer = new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE);
    transformer.registerHandlerVersion(
        TransformerSlot.SIGNAL, 2, () -> new RecordingSignalTransformer(ran));

    // when
    transformer.transformDefinitions(signalModel());

    // then — deploy time uses the latest version, matching what currentVersionsById() stamps
    assertThat(ran).isTrue();
  }

  @Test
  void shouldThrowWhenPinnedVersionHasNoRegisteredHandler() {
    // given — nothing registered above v1
    final var transformer = new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE);

    // when / then — fail fast rather than silently fall back to v1, which would diverge from
    // the leader's pipeline
    assertThatThrownBy(
            () ->
                transformer.transformDefinitions(signalModel(), Map.of(TransformerSlot.SIGNAL, 2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SIGNAL")
        .hasMessageContaining("version 2");
  }

  @Test
  void shouldThrowUpfrontEvenWhenElementIsAbsentFromModel() {
    // given — a model with no signal element at all
    final var model = Bpmn.createExecutableProcess("p").startEvent("s").endEvent("e").done();
    final var transformer = new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE);

    // when / then — the version map is validated upfront, not lazily per visited element
    assertThatThrownBy(
            () -> transformer.transformDefinitions(model, Map.of(TransformerSlot.SIGNAL, 2)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SIGNAL")
        .hasMessageContaining("version 2");
  }

  @Test
  void shouldRejectRegisteringVersionOne() {
    // given
    final var transformer = new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE);

    // when / then — v1 lives in the constructor, not in registerHandlerVersion
    assertThatThrownBy(
            () ->
                transformer.registerHandlerVersion(
                    TransformerSlot.SIGNAL, 1, SignalTransformer::new))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldReturnEmptyCurrentVersionsByIdByDefault() {
    // given / when / then — no v2+ handlers registered → nothing to stamp into ProcessRecord
    assertThat(new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE).currentVersionsById())
        .isEmpty();
  }

  @Test
  void shouldReturnOnlySlotsAboveDefaultVersionInCurrentVersionsById() {
    // given — one slot bumped to v2, all others at implicit v1
    final var transformer = new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE);
    transformer.registerHandlerVersion(TransformerSlot.SIGNAL, 2, SignalTransformer::new);

    // when
    final var result = transformer.currentVersionsById();

    // then — only the v2 slot appears; v1 slots are omitted (sparse representation)
    assertThat(result).containsExactly(Map.entry(TransformerSlot.SIGNAL.id(), 2));
  }

  @Test
  void shouldExposeRegisteredVersionsViaBpmnTransformerInstance() {
    // given — a transformer with a probe v2 for SIGNAL
    final var transformer = new BpmnTransformer(expressionLanguage(), Integer.MAX_VALUE);
    transformer.registerHandlerVersion(TransformerSlot.SIGNAL, 2, SignalTransformer::new);

    // when — the same instance that transforms is the one that stamps
    final var stampedVersions = transformer.currentVersionsById();

    // then — the stamped map carries the probe registration, matching what writeRecords would stamp
    assertThat(stampedVersions).containsExactly(Map.entry(TransformerSlot.SIGNAL.id(), 2));
  }

  /** Delegating marker handler — SignalTransformer is final, so we wrap rather than subclass. */
  private static final class RecordingSignalTransformer implements ModelElementTransformer<Signal> {
    private final SignalTransformer delegate = new SignalTransformer();
    private final AtomicBoolean ran;

    RecordingSignalTransformer(final AtomicBoolean ran) {
      this.ran = ran;
    }

    @Override
    public Class<Signal> getType() {
      return Signal.class;
    }

    @Override
    public void transform(final Signal element, final TransformContext context) {
      ran.set(true);
      delegate.transform(element, context);
    }
  }
}
