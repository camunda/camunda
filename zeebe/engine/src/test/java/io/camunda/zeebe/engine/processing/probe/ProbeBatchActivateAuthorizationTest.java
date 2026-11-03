/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.probe;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ProbeRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class ProbeBatchActivateAuthorizationTest {

  private static final ConfiguredUser ADMIN =
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
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(ADMIN)))
          .withSecurityConfig(
              cfg -> {
                final var defaultRoles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                defaultRoles.put("admin", Map.of("users", List.of(ADMIN.getUsername())));
                cfg.getInitialization().setDefaultRoles(defaultRoles);
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldCollectOnlyTheProbesOfTheProbeIdsThePollerMayAnswer() {
    // given
    final var allowed = "validate-configuration/io.camunda:allowed-" + UUID.randomUUID() + ":1";
    final var denied = "validate-configuration/io.camunda:denied-" + UUID.randomUUID() + ":1";
    final long allowedProbe =
        engine.probe().withProbeId(allowed).withInput("={}").create(ADMIN.getUsername()).getKey();
    engine.probe().withProbeId(denied).withInput("={}").create(ADMIN.getUsername());
    final var poller = createUser();
    allowToAnswer(poller, allowed);

    // when
    final var activated =
        engine.probe().activateAs(poller, List.of(allowed, denied), "worker-a", 4);

    // then
    assertThat(activated.getValue().getProbeKeys()).containsExactly(allowedProbe);
    assertThat(activated.getValue().getProbes())
        .extracting(ProbeRecordValue::getProbeId)
        .containsExactly(allowed);
  }

  private String createUser() {
    return engine
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create()
        .getValue()
        .getUsername();
  }

  private void allowToAnswer(final String username, final String probeId) {
    engine
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.ANSWER)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.PROBE)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(probeId)
        .create(ADMIN.getUsername());
  }
}
