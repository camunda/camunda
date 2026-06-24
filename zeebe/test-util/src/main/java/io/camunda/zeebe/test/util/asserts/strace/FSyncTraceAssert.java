/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.asserts.strace;

import io.camunda.zeebe.test.util.STracer.FSyncTrace;
import java.nio.file.Path;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Condition;
import org.assertj.core.api.InstanceOfAssertFactory;
import org.assertj.core.condition.VerboseCondition;

/**
 * Utility assertion class to verify `fsync` traces obtained via a {@link
 * io.camunda.zeebe.test.util.STracer}.
 *
 * <p>Typical usage is via {@link STracerAssert}.
 */
@SuppressWarnings("UnusedReturnValue")
public final class FSyncTraceAssert extends AbstractObjectAssert<FSyncTraceAssert, FSyncTrace> {

  /**
   * @param trace the actual trace to assert against
   */
  public FSyncTraceAssert(final FSyncTrace trace) {
    super(trace, FSyncTraceAssert.class);
  }

  /**
   * A convenience factory method that's consistent with AssertJ conventions.
   *
   * @param actual the actual trace to assert against
   * @return an instance of {@link FSyncTraceAssert} to assert properties of the given trace
   */
  public static FSyncTraceAssert assertThat(final FSyncTrace actual) {
    return new FSyncTraceAssert(actual);
  }

  /**
   * @return a factory you can use with {@link
   *     org.assertj.core.api.ListAssert#first(InstanceOfAssertFactory)} or {@link
   *     org.assertj.core.api.ObjectAssert#asInstanceOf(InstanceOfAssertFactory)}
   */
  public static InstanceOfAssertFactory<FSyncTrace, FSyncTraceAssert> factory() {
    return new InstanceOfAssertFactory<>(FSyncTrace.class, FSyncTraceAssert::assertThat);
  }

  /**
   * Asserts that the given fsync was applied on the given path.
   *
   * @param path the expected path
   * @return itself for chaining
   */
  public FSyncTraceAssert hasPath(final Path path) {
    return has(Conditions.hasPath(path));
  }

  public static final class Conditions {
    private Conditions() {}

    /** Returns a condition which checks that a stream has the given stream type. */
    public static Condition<FSyncTrace> hasPath(final Path path) {
      return VerboseCondition.verboseCondition(
          trace -> trace.path().equals(path),
          "fsync call for the '%s'".formatted(path),
          trace -> " but actual path is '%s'".formatted(trace.path()));
    }
  }
}
