/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.InputMappings;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link MappingResolver} that runs a primary resolver and a comparison resolver, logging a
 * warning when their results differ. The primary result is always returned and applied.
 *
 * <p>Both resolvers are always run — even when the primary fails — so that divergences between
 * failure and success outcomes are also detected and logged.
 */
@NullMarked
public final class ComparingMappingResolver implements MappingResolver {

  private static final Logger LOG = LoggerFactory.getLogger(ComparingMappingResolver.class);
  private static final ObjectMapper MSGPACK_MAPPER = new ObjectMapper(new MessagePackFactory());

  // 1/s throttle per instance; one engine instance → one resolver → effective global rate limit
  private final Logger throttledLog = new ThrottledLogger(LOG, Duration.ofSeconds(1));

  private final MappingResolver primary;
  private final MappingResolver comparison;

  public ComparingMappingResolver(final MappingResolver primary, final MappingResolver comparison) {
    this.primary = primary;
    this.comparison = comparison;
  }

  @Override
  public Either<Failure, DirectBuffer> resolveInputMappings(
      final InputMappings inputMappings, final MappingExpressionProcessor processor) {
    final var primaryResult = primary.resolveInputMappings(inputMappings, processor);

    try {
      // Snapshot before the comparison run: CombinedMappingResolver returns a view into a
      // shared evaluation buffer that the comparison resolver will overwrite.
      final var snapshot = snapshot(primaryResult);
      final var comparisonResult = comparison.resolveInputMappings(inputMappings, processor);

      if (!equivalent(snapshot, comparisonResult)) {
        throttledLog.warn(
            "Input mapping results differ between {} and {} resolvers [{}].",
            primary.getClass().getSimpleName(),
            comparison.getClass().getSimpleName(),
            processor.getMappingContext());
      }
    } catch (final Exception e) {
      LOG.warn(
          "Comparison resolver {} threw unexpectedly [{}]; primary {} result is applied.",
          comparison.getClass().getSimpleName(),
          processor.getMappingContext(),
          primaryResult.isRight() ? "success" : "failure",
          e);
    }

    return primaryResult;
  }

  private static Either<Failure, DirectBuffer> snapshot(
      final Either<Failure, DirectBuffer> result) {
    return result.isRight() ? Either.right(BufferUtil.cloneBuffer(result.get())) : result;
  }

  private static boolean equivalent(
      final Either<Failure, DirectBuffer> a, final Either<Failure, DirectBuffer> b) {
    if (a.isLeft() && b.isLeft()) {
      return a.getLeft().equals(b.getLeft());
    }
    if (a.isRight() && b.isRight()) {
      return documentsEqual(a.get(), b.get());
    }
    return false; // one succeeded, one failed
  }

  private static boolean documentsEqual(final DirectBuffer a, final DirectBuffer b) {
    if (a.capacity() != b.capacity()) {
      return false;
    }
    if (BufferUtil.equals(a, b)) {
      return true;
    }
    try {
      return MSGPACK_MAPPER
          .readTree(BufferUtil.bufferAsArray(a))
          .equals(MSGPACK_MAPPER.readTree(BufferUtil.bufferAsArray(b)));
    } catch (final Exception e) {
      return false;
    }
  }
}
