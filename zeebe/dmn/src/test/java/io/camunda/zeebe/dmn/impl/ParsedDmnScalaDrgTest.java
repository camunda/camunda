/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dmn.impl;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.dmn.EvaluatedDecision;
import io.camunda.zeebe.dmn.ParsedDecision;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import org.camunda.dmn.DmnEngine;
import org.camunda.dmn.parser.ParsedDmn;
import org.junit.jupiter.api.Test;

class ParsedDmnScalaDrgTest {

  private static final String DRG_WITH_REQUIRED_DECISION = "/drg-force-user.dmn";
  private static final String DECISION_WITH_VERSION_TAG = "/decision-table-with-version-tag.dmn";
  private static final String DECISION_WITHOUT_ID_WITH_VERSION_TAG =
      "/decision-without-id-with-version-tag.dmn";

  private final DmnEngine dmnEngine = new DmnEngine.Builder().build();

  @Test
  void shouldNotRetainDmnModel() {
    // given
    final var modelRef = new AtomicReference<WeakReference<Object>>();

    // when
    final var drg = createDrgAndTrackModel(DRG_WITH_REQUIRED_DECISION, modelRef);

    // then
    try {
      assertThat(isCollected(modelRef.get()))
          .describedAs("Expect that the DMN model is not reachable from the parsed DRG")
          .isTrue();
      assertThat(drg.getId()).isEqualTo("force_users");
      assertThat(drg.getName()).isEqualTo("force_users");
      assertThat(drg.getNamespace()).isEqualTo("http://camunda.org/schema/1.0/dmn");
    } finally {
      // keeps the DRG strongly reachable during collection; otherwise the JIT may collect it
      // early and the model would be freed even if the DRG still referenced it
      Reference.reachabilityFence(drg);
    }
  }

  @Test
  void shouldEvaluateRequiredDecisionWithoutDmnModel() {
    // given
    final var drg = ParsedDmnScalaDrg.of(parse(DRG_WITH_REQUIRED_DECISION));

    // when
    final var result =
        new DmnScalaDecisionEngine()
            .evaluateDecisionById(
                drg,
                "force_user",
                new VariablesContext(
                    Map.of("lightsaberColor", asMsgPack("\"blue\""), "height", asMsgPack("185"))));

    // then
    assertThat(result.isFailure()).describedAs(result.getFailureMessage()).isFalse();
    assertThat(result.getEvaluatedDecisions())
        .extracting(EvaluatedDecision::decisionId)
        .containsExactly("jedi_or_sith", "force_user");
  }

  @Test
  void shouldProvideVersionTagOfDecision() {
    // when
    final var drg = ParsedDmnScalaDrg.of(parse(DECISION_WITH_VERSION_TAG));

    // then
    assertThat(drg.getVersionTag("jedi_or_sith")).hasValue("v1.0");
    assertThat(drg.getVersionTag("untagged_decision")).isEmpty();
    assertThat(drg.getVersionTag("unknown_decision")).isEmpty();
  }

  @Test
  void shouldParseDecisionWithoutIdButWithVersionTag() {
    // when
    final var drg = ParsedDmnScalaDrg.of(parse(DECISION_WITHOUT_ID_WITH_VERSION_TAG));

    // then
    assertThat(drg.isValid()).isTrue();
    assertThat(drg.getDecisions()).extracting(ParsedDecision::getName).containsExactly("No ID");
  }

  @Test
  void shouldProvideNoVersionTagIfAbsent() {
    // when
    final var drg = ParsedDmnScalaDrg.of(parse(DRG_WITH_REQUIRED_DECISION));

    // then
    assertThat(drg.getVersionTag("jedi_or_sith")).isEmpty();
    assertThat(drg.getVersionTag("force_user")).isEmpty();
  }

  // Parses in a separate frame so that the test holds no reference to the original ParsedDmn.
  private ParsedDmnScalaDrg createDrgAndTrackModel(
      final String resource, final AtomicReference<WeakReference<Object>> modelRef) {
    final var parsedDmn = parse(resource);
    modelRef.set(new WeakReference<>(parsedDmn.model()));
    return ParsedDmnScalaDrg.of(parsedDmn);
  }

  private ParsedDmn parse(final String resource) {
    return dmnEngine.parse(getClass().getResourceAsStream(resource)).toOption().get();
  }

  // System.gc() is only a hint, so retry before concluding that the model is still reachable.
  private static boolean isCollected(final WeakReference<?> ref) {
    return IntStream.range(0, 20)
        .anyMatch(
            i -> {
              System.gc();
              return ref.get() == null;
            });
  }
}
