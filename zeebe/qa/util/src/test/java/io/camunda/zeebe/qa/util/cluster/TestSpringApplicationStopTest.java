/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.qa.util.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

final class TestSpringApplicationStopTest {

  @Test
  void shouldCloseContext() {
    // given
    final var context = new GenericApplicationContext();
    context.refresh();

    // when
    TestSpringApplication.closeWithTimeout(context, Duration.ofSeconds(30));

    // then
    assertThat(context.isActive()).isFalse();
  }

  @Test
  void shouldGiveUpWhenContextDoesNotCloseInTime() {
    // given a bean whose destruction blocks, like a broker waiting on a stuck actor
    final var release = new CountDownLatch(1);
    final var context = new GenericApplicationContext();
    context.registerBean("stuck", BlockingBean.class, () -> new BlockingBean(release), bd -> {});
    context.refresh();

    try {
      // when / then
      assertThatThrownBy(
              () -> TestSpringApplication.closeWithTimeout(context, Duration.ofMillis(200)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("did not stop within");
    } finally {
      release.countDown();
    }
  }

  static final class BlockingBean implements AutoCloseable {
    private final CountDownLatch release;

    BlockingBean(final CountDownLatch release) {
      this.release = release;
    }

    @Override
    public void close() throws InterruptedException {
      release.await();
    }
  }
}
