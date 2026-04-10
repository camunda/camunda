/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.auditlog;

import static io.camunda.it.auditlog.AuditLogUtils.DEFAULT_USERNAME;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_A;
import static io.camunda.it.auditlog.AuditLogUtils.PROCESS_A_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.search.enums.AuditLogActorTypeEnum;
import io.camunda.client.api.search.enums.AuditLogEntityTypeEnum;
import io.camunda.client.api.search.enums.AuditLogOperationTypeEnum;
import io.camunda.qa.util.auth.Authenticated;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.qa.util.multidb.MultiDbTestApplication;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Verifies that audit log entries contain correct actor information when authorizations are
 * disabled. This configuration (auth enabled, authorizations disabled) triggered a bug where
 * identity claims were dropped along with authorization claims.
 */
@MultiDbTest
@DisabledIfSystemProperty(named = "test.integration.camunda.database.type", matches = "AWS_OS")
public class AuditLogWithoutAuthorizationsIT {

  @MultiDbTestApplication
  static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withBasicAuth()
          .withAuthorizationsDisabled()
          // withAuthenticatedAccess must be done after disabling authorizations as that implicitly
          // set withUnauthenticatedAccess()
          // TODO: decouple authorizations & authentication setups
          .withAuthenticatedAccess();

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldSetActorWhenAuthorizationsDisabled(
      final boolean useRestApi, @Authenticated final CamundaClient client) {
    client.newDeployResourceCommand().addProcessModel(PROCESS_A, "a.bpmn").send().join();

    final var commandStep1 = client.newCreateInstanceCommand();
    if (useRestApi) {
      commandStep1.useRest();
    } else {
      commandStep1.useGrpc();
    }
    final var instance = commandStep1.bpmnProcessId(PROCESS_A_ID).latestVersion().send().join();

    Awaitility.await("Audit log entry is created for the process instance")
        .ignoreExceptionsInstanceOf(ProblemException.class)
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var auditLogs =
                  client
                      .newAuditLogSearchRequest()
                      .filter(
                          f ->
                              f.entityType(AuditLogEntityTypeEnum.PROCESS_INSTANCE)
                                  .operationType(AuditLogOperationTypeEnum.CREATE)
                                  .processInstanceKey("" + instance.getProcessInstanceKey()))
                      .send()
                      .join();

              assertThat(auditLogs.items())
                  .hasSize(1)
                  .allSatisfy(
                      log -> {
                        assertThat(log.getProcessInstanceKey())
                            .isEqualTo("" + instance.getProcessInstanceKey());
                        assertThat(log.getActorId()).isEqualTo(DEFAULT_USERNAME);
                        assertThat(log.getActorType()).isEqualTo(AuditLogActorTypeEnum.USER);
                      });
            });
  }
}
