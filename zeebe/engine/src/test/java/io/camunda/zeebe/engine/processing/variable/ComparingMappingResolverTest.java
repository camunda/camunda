/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.test.util.logging.RecordingAppender;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ComparingMappingResolverTest {

  private static final MappingContext CONTEXT =
      new MappingContext(BufferUtil.wrapString("element-1"), 100L, 200L, 300L, "default");

  private static final InputMappings INPUT_MAPPINGS = mock(InputMappings.class);
  private static final MappingExpressionProcessor PROCESSOR =
      mock(MappingExpressionProcessor.class);

  private final RecordingAppender recorder = new RecordingAppender();
  private Logger log4jLogger;

  @BeforeEach
  void setUp() {
    log4jLogger = (Logger) LogManager.getLogger(ComparingMappingResolver.class);
    recorder.start();
    log4jLogger.addAppender(recorder);
    log4jLogger.setLevel(Level.WARN);
    when(PROCESSOR.getMappingContext()).thenReturn(CONTEXT);
  }

  @AfterEach
  void tearDown() {
    recorder.stop();
    log4jLogger.removeAppender(recorder);
  }

  private static DirectBuffer bufferOf(final String s) {
    return BufferUtil.wrapString(s);
  }

  /** Produces a real MsgPack buffer from a JSON string for structural comparison tests. */
  private static DirectBuffer msgPackOf(final String json) {
    return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(json));
  }

  @Test
  void shouldReturnPrimaryResultWhenBothSucceedWithEqualBytes() {
    final var buf = bufferOf("same");
    final MappingResolver primary = (m, p) -> Either.right(buf);
    final MappingResolver comparison = (m, p) -> Either.right(buf);
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEqualTo(buf);
    assertThat(recorder.getAppendedEvents()).isEmpty();
  }

  @Test
  void shouldLogWarnWhenPrimarySucceedsButComparisonFails() {
    final var buf = bufferOf("value");
    final MappingResolver primary = (m, p) -> Either.right(buf);
    final MappingResolver comparison = (m, p) -> Either.left(new Failure("comparison failed"));
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEqualTo(buf);

    assertThat(recorder.getAppendedEvents())
        .hasSize(1)
        .first()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getMessage().getFormattedMessage())
                  .contains("Input mapping results differ");
            });
  }

  @Test
  void shouldLogWarnWhenPrimaryFailsAndComparisonSucceeds() {
    final var buf = bufferOf("value");
    final MappingResolver primary = (m, p) -> Either.left(new Failure("primary failed"));
    final MappingResolver comparison = (m, p) -> Either.right(buf);
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage()).isEqualTo("primary failed");

    assertThat(recorder.getAppendedEvents())
        .hasSize(1)
        .first()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getMessage().getFormattedMessage())
                  .contains("Input mapping results differ");
            });
  }

  @Test
  void shouldNotLogWarnWhenBothFailWithSameFailure() {
    final MappingResolver primary = (m, p) -> Either.left(new Failure("same error"));
    final MappingResolver comparison = (m, p) -> Either.left(new Failure("same error"));
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage()).isEqualTo("same error");

    assertThat(recorder.getAppendedEvents()).isEmpty();
  }

  @Test
  void shouldLogWarnWhenBothFailWithDifferentMessages() {
    final MappingResolver primary = (m, p) -> Either.left(new Failure("error A"));
    final MappingResolver comparison = (m, p) -> Either.left(new Failure("error B"));
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().getMessage()).isEqualTo("error A");

    assertThat(recorder.getAppendedEvents())
        .hasSize(1)
        .first()
        .satisfies(e -> assertThat(e.getLevel()).isEqualTo(Level.WARN));
  }

  @Test
  void shouldLogWarnWhenBothSucceedWithDifferentDocuments() {
    final MappingResolver primary = (m, p) -> Either.right(msgPackOf("{\"a\":1}"));
    final MappingResolver comparison = (m, p) -> Either.right(msgPackOf("{\"a\":2}"));
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isRight()).isTrue();
    assertThat(recorder.getAppendedEvents()).hasSize(1);
  }

  @Test
  void shouldNotLogWarnWhenBothSucceedWithEqualNestedDocuments() {
    final var buf = msgPackOf("{\"a\":{\"b\":1,\"c\":2},\"d\":3}");
    final MappingResolver primary = (m, p) -> Either.right(buf);
    final MappingResolver comparison = (m, p) -> Either.right(buf);
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isRight()).isTrue();
    assertThat(recorder.getAppendedEvents()).isEmpty();
  }

  @Test
  void shouldLogWarnWhenBothSucceedWithDifferentNestedValues() {
    final MappingResolver primary = (m, p) -> Either.right(msgPackOf("{\"a\":{\"b\":1}}"));
    final MappingResolver comparison = (m, p) -> Either.right(msgPackOf("{\"a\":{\"b\":99}}"));
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isRight()).isTrue();
    assertThat(recorder.getAppendedEvents()).hasSize(1);
  }

  @Test
  void shouldNotLogWarnWhenDocumentsHaveSameContentButDifferentKeyOrder() {
    // Tests the deep (order-insensitive) comparison path — same key-value pairs,
    // different insertion order → different bytes but semantically equal
    final MappingResolver primary = (m, p) -> Either.right(msgPackOf("{\"a\":1,\"b\":2}"));
    final MappingResolver comparison = (m, p) -> Either.right(msgPackOf("{\"b\":2,\"a\":1}"));
    final var resolver = new ComparingMappingResolver(primary, comparison);

    final var result = resolver.resolveInputMappings(INPUT_MAPPINGS, PROCESSOR);

    assertThat(result.isRight()).isTrue();
    assertThat(recorder.getAppendedEvents()).isEmpty();
  }
}
