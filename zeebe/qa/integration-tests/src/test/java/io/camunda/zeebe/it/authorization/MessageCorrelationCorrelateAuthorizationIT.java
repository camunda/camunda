/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.application.Profile;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
public class MessageCorrelationCorrelateAuthorizationIT {

  public static final String INTERMEDIATE_MSG_NAME = "intermediateMsg";
  public static final String START_MSG_NAME = "startMsg";
  public static final String CORRELATION_KEY_VARIABLE = "correlationKey";

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static final String PROCESS_ID = "processId";

  @TestZeebe(autoStart = false)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withBrokerConfig(
              b -> b.getExperimental().getEngine().getAuthorizations().setEnableAuthorization(true))
          .withAdditionalProfile(Profile.AUTH_BASIC);

  private static AuthorizationsUtil authUtil;
  private static ZeebeClient defaultUserClient;

  @BeforeAll
  static void beforeAll() {
    BROKER.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    BROKER.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClient(BROKER, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(BROKER, defaultUserClient, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
    defaultUserClient
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateCatchEvent()
                .message(
                    m ->
                        m.name(INTERMEDIATE_MSG_NAME)
                            .zeebeCorrelationKeyExpression(CORRELATION_KEY_VARIABLE))
                .endEvent()
                .moveToProcess(PROCESS_ID)
                .startEvent()
                .message(m -> m.name(START_MSG_NAME))
                .done(),
            "process.xml")
        .send()
        .join();
  }

  @Test
  void shouldBeAuthorizedToCorrelateMessageToIntermediateEventWithDefaultUser() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    final var processInstance = createProcessInstance(correlationKey);

    // when
    final var response =
        defaultUserClient
            .newCorrelateMessageCommand()
            .messageName(INTERMEDIATE_MSG_NAME)
            .correlationKey(correlationKey)
            .send()
            .join();

    // then
    assertThat(response.getProcessInstanceKey()).isEqualTo(processInstance.getProcessInstanceKey());
  }

  @Test
  void shouldBeAuthorizedToCorrelateMessageToIntermediateEventWithUser() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    final var processInstance = createProcessInstance(correlationKey);
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION, PermissionTypeEnum.UPDATE, List.of(PROCESS_ID)));

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response =
          client
              .newCorrelateMessageCommand()
              .messageName(INTERMEDIATE_MSG_NAME)
              .correlationKey(correlationKey)
              .send()
              .join();

      // then
      assertThat(response.getProcessInstanceKey())
          .isEqualTo(processInstance.getProcessInstanceKey());
    }
  }

  @Test
  void shouldBeUnauthorizedToCorrelateMessageToIntermediateEventIfNoPermissions() {
    // given
    final var correlationKey = UUID.randomUUID().toString();
    createProcessInstance(correlationKey);
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {

      // when
      final var response =
          client
              .newCorrelateMessageCommand()
              .messageName(INTERMEDIATE_MSG_NAME)
              .correlationKey(correlationKey)
              .send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: UNAUTHORIZED")
          .hasMessageContaining("status: 401")
          .hasMessageContaining(
              "Unauthorized to perform operation 'UPDATE' on resource 'PROCESS_DEFINITION' with BPMN process id '%s'",
              PROCESS_ID);
    }
  }

  @Test
  void shouldBeAuthorizedToCorrelateMessageToStartEventWithDefaultUser() {
    // when
    final var response =
        defaultUserClient
            .newCorrelateMessageCommand()
            .messageName(START_MSG_NAME)
            .withoutCorrelationKey()
            .send()
            .join();

    // then
    assertThat(response.getProcessInstanceKey()).isPositive();
  }

  @Test
  void shouldBeAuthorizedToCorrelateMessageToStartEventWithUser() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION, PermissionTypeEnum.CREATE, List.of(PROCESS_ID)));

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response =
          client
              .newCorrelateMessageCommand()
              .messageName(START_MSG_NAME)
              .withoutCorrelationKey()
              .send()
              .join();

      // then
      assertThat(response.getProcessInstanceKey()).isPositive();
    }
  }

  @Test
  void shouldBeUnauthorizedToCorrelateMessageToStartEventIfNoPermissions() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response =
          client
              .newCorrelateMessageCommand()
              .messageName(START_MSG_NAME)
              .withoutCorrelationKey()
              .send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: UNAUTHORIZED")
          .hasMessageContaining("status: 401")
          .hasMessageContaining(
              "Unauthorized to perform operation 'CREATE' on resource 'PROCESS_DEFINITION' with BPMN process id '%s'",
              PROCESS_ID);
    }
  }

  @Test
  void shouldNotCorrelateAnyMessageIfUnauthorizedForOne() {
    // given
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION, PermissionTypeEnum.CREATE, List.of(PROCESS_ID)));
    final var unauthorizedProcessId = "unauthorizedProcessId";
    final var resourceName = "unauthorizedProcess.xml";
    final var deploymentKey =
        defaultUserClient
            .newDeployResourceCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess(unauthorizedProcessId)
                    .startEvent()
                    .message(m -> m.name(START_MSG_NAME))
                    .endEvent()
                    .done(),
                resourceName)
            .send()
            .join()
            .getKey();

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response =
          client
              .newCorrelateMessageCommand()
              .messageName(START_MSG_NAME)
              .withoutCorrelationKey()
              .send();

      // then
      assertThatThrownBy(response::join)
          .isInstanceOf(ProblemException.class)
          .hasMessageContaining("title: UNAUTHORIZED")
          .hasMessageContaining("status: 401")
          .hasMessageContaining(
              "Unauthorized to perform operation 'CREATE' on resource 'PROCESS_DEFINITION' with BPMN process id '%s'",
              unauthorizedProcessId);

      final var deploymentPosition =
          RecordingExporter.deploymentRecords(DeploymentIntent.CREATED)
              .withRecordKey(deploymentKey)
              .getFirst()
              .getPosition();
      assertThat(
              RecordingExporter.records()
                  .after(deploymentPosition)
                  .limit(r -> r.getRejectionType() == RejectionType.UNAUTHORIZED)
                  .processInstanceRecords()
                  .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATING)
                  .withBpmnProcessId(unauthorizedProcessId)
                  .exists())
          .isFalse();
    }
  }

  private ProcessInstanceEvent createProcessInstance(final String correlationKey) {
    return defaultUserClient
        .newCreateInstanceCommand()
        .bpmnProcessId(PROCESS_ID)
        .latestVersion()
        .variables(Map.of(CORRELATION_KEY_VARIABLE, correlationKey))
        .send()
        .join();
  }
}
