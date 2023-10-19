/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.asserts.strace;

import io.camunda.zeebe.test.util.STracer;
import io.camunda.zeebe.test.util.STracer.FSyncTrace;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;

/**
 * Convenience class to assert values obtained through a {@link io.camunda.zeebe.test.util.STracer}.
 */
public final class STracerAssert extends AbstractObjectAssert<STracerAssert, STracer> {
  /**
   * @param tracer the actual tracer to assert against
   */
  public STracerAssert(final STracer tracer) {
    super(tracer, STracerAssert.class);
  }

  /**
   * A convenience factory method that's consistent with AssertJ conventions.
   *
   * @param actual the actual tracer to assert against
   * @return an instance of {@link STracerAssert} to assert properties of the given tracer
   */
  public static STracerAssert assertThat(final STracer actual) {
    return new STracerAssert(actual);
  }

  /** Returns collection assertions on the underlying `fsync` traces */
  public ListAssert<FSyncTrace> fsyncTraces() {
    isNotNull();

    final List<FSyncTrace> traces;
    try {
      traces = actual.fSyncTraces();
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }

    return Assertions.assertThat(traces);
  }
}
