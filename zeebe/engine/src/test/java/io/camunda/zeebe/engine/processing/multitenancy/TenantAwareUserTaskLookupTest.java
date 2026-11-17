/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.UserTaskLookupResponses;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class TenantAwareUserTaskLookupTest {

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withMultiTenancyChecksEnabled(true);

  private static final UserTaskLookupResponses RESPONSES = new UserTaskLookupResponses();
  private static final AtomicLong REQUEST_IDS = new AtomicLong();
  private static final String USERNAME = Strings.newRandomValidIdentityId();
  private static final String USER_TENANT = Strings.newRandomValidIdentityId();
  private static final String OTHER_TENANT = Strings.newRandomValidIdentityId();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final String processId = Strings.newRandomValidBpmnId();

  @BeforeClass
  public static void setUp() {
    RESPONSES.install(ENGINE.getCommandResponseWriter());
    ENGINE.user().newUser(USERNAME).create();
    ENGINE.tenant().newTenant().withTenantId(USER_TENANT).create();
    ENGINE.tenant().newTenant().withTenantId(OTHER_TENANT).create();
    ENGINE
        .tenant()
        .addEntity(USER_TENANT)
        .withEntityId(USERNAME)
        .withEntityType(EntityType.USER)
        .add();
  }

  @Test
  public void shouldFetchUserTaskOfTenantTheUserIsAssignedTo() {
    // given
    final long userTaskKey = createUserTask(USER_TENANT);
    final long requestId = REQUEST_IDS.incrementAndGet();

    // when
    ENGINE.userTaskLookup().withKey(userTaskKey).withRequestId(requestId).fetch(USERNAME);

    // then
    final var response = RESPONSES.awaitResponse(requestId);
    assertThat(response.getTenantId()).isEqualTo(USER_TENANT);
    assertThat(response.getUserTask().getUserTaskKey()).isEqualTo(userTaskKey);
    assertThat(response.getUserTask().getTenantId()).isEqualTo(USER_TENANT);
  }

  @Test
  public void shouldRejectUserTaskOfTenantTheUserIsNotAssignedTo() {
    // given
    final long userTaskKey = createUserTask(OTHER_TENANT);

    // when
    final var rejection =
        ENGINE.userTaskLookup().withKey(userTaskKey).expectRejection().fetch(USERNAME);

    // then
    Assertions.assertThat(rejection)
        .describedAs("a task of another tenant must look like it doesn't exist")
        .hasRejectionType(RejectionType.NOT_FOUND)
        .hasRejectionReason(
            "Expected to fetch user task with key '%d', but no such user task was found"
                .formatted(userTaskKey));
  }

  private long createUserTask(final String tenantId) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .userTask("task")
                .zeebeUserTask()
                .endEvent()
                .done())
        .withTenantId(tenantId)
        .deploy();
    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withTenantId(tenantId).create();
    return RecordingExporter.userTaskRecords(UserTaskIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst()
        .getKey();
  }
}
