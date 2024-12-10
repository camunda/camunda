/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.authorization.rest;

import static io.camunda.zeebe.it.util.AuthorizationsUtil.createClient;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.Profile;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.protocol.rest.PermissionTypeEnum;
import io.camunda.zeebe.client.protocol.rest.ResourceTypeEnum;
import io.camunda.zeebe.it.util.AuthorizationsUtil;
import io.camunda.zeebe.it.util.AuthorizationsUtil.Permissions;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.junit.AutoCloseResources;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@AutoCloseResources
@Testcontainers
@ZeebeIntegration
@TestMethodOrder(OrderAnnotation.class)
public class JobBatchActivateAuthorizationIT {

  public static final String JOB_TYPE = "jobType";

  @Container
  private static final ElasticsearchContainer CONTAINER =
      TestSearchContainers.createDefeaultElasticsearchContainer();

  private static AuthorizationsUtil authUtil;
  @AutoCloseResource private static ZeebeClient defaultUserClient;

  @TestZeebe(autoStart = false)
  private TestStandaloneBroker broker =
      new TestStandaloneBroker()
          .withRecordingExporter(true)
          .withSecurityConfig(c -> c.getAuthorizations().setEnabled(true))
          .withGatewayConfig(c -> c.getLongPolling().setEnabled(false))
          .withAdditionalProfile(Profile.AUTH_BASIC);

  @BeforeEach
  void beforeEach() {
    broker.withCamundaExporter("http://" + CONTAINER.getHttpHostAddress());
    broker.start();

    final var defaultUsername = "demo";
    defaultUserClient = createClient(broker, defaultUsername, "demo");
    authUtil = new AuthorizationsUtil(broker, defaultUserClient, CONTAINER.getHttpHostAddress());

    authUtil.awaitUserExistsInElasticsearch(defaultUsername);
  }

  @Test
  @Order(1) // we need to run this first, otherwise it will activate jobs of the other testcases
  void shouldBeAuthorizedToActivateAllJobsWithDefaultUser() {
    // given
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);

    // when
    final var response =
        defaultUserClient
            .newActivateJobsCommand()
            .jobType(JOB_TYPE)
            .maxJobsToActivate(2)
            .send()
            .join();

    // then
    assertThat(response.getJobs())
        .hasSize(2)
        .extracting(ActivatedJob::getBpmnProcessId)
        .containsExactlyInAnyOrder(processId1, processId2);
  }

  @Test
  @Order(2)
  void shouldBeAuthorizedToActivateMultipleJobsWithUser() {
    // given
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    // and user has permission for both processes
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.UPDATE_PROCESS_INSTANCE,
            List.of(processId1, processId2)));

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response =
          client.newActivateJobsCommand().jobType(JOB_TYPE).maxJobsToActivate(2).send().join();

      // then
      assertThat(response.getJobs())
          .hasSize(2)
          .extracting(ActivatedJob::getBpmnProcessId)
          .containsExactlyInAnyOrder(processId1, processId2);
    }
  }

  @Test
  @Order(3)
  void shouldBeAuthorizedToActivateSingleJobWithUser() {
    // given
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    // and user has permission for only one of the processes
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUserWithPermissions(
        username,
        password,
        new Permissions(
            ResourceTypeEnum.PROCESS_DEFINITION,
            PermissionTypeEnum.UPDATE_PROCESS_INSTANCE,
            List.of(processId1)));

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response =
          client.newActivateJobsCommand().jobType(JOB_TYPE).maxJobsToActivate(2).send().join();

      // then
      assertThat(response.getJobs())
          .hasSize(1)
          .extracting(ActivatedJob::getBpmnProcessId)
          .containsOnly(processId1);
    }
  }

  @Test
  @Order(4)
  void shouldNotActivateJobsWithUnauthorizedUser() {
    // given
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    // and user has permission for none of the processes
    final var username = UUID.randomUUID().toString();
    final var password = "password";
    authUtil.createUser(username, password);

    try (final var client = authUtil.createClient(username, password)) {
      // when
      final var response =
          client
              .newActivateJobsCommand()
              .jobType(JOB_TYPE)
              .maxJobsToActivate(2)
              .timeout(Duration.ofMinutes(10))
              .send()
              .join();

      // then
      assertThat(response.getJobs()).isEmpty();
    }
  }

  private static void createJobs(final String... processIds) {
    for (final String processId : processIds) {
      defaultUserClient
          .newDeployResourceCommand()
          .addProcessModel(
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .serviceTask("serviceTask", t -> t.zeebeJobType(JOB_TYPE))
                  .endEvent()
                  .done(),
              "%s.xml".formatted(processId))
          .send()
          .join();

      defaultUserClient
          .newCreateInstanceCommand()
          .bpmnProcessId(processId)
          .latestVersion()
          .send()
          .join();
    }

    RecordingExporter.jobRecords(JobIntent.CREATED).limit(processIds.length).await();
  }
}
