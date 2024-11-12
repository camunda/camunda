/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.asserts.strace;

import io.camunda.zeebe.test.util.STracer;
import io.camunda.zeebe.test.util.STracer.FSyncTrace;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.api.ListAssert;

/**
 * Convenience class to assert values obtained through a {@link io.camunda.zeebe.test.util.STracer}.
 *
 * <p>NOTE: since strace and the Java program interact asynchronously over file I/O, you will likely
 * need to wrap your assertion in an {@link org.awaitility.Awaitility} block.
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
    return ListAssert.assertThatStream(actual.fSyncTraces());
  }
}
