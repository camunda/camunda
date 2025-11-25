/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class CorrelateMessageTest {

  @TestZeebe
  private static final TestStandaloneBroker ZEEBE =
      new TestStandaloneBroker().withRecordingExporter(true).withUnauthenticatedAccess();

  CamundaClient client;
  ZeebeResourcesHelper resourcesHelper;

  @BeforeEach
  void initClientAndInstances() {
    client = ZEEBE.newClientBuilder().defaultRequestTimeout(Duration.ofSeconds(15)).build();
    resourcesHelper = new ZeebeResourcesHelper(client);
  }

  @Test
  void shouldCorrelateToProcessInstanceStartEventWithCorrelationKey() {
    // given
    final var processId = "processId";
    final var messageName = "messageName";
    final var process =
        Bpmn.createExecutableProcess(processId).startEvent().message(messageName).endEvent().done();
    resourcesHelper.deployProcess(process);

    // when
    final var response =
        client
            .newCorrelateMessageCommand()
            .messageName(messageName)
            .correlationKey("test")
            .send()
            .join();

    // then
    assertThat(response.getMessageKey()).isNotNull();
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(response.getProcessInstanceKey()).isNotNull();
  }

  @Test
  void shouldCorrelateToProcessInstanceStartEventWithoutCorrelationKey() {
    // given
    final var processId = "processId";
    final var messageName = "messageName";
    final var process =
        Bpmn.createExecutableProcess(processId).startEvent().message(messageName).endEvent().done();
    resourcesHelper.deployProcess(process);

    // when
    final var response =
        client
            .newCorrelateMessageCommand()
            .messageName(messageName)
            .withoutCorrelationKey()
            .send()
            .join();

    // then
    assertThat(response.getMessageKey()).isNotNull();
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(response.getProcessInstanceKey()).isNotNull();
  }

  @Test
  void shouldCorrelateToMessageCatchEvent() {
    // given
    final var messageName = "messageName";
    final var correlationKey = "correlationKey";
    final var process =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .intermediateCatchEvent()
            .message(
                m ->
                    m.name(messageName)
                        .zeebeCorrelationKeyExpression("= \"%s\"".formatted(correlationKey)))
            .endEvent()
            .done();
    final var processDefinitionKey = resourcesHelper.deployProcess(process);
    final var processInstanceKey = resourcesHelper.createProcessInstance(processDefinitionKey);

    // when
    final var response =
        client
            .newCorrelateMessageCommand()
            .messageName(messageName)
            .correlationKey(correlationKey)
            .send()
            .join();

    // then
    assertThat(response.getMessageKey()).isNotNull();
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(response.getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldCorrelateToProcessStartEventOverMessageCatchEvent() {
    // given
    final var processMessageStart = "processMessageStart";
    final var processMessageCatch = "processMessageCatch";
    final var messageName = "messageName";
    final var correlationKey = "correlationKey";
    final var messageStartProcess =
        Bpmn.createExecutableProcess(processMessageStart)
            .startEvent()
            .message(messageName)
            .endEvent()
            .done();
    resourcesHelper.deployProcess(messageStartProcess);
    final var messageCatchProcess =
        Bpmn.createExecutableProcess(processMessageCatch)
            .startEvent()
            .intermediateCatchEvent()
            .message(
                m ->
                    m.name(messageName)
                        .zeebeCorrelationKeyExpression("= \"%s\"".formatted(correlationKey)))
            .endEvent()
            .done();
    final var processDefinitionKey = resourcesHelper.deployProcess(messageCatchProcess);
    final var messageCatchProcessInstanceKey =
        resourcesHelper.createProcessInstance(processDefinitionKey);

    // when
    final var response =
        client
            .newCorrelateMessageCommand()
            .messageName(messageName)
            .correlationKey(correlationKey)
            .send()
            .join();

    // then
    assertThat(response.getMessageKey()).isNotNull();
    assertThat(response.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
    assertThat(response.getProcessInstanceKey()).isNotNull();
    assertThat(response.getProcessInstanceKey()).isNotEqualTo(messageCatchProcessInstanceKey);
  }

  @Test
  void shouldNotCorrelateWhenNoExistingSubscription() {
    // given
    final var messageName = "messageName";
    final var correlationKey = "correlationKey";

    // when
    final var responseFuture =
        client
            .newCorrelateMessageCommand()
            .messageName(messageName)
            .correlationKey(correlationKey)
            .send();

    //     then
    assertThatThrownBy(responseFuture::join)
        .isInstanceOf(ProblemException.class)
        .hasMessageContaining("title: NOT_FOUND")
        .hasMessageContaining("status: 404")
        .hasMessageContaining(
            "Expected to find subscription for message with name '%s' and correlation key '%s', but none was found."
                .formatted(messageName, correlationKey));
  }
}
