/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class DeploymentErrorCollectorTest {

  @Test
  void shouldNotCapMessageWhenWithinMaxSize() {
    // given
    final var collector = new DeploymentErrorCollector(1024);
    collector.add("'foo.bpmn': unknown resource type");
    collector.add("'bar.bpmn': unknown resource type");

    // when
    final var message = collector.formatMessage();

    // then
    assertThat(message)
        .contains("'foo.bpmn': unknown resource type")
        .contains("'bar.bpmn': unknown resource type")
        .doesNotContain("omitted");
  }

  @Test
  void shouldCapMessageAtMaxSizeAndReportOmittedCount() {
    // given
    final var maxSize = 200;
    final var collector = new DeploymentErrorCollector(maxSize);
    for (var i = 0; i < 200; i++) {
      collector.add("'resource-%d.json': unknown resource type", i);
    }

    // when
    final var message = collector.formatMessage();

    // then
    assertThat(message.length()).isLessThan(maxSize + 100);
    assertThat(message).contains("more errors omitted");
    // no error line is cut mid-string - every included line is a complete, well-formed entry
    assertThat(message.lines().filter(line -> line.contains("unknown resource type")))
        .allMatch(line -> line.matches(".*'resource-\\d+\\.json': unknown resource type"));
  }

  @Test
  void shouldAlwaysIncludeAtLeastOneErrorEvenIfItExceedsMaxSize() {
    // given
    // the prefix alone already exceeds this cap, so even a single (truncated) error forces the
    // aggregate over budget - the collector must still emit it rather than an empty error list
    final var collector = new DeploymentErrorCollector(10);
    collector.add("a single error message that is much longer than the configured cap");
    collector.add("a second error that should be omitted");

    // when
    final var message = collector.formatMessage();

    // then
    assertThat(message)
        .contains("1 more errors omitted")
        .doesNotContain("a second error that should be omitted");
  }

  @Test
  void shouldCapIndividualErrorMessageLengthEvenWhenOnlyOneErrorIsCollected() {
    // given
    // simulates DeploymentTransformer's catch(RuntimeException) sites, which add an exception's
    // getMessage() verbatim - that message is not itself size-bounded (e.g. it could embed a large
    // offending input), so a single such error must not be able to reintroduce an unbounded
    // rejection reason on its own - see https://github.com/camunda/camunda/issues/61996
    final var maxOutputSize = 100;
    final var collector = new DeploymentErrorCollector(maxOutputSize);
    final var hugeExceptionMessage = "x".repeat(10_000);

    // when
    collector.add("'some-resource.json': %s", hugeExceptionMessage);
    final var message = collector.formatMessage();

    // then
    assertThat(message.length())
        .as("a single oversized error must not blow past the configured cap")
        .isLessThan(maxOutputSize + 100);
  }

  @Test
  void shouldReportNoErrorsWhenNoneAdded() {
    // given
    final var collector = new DeploymentErrorCollector(1024);

    // when
    final var hasErrors = collector.hasErrors();

    // then
    assertThat(hasErrors).isFalse();
  }
}
