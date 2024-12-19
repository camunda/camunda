/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class JobBatchActivateAuthorizationTest {

  public static final String JOB_TYPE = "jobType";

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());
  private static long defaultUserKey = -1L;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withoutAwaitingIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)));

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Before
  public void beforeEach() {
    defaultUserKey =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUsername(DEFAULT_USER.getUsername())
            .getFirst()
            .getKey();
  }

  @Test
  public void shouldBeAuthorizedToActivateAllJobsWithDefaultUser() {
    // given
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);

    // when
    final var response =
        engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(2).activate(defaultUserKey);

    // then
    assertThat(response.getValue().getJobs())
        .hasSize(2)
        .extracting(JobRecordValue::getBpmnProcessId)
        .containsExactlyInAnyOrder(processId1, processId2);
  }

  @Test
  public void shouldBeAuthorizedToActivateMultipleJobsWithUser() {
    // given
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        processId1,
        processId2);

    // when
    final var response =
        engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(2).activate(userKey);

    // then
    assertThat(response.getValue().getJobs())
        .hasSize(2)
        .extracting(JobRecordValue::getBpmnProcessId)
        .containsExactlyInAnyOrder(processId1, processId2);
  }

  @Test
  public void shouldBeAuthorizedToActivateSingleJobWithUser() {
    // given
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    final var userKey = createUser();
    addPermissionsToUser(
        userKey,
        AuthorizationResourceType.PROCESS_DEFINITION,
        PermissionType.UPDATE_PROCESS_INSTANCE,
        processId1);

    // when
    final var response =
        engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(2).activate(userKey);

    // then
    assertThat(response.getValue().getJobs())
        .hasSize(1)
        .extracting(JobRecordValue::getBpmnProcessId)
        .containsOnly(processId1);
  }

  @Test
  public void shouldNotActivateJobsWithUnauthorizedUser() {
    // given
    final var processId1 = Strings.newRandomValidBpmnId();
    final var processId2 = Strings.newRandomValidBpmnId();
    createJobs(processId1, processId2);
    final var userKey = createUser();

    // when
    final var response =
        engine.jobs().withType(JOB_TYPE).withMaxJobsToActivate(2).activate(userKey);

    // then
    assertThat(response.getValue().getJobs()).isEmpty();
  }

  private long createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getKey();
  }

  private void addPermissionsToUser(
      final long userKey,
      final AuthorizationResourceType authorization,
      final PermissionType permissionType,
      final String... resourceIds) {
    engine
        .authorization()
        .permission()
        .withOwnerKey(userKey)
        .withResourceType(authorization)
        .withPermission(permissionType, resourceIds)
        .add(defaultUserKey);
  }

  private void createJobs(final String... processIds) {
    for (final String processId : processIds) {
      engine
          .deployment()
          .withXmlResource(
              "%s.bpmn".formatted(processId),
              Bpmn.createExecutableProcess(processId)
                  .startEvent()
                  .serviceTask("serviceTask", t -> t.zeebeJobType(JOB_TYPE))
                  .endEvent()
                  .done())
          .deploy(defaultUserKey);

      engine.processInstance().ofBpmnProcessId(processId).create(defaultUserKey);
    }

    RecordingExporter.jobRecords(JobIntent.CREATED).limit(processIds.length).await();
  }
}
