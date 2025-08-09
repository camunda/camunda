/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SafeInitProxyTest {

  @Mock Supplier<String> initMethod;

  @Test
  void shouldInitializeAfterRetries() {
    Mockito.when(initMethod.get())
        .thenThrow(new RuntimeException("Startup failure"))
        .thenThrow(new RuntimeException("Retry failure 1"))
        .thenThrow(new RuntimeException("Retry failure 2"))
        .thenReturn("created object");

    final List<String> receivedErrors = new ArrayList<>();

    final SafeInitProxy<String> proxy =
        new SafeInitProxy<>(
            initMethod,
            e -> {
              receivedErrors.add(e.getMessage());
            },
            0);

    Awaitility.await("Object is initialised")
        .untilAsserted(
            () -> {
              final String result =
                  proxy.orElseThrow(() -> new IllegalStateException("Object must be initialized"));
              assertEquals("created object", result);
              assertEquals(
                  List.of("Startup failure", "Retry failure 1", "Retry failure 2"), receivedErrors);
            });
  }

  @Test
  void shouldInitializeOnStartup() {
    Mockito.when(initMethod.get()).thenReturn("created object");

    final List<String> receivedErrors = new ArrayList<>();

    final SafeInitProxy<String> proxy =
        new SafeInitProxy<>(
            initMethod,
            e -> {
              receivedErrors.add(e.getMessage());
            },
            0);

    Awaitility.await("Object is initialised")
        .untilAsserted(
            () -> {
              final String result =
                  proxy.orElseThrow(() -> new IllegalStateException("Object must be initialized"));
              assertEquals("created object", result);
              assertEquals(List.of(), receivedErrors);
            });
  }
}
