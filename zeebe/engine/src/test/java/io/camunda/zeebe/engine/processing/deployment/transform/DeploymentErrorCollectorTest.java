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
    assertThat(message.length()).isLessThanOrEqualTo(maxSize);
    assertThat(message).contains("more errors omitted");
    // no error line is cut mid-string - every included line is a complete, well-formed entry
    assertThat(message.lines().filter(line -> line.contains("unknown resource type")))
        .allMatch(line -> line.matches(".*'resource-\\d+\\.json': unknown resource type"));
  }

  @Test
  void shouldNeverExceedMaxOutputSizeEvenWhenFirstErrorAloneOverflows() {
    // given
    // the fixed prefix alone is 71 chars, so a cap of 90 leaves little room - the forced-in first
    // error overflows the aggregate cap by itself. The hard truncation pass must still guarantee
    // the configured bound rather than just approximating it.
    final var maxOutputSize = 90;
    final var collector = new DeploymentErrorCollector(maxOutputSize);
    collector.add("a single error message that is much longer than the configured cap");
    collector.add("a second error that should be omitted");

    // when
    final var message = collector.formatMessage();

    // then
    assertThat(message.length())
        .as(
            "the aggregate must never exceed the configured cap, even when the forced-in first"
                + " error alone would overflow it")
        .isLessThanOrEqualTo(maxOutputSize);
    assertThat(message).isNotEmpty();
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
        .isLessThanOrEqualTo(maxOutputSize);
  }

  @Test
  void shouldNotThrowWhenMaxOutputSizeIsNegative() {
    // given
    // this codebase uses "-1 disables it" as a convention for other config values (see
    // RocksdbCfg.maxMemoryFraction) - an operator following that convention here, unaware it
    // doesn't apply to this field, must not crash deployment processing on the first error added
    final var collector = new DeploymentErrorCollector(-1);

    // when
    collector.add("some error");
    final var message = collector.formatMessage();

    // then
    assertThat(message).isEmpty();
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
