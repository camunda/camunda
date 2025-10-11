/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.SignalClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.SignalIntent;
import io.camunda.zeebe.protocol.record.intent.SignalSubscriptionIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceMatcher;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class BroadcastSignalMultiplePartitionsTest {

  private static final ConfiguredUser DEFAULT_USER =
      new ConfiguredUser(
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString(),
          UUID.randomUUID().toString());

  private static final int PARTITION_COUNT = 3;

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.multiplePartition(PARTITION_COUNT)
          .withIdentitySetup()
          .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(true))
          .withSecurityConfig(cfg -> cfg.getInitialization().setUsers(List.of(DEFAULT_USER)))
          .withSecurityConfig(
              cfg ->
                  cfg.getInitialization()
                      .getDefaultRoles()
                      .put("admin", Map.of("users", List.of(DEFAULT_USER.getUsername()))));

  private final String signalName = UUID.randomUUID().toString();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SignalClient signalClient = ENGINE.signal().withSignalName(signalName);

  @Test
  public void shouldWriteDistributingRecordsForOtherPartitions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var process =
        Bpmn.createExecutableProcess(processId).startEvent().signal(signalName).endEvent().done();
    // when
    ENGINE.deployment().withXmlResource(process).deploy(DEFAULT_USER.getUsername());

    // then
    final var signalKey = signalClient.broadcast(DEFAULT_USER.getUsername()).getKey();
    final var commandDistributionRecords =
        RecordingExporter.commandDistributionRecords()
            .withIntent(CommandDistributionIntent.DISTRIBUTING)
            .valueFilter(v -> v.getValueType().equals(ValueType.SIGNAL))
            .limit(2)
            .asList();

    assertThat(commandDistributionRecords).extracting(Record::getKey).containsOnly(signalKey);

    assertThat(commandDistributionRecords)
        .extracting(Record::getValue)
        .extracting(CommandDistributionRecordValue::getPartitionId)
        .containsExactly(2, 3);
  }

  @Test
  public void shouldTriggerMultipleSignalCatchEvent() {
    // given
    final var process1 =
        Bpmn.createExecutableProcess("wf_1")
            .startEvent()
            .intermediateCatchEvent("catch1")
            .signal(signalName)
            .endEvent()
            .done();
    final var process2 =
        Bpmn.createExecutableProcess("wf_2")
            .startEvent()
            .intermediateCatchEvent("catch2")
            .signal(signalName)
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(process1).deploy(DEFAULT_USER.getUsername());
    ENGINE.deployment().withXmlResource(process2).deploy(DEFAULT_USER.getUsername());

    final var processInstanceKey1 = ENGINE.processInstance().ofBpmnProcessId("wf_1").create();
    final var processInstanceKey2 = ENGINE.processInstance().ofBpmnProcessId("wf_2").create();

    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(signalName)
                .limit(2))
        .extracting(record -> record.getValue().getCatchEventId())
        .containsOnly("catch1", "catch2");

    // when
    signalClient.broadcast(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey1)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("catch1", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("catch1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("wf_1", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("wf_1", ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey2)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("catch2", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("catch2", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("wf_2", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("wf_2", ProcessInstanceIntent.ELEMENT_COMPLETED));
  }

  @Test
  public void shouldAuthorizeOnAllPartitions() {
    // given
    final var processId = Strings.newRandomValidBpmnId();
    final var otherProcessId = Strings.newRandomValidBpmnId();
    final var user = createUser();
    deployProcess(processId);
    deployProcess(otherProcessId);
    addPermissionsToUser(user.getUsername(), PermissionType.UPDATE_PROCESS_INSTANCE, processId);
    createProcessInstance(processId, 1);
    createProcessInstance(otherProcessId, 2);

    // wait until both subscriptions are created
    assertThat(
            RecordingExporter.signalSubscriptionRecords(SignalSubscriptionIntent.CREATED)
                .withSignalName(signalName)
                .limit(2)
                .count())
        .isEqualTo(2);

    // when
    ENGINE.signal().withSignalName(signalName).broadcast(user.getUsername());

    // then
    RecordingExporter.signalRecords(SignalIntent.BROADCAST)
        .withSignalName(signalName)
        .limit(1)
        .withPartitionId(2)
        .withRecordType(RecordType.COMMAND_REJECTION)
        .withRejectionReason(
            "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                .formatted(otherProcessId))
        .exists();
  }

  private long createProcessInstance(final String processId, final int partitionId) {
    return ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .onPartition(partitionId)
        .create(DEFAULT_USER.getUsername());
  }

  private UserRecordValue createUser() {
    return ENGINE
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create(DEFAULT_USER.getUsername())
        .getValue();
  }

  private static void addPermissionsToUser(
      final String username, final PermissionType permissionType, final String processId) {
    addPermissionsToUser(username, permissionType, processId, AuthorizationResourceMatcher.ID);
  }

  private static void addPermissionsToUser(
      final String username,
      final PermissionType permissionType,
      final String processId,
      final AuthorizationResourceMatcher matcher) {
    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(permissionType)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withResourceMatcher(matcher)
        .withResourceId(processId)
        .create(DEFAULT_USER.getUsername());
  }

  private void deployProcess(final String processId) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent()
                .signal(signalName)
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }
}
