/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import java.util.ArrayList;
import java.util.List;
import org.agrona.collections.MutableBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/**
 * This tests that all instance fields are correctly closed by closing on their references and
 * adding one assertion for each to a final {@link AfterAll} callback. The structure is a bit
 * strange: given-when is in the test themselves, and the `then` is in the {@link AfterAll}
 * callback.
 *
 * <p>I'm not sure how we could test that all static fields are correctly closed, but considering
 * the code paths are almost identical, this is hopefully enough.
 */
@AutoCloseResources
final class AutoCloseResourcesTest {
  private static final List<Runnable> ASSERTIONS = new ArrayList<>();

  @AutoCloseResource
  private final MyCloseable closeable = new MyCloseable(new MutableBoolean(false));

  @AutoCloseResource(closeMethod = "myCustomClose")
  private final MyCustomCloseable customCloseable =
      new MyCustomCloseable(new MutableBoolean(false));

  // not closed as not annotated
  private final MyCloseable notClosed = new MyCloseable(new MutableBoolean(false));

  @AfterAll
  static void ensureInstanceFieldsAreClosed() {
    // then
    ASSERTIONS.forEach(Runnable::run);
  }

  @Test
  void shouldCloseAutoCloseable() {
    // given
    final var closeableRef = closeable;

    // when
    ASSERTIONS.add(() -> assertThat(closeableRef.isClosed().get()).isTrue());
  }

  @Test
  void shouldCloseCustomCloseable() {
    // given
    final var customCloseableRef = customCloseable;

    // when
    ASSERTIONS.add(() -> assertThat(customCloseableRef.isClosed().get()).isTrue());
  }

  @Test
  void shouldNotClosedNonAnnotatedAutoCloseable() {
    // given
    final var notClosedRef = notClosed;

    // when
    ASSERTIONS.add(() -> assertThat(notClosedRef.isClosed().get()).isFalse());
  }

  private record MyCloseable(MutableBoolean isClosed) implements AutoCloseable {

    @Override
    public void close() {
      isClosed.set(true);
    }
  }

  private record MyCustomCloseable(MutableBoolean isClosed) {
    @SuppressWarnings("unused")
    public void myCustomClose() {
      isClosed.set(true);
    }
  }
}
