/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.ArrayList;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class GlobalTaskListenerCommandIT {

  private static CamundaClient camundaClient;

  // ============ CREATE TESTS ============

  @Test
  void shouldCreateGlobalTaskListenerWithMinimalData() {
    final String listenerId = "my-listener-" + UUID.randomUUID();
    final var response =
        camundaClient
            .newCreateGlobalTaskListenerRequest()
            .id(listenerId)
            .type("my-job")
            .eventTypes(
                GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING)
            .send()
            .join();

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(listenerId);
    assertThat(response.getType()).isEqualTo("my-job");
    assertThat(response.getEventTypes())
        .containsExactlyInAnyOrder(
            GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING);
    assertThat(response.getRetries()).isEqualTo(GlobalListenerRecordValue.DEFAULT_RETRIES);
    assertThat(response.getAfterNonGlobal()).isFalse();
    assertThat(response.getPriority()).isEqualTo(GlobalListenerRecordValue.DEFAULT_PRIORITY);
    assertThat(response.getSource()).isEqualTo(GlobalListenerSource.API);
  }

  @Test
  void shouldCreateGlobalTaskListenerWithFullData() {
    final String listenerId = "my-listener-" + UUID.randomUUID();
    final var response =
        camundaClient
            .newCreateGlobalTaskListenerRequest()
            .id(listenerId)
            .type("my-job")
            .eventTypes(
                GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING)
            .afterNonGlobal(true)
            .retries(5)
            .priority(70)
            .send()
            .join();

    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(listenerId);
    assertThat(response.getType()).isEqualTo("my-job");
    assertThat(response.getEventTypes())
        .containsExactlyInAnyOrder(
            GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING);
    assertThat(response.getRetries()).isEqualTo(5);
    assertThat(response.getAfterNonGlobal()).isTrue();
    assertThat(response.getPriority()).isEqualTo(70);
    assertThat(response.getSource()).isEqualTo(GlobalListenerSource.API);
  }

  @Test
  void shouldRejectCreationIfIdIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateGlobalTaskListenerRequest()
                    .id(null)
                    .type("my-job")
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be null");
  }

  @Test
  void shouldRejectCreationIfIdIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateGlobalTaskListenerRequest()
                    .id("")
                    .type("my-job")
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be empty");
  }

  @Test
  void shouldRejectCreationIfTypeIsNull() {
    final String listenerId = "my-listener-" + UUID.randomUUID();
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateGlobalTaskListenerRequest()
                    .id(listenerId)
                    .type(null)
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type must not be null");
  }

  @Test
  void shouldRejectCreationIfTypeIsEmpty() {
    final String listenerId = "my-listener-" + UUID.randomUUID();
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateGlobalTaskListenerRequest()
                    .id(listenerId)
                    .type("")
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("type must not be empty");
  }

  @Test
  void shouldRejectCreationIfEventTypesIsEmpty() {
    final String listenerId = "my-listener-" + UUID.randomUUID();
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateGlobalTaskListenerRequest()
                    .id(listenerId)
                    .type("my-job")
                    .eventTypes(new ArrayList<>())
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("eventTypes must not be empty");
  }

  @Test
  void shouldRejectCreationIfDuplicateGlobalListener() {
    // given
    final String listenerId = "my-listener-" + UUID.randomUUID();

    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("my-job")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();

    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newCreateGlobalTaskListenerRequest()
                    .id(listenerId)
                    .type("my-other-job")
                    .eventTypes(GlobalTaskListenerEventType.CREATING)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 409: 'Conflict'");
  }

  // ============ DELETE TESTS ============
  @Test
  void shouldDeleteGlobalTaskListener() {
    // given
    final String listenerId = "my-listener-" + UUID.randomUUID();

    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("my-job")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();

    // when
    camundaClient.newDeleteGlobalTaskListenerRequest(listenerId).send().join();

    // then - verify deletion by attempting to recreate
    assertThatCode(
            () ->
                camundaClient
                    .newCreateGlobalTaskListenerRequest()
                    .id(listenerId)
                    .type("my-job")
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectDeletionIfListenerIdIsNull() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newDeleteGlobalTaskListenerRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be null");
  }

  @Test
  void shouldRejectDeletionIfListenerIdIsEmpty() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newDeleteGlobalTaskListenerRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be empty");
  }

  @Test
  void shouldRejectDeletionIfGlobalListenerDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newDeleteGlobalTaskListenerRequest("my-non-existent-listener")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }

  // ============ UPDATE TESTS ============

  @Test
  void shouldUpdateGlobalTaskListener() {
    // given
    final String listenerId = "my-listener-" + UUID.randomUUID();

    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("my-job")
        .eventTypes(GlobalTaskListenerEventType.ALL)
        .send()
        .join();

    // when
    final var response =
        camundaClient
            .newUpdateGlobalTaskListenerRequest(listenerId)
            .type("my-other-job")
            .eventTypes(
                GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING)
            .afterNonGlobal(true)
            .retries(5)
            .priority(70)
            .send()
            .join();

    // then
    assertThat(response).isNotNull();
    assertThat(response.getId()).isEqualTo(listenerId);
    assertThat(response.getType()).isEqualTo("my-other-job");
    assertThat(response.getEventTypes())
        .containsExactlyInAnyOrder(
            GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING);
    assertThat(response.getRetries()).isEqualTo(5);
    assertThat(response.getAfterNonGlobal()).isTrue();
    assertThat(response.getPriority()).isEqualTo(70);
    assertThat(response.getSource()).isEqualTo(GlobalListenerSource.API);
  }

  @Test
  void shouldRejectUpdateIfListenerIdIsNull() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUpdateGlobalTaskListenerRequest(null)
                    .type("my-job")
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be null");
  }

  @Test
  void shouldRejectUpdateIfListenerIdIsEmpty() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUpdateGlobalTaskListenerRequest("")
                    .type("my-job")
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be empty");
  }

  @Test
  void shouldRejectUpdateIfGlobalListenerDoesNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newUpdateGlobalTaskListenerRequest("my-non-existent-listener")
                    .type("my-job")
                    .eventTypes(GlobalTaskListenerEventType.ALL)
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
