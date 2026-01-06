/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DecisionIntent;
import io.camunda.zeebe.protocol.record.intent.DecisionRequirementsIntent;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class ResourceEventsClaimsTest {

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
          .withSecurityConfig(
              cfg -> {
                cfg.getAuthorizations().setEnabled(true);
                cfg.getInitialization().setUsers(List.of(DEFAULT_USER));
                cfg.getInitialization()
                    .getDefaultRoles()
                    .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername())));
              });

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldIncludeClaimsInProcessCreatedEvents() {
    // when
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess("testProcess").startEvent().endEvent().done())
        .deploy(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.processRecords(ProcessIntent.CREATED)
            .withBpmnProcessId("testProcess")
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInProcessDeletedEvents() {
    // given
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess("testProcess").startEvent().endEvent().done())
        .deploy(DEFAULT_USER.getUsername());

    final var processDefinitionKey =
        RecordingExporter.processRecords(ProcessIntent.CREATED)
            .withBpmnProcessId("testProcess")
            .getFirst()
            .getKey();

    // when
    engine
        .resourceDeletion()
        .withResourceKey(processDefinitionKey)
        .delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.processRecords(ProcessIntent.DELETED)
            .withProcessDefinitionKey(processDefinitionKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInDecisionRequirementsCreatedEvents() {
    // when
    engine
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .deploy(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.decisionRequirementsRecords()
            .withIntent(DecisionRequirementsIntent.CREATED)
            .findFirst();
    final var decisionRecords =
        RecordingExporter.decisionRecords().withIntent(DecisionIntent.CREATED).limit(3L);
    assertAuthorizationClaims(record);
    decisionRecords.forEach(decisionRecord -> assertAuthorizationClaims(decisionRecord));
  }

  @Test
  public void shouldIncludeClaimsInDecisionRequirementsDeletedEvents() {
    // given
    engine
        .deployment()
        .withXmlClasspathResource("/dmn/drg-force-user.dmn")
        .deploy(DEFAULT_USER.getUsername());

    final var drgKey =
        RecordingExporter.decisionRequirementsRecords()
            .withIntent(DecisionRequirementsIntent.CREATED)
            .getFirst()
            .getKey();

    // when
    engine.resourceDeletion().withResourceKey(drgKey).delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.decisionRequirementsRecords()
            .withIntent(DecisionRequirementsIntent.DELETED)
            .withDecisionRequirementsKey(drgKey)
            .findFirst();
    final var decisionRecords =
        RecordingExporter.decisionRecords().withIntent(DecisionIntent.DELETED).limit(3L);
    assertAuthorizationClaims(record);
    decisionRecords.forEach(decisionRecord -> assertAuthorizationClaims(decisionRecord));
  }

  @Test
  public void shouldIncludeClaimsInFormCreatedEvents() {
    // when
    engine
        .deployment()
        .withJsonClasspathResource("/form/test-form-1.form")
        .deploy(DEFAULT_USER.getUsername());

    // then
    final var record = RecordingExporter.formRecords().withIntent(FormIntent.CREATED).findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInFormDeletedEvents() {
    // given
    engine
        .deployment()
        .withJsonClasspathResource("/form/test-form-1.form")
        .deploy(DEFAULT_USER.getUsername());

    final var formKey =
        RecordingExporter.formRecords().withIntent(FormIntent.CREATED).getFirst().getKey();

    // when
    engine.resourceDeletion().withResourceKey(formKey).delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.formRecords()
            .withIntent(FormIntent.DELETED)
            .withFormKey(formKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInResourceCreatedEvents() {
    // when
    engine
        .deployment()
        .withJsonClasspathResource("/resource/test-rpa-1.rpa")
        .deploy(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.resourceRecords().withIntent(ResourceIntent.CREATED).findFirst();
    assertAuthorizationClaims(record);
  }

  @Test
  public void shouldIncludeClaimsInResourceDeletedEvents() {
    // given
    engine
        .deployment()
        .withJsonClasspathResource("/resource/test-rpa-1.rpa")
        .deploy(DEFAULT_USER.getUsername());

    final var resourceKey =
        RecordingExporter.resourceRecords().withIntent(ResourceIntent.CREATED).getFirst().getKey();

    // when
    engine.resourceDeletion().withResourceKey(resourceKey).delete(DEFAULT_USER.getUsername());

    // then
    final var record =
        RecordingExporter.resourceRecords()
            .withIntent(ResourceIntent.DELETED)
            .withResourceKey(resourceKey)
            .findFirst();
    assertAuthorizationClaims(record);
  }

  private void assertAuthorizationClaims(final java.util.Optional<?> record) {
    assertThat(record).isPresent();
    final var typedRecord = (Record<?>) record.get();
    assertAuthorizationClaims(typedRecord);
  }

  private void assertAuthorizationClaims(final Record record) {
    assertThat(record.getAuthorizations())
        .containsEntry(Authorization.AUTHORIZED_USERNAME, DEFAULT_USER.getUsername());
  }
}
