/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.GlobalListenerSource;
import io.camunda.client.api.search.enums.GlobalTaskListenerEventType;
import io.camunda.it.util.TestHelper;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class GlobalTaskListenerFetchIT {

  private static CamundaClient camundaClient;

  // ============ GET TESTS ============
  @Test
  void shouldGetGlobalTaskListener() {
    // given
    final String listenerId = "my-listener-" + UUID.randomUUID();

    camundaClient
        .newCreateGlobalTaskListenerRequest()
        .id(listenerId)
        .type("my-job")
        .eventTypes(GlobalTaskListenerEventType.CREATING, GlobalTaskListenerEventType.COMPLETING)
        .send()
        .join();

    // wait for data to be indexed
    TestHelper.waitForGlobalTaskListenerToBeIndexed(camundaClient, listenerId);

    // when
    final var response = camundaClient.newGlobalTaskListenerGetRequest(listenerId).send().join();

    // then
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
  void shouldRejectGetIfIdIsNull() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newGlobalTaskListenerGetRequest(null).send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be null");
  }

  @Test
  void shouldRejectGetIfIdIsEmpty() {
    // when / then
    assertThatThrownBy(() -> camundaClient.newGlobalTaskListenerGetRequest("").send().join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id must not be empty");
  }

  @Test
  void shouldRejectGetIfGlobalTaskListenerNotExist() {
    // when / then
    assertThatThrownBy(
            () ->
                camundaClient
                    .newGlobalTaskListenerGetRequest("non-existent-listener")
                    .send()
                    .join())
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("Failed with code 404: 'Not Found'");
  }
}
