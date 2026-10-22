/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

/**
 * Asserts that an authorization rejection of a still active instance carries the instance's stored
 * ordinal.
 */
public final class HistoryDeletionAuthorizationStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 1234;

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withAuthorizationsEnabled(true)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL))
          .withSecurityConfig(
              cfg -> {
                cfg.getInitialization().setUsers(List.of(DEFAULT_USER));
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldRejectForbiddenActiveProcessInstanceDeletionWithItsStorageOrdinal() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId).startEvent().userTask().endEvent().done())
        .deploy(DEFAULT_USER.getUsername());
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).create(DEFAULT_USER.getUsername());
    final var user = createUser();

    // when
    final var rejection =
        engine
            .historyDeletion()
            .processInstance(processInstanceKey)
            .expectRejection()
            .delete(user.getUsername());

    // then
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(rejection.getValue().getResourceStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }

  private UserRecordValue createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getValue();
  }
}
