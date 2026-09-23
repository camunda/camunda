/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.zeebe.containers.ZeebeContainer;
import org.junit.jupiter.api.Test;

public class TasklistZeebeExtensionTest {

  @Test
  public void shouldStopContainerAfterFailedStart() {
    final var container = mock(ZeebeContainer.class);
    final var startFailure = new IllegalStateException("start failed");
    doThrow(startFailure).when(container).start();

    assertThatThrownBy(() -> TasklistZeebeExtension.startIdentityZeebeContainer(container))
        .isSameAs(startFailure);
    verify(container).stop();
  }

  @Test
  public void shouldPreserveStartupFailureWhenCleanupFails() {
    final var container = mock(ZeebeContainer.class);
    final var startFailure = new IllegalStateException("start failed");
    final var stopFailure = new IllegalStateException("stop failed");
    doThrow(startFailure).when(container).start();
    doThrow(stopFailure).when(container).stop();

    assertThatThrownBy(() -> TasklistZeebeExtension.startIdentityZeebeContainer(container))
        .isSameAs(startFailure);
    assertThat(startFailure.getSuppressed()).containsExactly(stopFailure);
  }

  @Test
  public void shouldNotStopContainerWhenStartupSucceeds() {
    final var container = mock(ZeebeContainer.class);

    TasklistZeebeExtension.startIdentityZeebeContainer(container);

    verify(container).start();
    verify(container, never()).stop();
  }
}
