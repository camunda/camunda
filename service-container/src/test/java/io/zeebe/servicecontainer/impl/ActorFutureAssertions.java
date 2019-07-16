/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.servicecontainer.impl;

import static org.assertj.core.api.Java6Assertions.assertThat;

import io.zeebe.util.sched.future.ActorFuture;

public class ActorFutureAssertions {
  protected static void assertCompleted(final ActorFuture<?> serviceFuture) {
    assertThat(serviceFuture).isDone();
  }

  protected static void assertNotCompleted(final ActorFuture<?> serviceFuture) {
    assertThat(serviceFuture).isNotDone();
  }

  protected static void assertFailed(final ActorFuture<?> serviceFuture) {
    assertThat(serviceFuture.isCompletedExceptionally()).isTrue();
  }
}
