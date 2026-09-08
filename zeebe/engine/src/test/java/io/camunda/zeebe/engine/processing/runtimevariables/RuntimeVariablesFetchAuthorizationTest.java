/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.runtimevariables;

import io.camunda.security.api.model.config.initialization.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;

public class RuntimeVariablesFetchAuthorizationTest {

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
                final var roles = new HashMap<>(cfg.getInitialization().getDefaultRoles());
                roles.put("admin", Map.of("users", List.of(ADMIN.getUsername())));
                cfg.getInitialization().setDefaultRoles(roles);
              });

  @Test
  public void shouldRejectWithoutReadProcessInstancePermission() {
    // given
    final var processId = "runtime-variables-auth";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask("task", task -> task.zeebeJobType("test"))
                .endEvent()
                .done())
        .deploy(ADMIN.getUsername());
    final var processInstanceKey =
        engine.processInstance().ofBpmnProcessId(processId).create(ADMIN.getUsername());
    final var unauthorizedUser = UUID.randomUUID().toString();
    engine
        .user()
        .newUser(unauthorizedUser)
        .withPassword(UUID.randomUUID().toString())
        .withName("Unauthorized")
        .withEmail(unauthorizedUser + "@example.com")
        .create();
    final var runtimeVariables = engine.runtimeVariables();

    // when
    final var rejection =
        runtimeVariables.withScopeKey(processInstanceKey).fetchRejection(unauthorizedUser);

    // then
    Assertions.assertThat(rejection)
        .hasRejectionType(RejectionType.FORBIDDEN)
        .hasRejectionReason(
            "Insufficient permissions to perform operation 'READ_PROCESS_INSTANCE' on resource"
                + " 'PROCESS_DEFINITION'");
  }
}
