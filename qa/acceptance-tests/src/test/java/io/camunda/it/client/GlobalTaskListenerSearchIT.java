/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.client.api.search.response.GlobalTaskListener;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class GlobalTaskListenerSearchIT {

  private static CamundaClient camundaClient;

  // Test data - Global scoped variables
  private static String globalListenerId1;
  private static String globalListenerId2;
  private static String globalListenerId3;
  private static String globalListenerId4;

  @BeforeAll
  static void setupGlobalTaskListeners() {
    // Setup global scoped variables
    globalListenerId1 = "listener-search-1-" + UUID.randomUUID();
    globalListenerId2 = "listener-search-2-" + UUID.randomUUID();
    globalListenerId3 = "listener-search-3-" + UUID.randomUUID();
    globalListenerId4 = "listener-search-4-" + UUID.randomUUID();

    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id(globalListenerId1)
        .type("my-job-22")
        .eventTypes(GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING)
        .afterNonGlobal(false)
        .retries(3)
        .priority(50)
        .send()
        .join();

    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id(globalListenerId2)
        .type("my-job-20")
        .eventTypes(GlobalTaskListenerEventType.CREATING)
        .afterNonGlobal(true)
        .retries(4)
        .priority(30)
        .send()
        .join();

    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id(globalListenerId3)
        .type("my-job-12")
        .eventTypes(GlobalTaskListenerEventType.COMPLETING)
        .afterNonGlobal(false)
        .retries(5)
        .priority(50)
        .send()
        .join();

    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id(globalListenerId4)
        .type("my-job-13")
        .eventTypes(GlobalTaskListenerEventType.ASSIGNING)
        .afterNonGlobal(true)
        .retries(6)
        .priority(70)
        .send()
        .join();

    // Wait for all variables to be indexed
    TestHelper.waitForGlobalTaskListenerToBeIndexed(
        camundaClient,
        List.of(globalListenerId1, globalListenerId2, globalListenerId3, globalListenerId4));
  }

  // ============ SEARCH TESTS ============

  @Test
  void shouldSearchGlobalTaskListeners() {
    // when
    final var response = camundaClient.newGlobalTaskListenerSearchRequest().send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    // order: by afterNonGlobal (false before true), then by priority desc, then by name asc
    assertThat(response.items().stream().map(GlobalTaskListener::getId).toList())
        .containsExactly(
            globalListenerId1, globalListenerId3, globalListenerId4, globalListenerId2);
  }

  // SORTING TESTS

  @Test
  void shouldSearchSortGlobalTaskListenersById() {
    // when
    final var response =
        camundaClient.newGlobalTaskListenerSearchRequest().sort(s -> s.id().asc()).send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items().stream().map(GlobalTaskListener::getId).toList())
        .containsExactly(
            globalListenerId1, globalListenerId2, globalListenerId3, globalListenerId4);
  }

  @Test
  void shouldSearchSortGlobalTaskListenersByType() {
    // when
    final var response =
        camundaClient.newGlobalTaskListenerSearchRequest().sort(s -> s.type().asc()).send().join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items().stream().map(GlobalTaskListener::getId).toList())
        .containsExactly(
            globalListenerId3, globalListenerId4, globalListenerId2, globalListenerId1);
  }

  @Test
  void shouldSearchSortGlobalTaskListenersByPriority() {
    // when
    final var response =
        camundaClient
            .newGlobalTaskListenerSearchRequest()
            .sort(s -> s.priority().asc())
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.items()).isNotEmpty();
    assertThat(response.items().stream().map(GlobalTaskListener::getId).toList())
        .containsExactly(
            globalListenerId2, globalListenerId1, globalListenerId3, globalListenerId4);
  }
}
