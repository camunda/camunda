/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.StartEventBuilder;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class TenantAwareTimerStartEventTest {
  public static final String USERNAME = "username";
  private static final String PROCESS_ID = "process";
  private static final String TENANT = "tenant-a";

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withIdentitySetup()
          .withSecurityConfig(config -> config.getMultiTenancy().setChecksEnabled(true))
          .withSecurityConfig(
              config ->
                  config
                      .getInitialization()
                      .setUsers(List.of(new ConfiguredUser(USERNAME, "password", "name", "email"))))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(USERNAME))));

  private long processDefinitionKey;

  private static BpmnModelInstance processWithTimerStartEvent(
      final Consumer<StartEventBuilder> consumer) {
    final var builder = Bpmn.createExecutableProcess(PROCESS_ID).startEvent("startEvent");
    consumer.accept(builder);
    return builder.endEvent().done();
  }

  @Before
  public void init() {
    engine.tenant().newTenant().withTenantId(TENANT).create();
    engine.tenant().addEntity(TENANT).withEntityId(USERNAME).withEntityType(EntityType.USER).add();
    final var process = processWithTimerStartEvent(c -> c.timerWithCycle("R2/PT1M"));
    final var deployment =
        engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
    processDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();
  }

  @Test
  public void shouldCreateTimer() {
    // then
    final var timerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();

    Assertions.assertThat(timerRecord.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldTriggerTimer() {
    // when
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.TRIGGERED)
                .withProcessDefinitionKey(processDefinitionKey)
                .getFirst())
        .extracting(r -> r.getValue().getTenantId(), Record::getIntent)
        .containsSequence(TENANT, TimerIntent.TRIGGERED);
  }

  @Test
  public void shouldRescheduleTimer() {
    // given
    final var timerCreated =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();

    // when
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    final var timerRescheduled =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .limit(2)
            .getLast();

    assertThat(timerRescheduled.getKey()).isGreaterThan(timerCreated.getKey());
    Assertions.assertThat(timerRescheduled.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldCreateProcessInstance() {
    // when
    engine.increaseTime(Duration.ofMinutes(1));

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .onlyEvents()
                .withProcessDefinitionKey(processDefinitionKey)
                .withElementType(BpmnElementType.START_EVENT)
                .withEventType(BpmnEventType.TIMER)
                .limit("startEvent", ProcessInstanceIntent.ELEMENT_COMPLETED))
        .extracting(r -> r.getValue().getTenantId(), Record::getIntent)
        .containsSequence(
            tuple(TENANT, ProcessInstanceIntent.ELEMENT_ACTIVATING),
            tuple(TENANT, ProcessInstanceIntent.ELEMENT_ACTIVATED),
            tuple(TENANT, ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple(TENANT, ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldCancelTimerWhenDeployingNewProcessDefinitionVersionWithoutTimer() {
    // when (deploy new version of process)
    final var process = processWithTimerStartEvent(StartEventBuilder::done);
    engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();

    // then
    final var canceledEvent =
        RecordingExporter.timerRecords(TimerIntent.CANCELED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();

    Assertions.assertThat(canceledEvent.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldCreateNewTimerWhenDeployingNewProcessDefinitionVersionWithTimer() {
    // given
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    // when (deploy new version of process)
    final var process = processWithTimerStartEvent(c -> c.timerWithCycle("R/PT1M"));
    final var deployment =
        engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
    final var latestProcessDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then
    final var canceledEvent =
        RecordingExporter.timerRecords(TimerIntent.CANCELED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();
    Assertions.assertThat(canceledEvent.getValue()).hasTenantId(TENANT);

    final var newCreatedTimer =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(latestProcessDefinitionKey)
            .getFirst();
    Assertions.assertThat(newCreatedTimer.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldCancelTimerWhenDeletingProcessDefinition() {
    // given
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    // when
    engine
        .resourceDeletion()
        .withResourceKey(processDefinitionKey)
        .withAuthorizedTenantIds(TENANT)
        .delete(USERNAME);

    // then
    final var canceledEvent =
        RecordingExporter.timerRecords(TimerIntent.CANCELED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst();
    Assertions.assertThat(canceledEvent.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldRecreateTimerWhenDeletingLatestProcessDefinition() {
    // given
    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    final var process = processWithTimerStartEvent(c -> c.timerWithCycle("R/PT1M"));
    final var deployment =
        engine.deployment().withXmlResource(process).withTenantId(TENANT).deploy();
    final var latestProcessDefinitionKey =
        deployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // when
    engine
        .resourceDeletion()
        .withResourceKey(latestProcessDefinitionKey)
        .withAuthorizedTenantIds(TENANT)
        .delete(USERNAME);

    // then
    final var canceledEvent =
        RecordingExporter.timerRecords(TimerIntent.CANCELED)
            .withProcessDefinitionKey(latestProcessDefinitionKey)
            .getFirst();
    Assertions.assertThat(canceledEvent.getValue()).hasTenantId(TENANT);

    final var recreatedTimerRecord =
        RecordingExporter.timerRecords(TimerIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .limit(2)
            .getLast();
    Assertions.assertThat(recreatedTimerRecord.getValue()).hasTenantId(TENANT);
  }

  @Test
  public void shouldCreateTimerForAnotherTenantWhenHavingSameBPMNProcessId() {
    // given
    final var tenantB = "tenant-b";
    final var processWithSameBPMNProcessId =
        processWithTimerStartEvent(c -> c.timerWithCycle("R2/PT1M"));

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isTrue();

    // when
    final var tenantBDeployment =
        engine
            .deployment()
            .withXmlResource(processWithSameBPMNProcessId)
            .withTenantId(tenantB)
            .deploy();
    final var tenantBProcessDefinitionKey =
        tenantBDeployment.getValue().getProcessesMetadata().get(0).getProcessDefinitionKey();

    // then
    assertThat(
            RecordingExporter.records()
                .limit(
                    record ->
                        record.getIntent().equals(DeploymentIntent.CREATED)
                            && ((DeploymentRecordValue) record.getValue()).getDeploymentKey()
                                == tenantBDeployment.getKey())
                .timerRecords()
                .withIntent(TimerIntent.CANCELED)
                .withProcessDefinitionKey(processDefinitionKey)
                .exists())
        .isFalse();

    assertThat(
            RecordingExporter.timerRecords(TimerIntent.CREATED)
                .withProcessDefinitionKey(tenantBProcessDefinitionKey)
                .exists())
        .isTrue();
  }
}
