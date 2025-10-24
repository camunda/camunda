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
import static org.awaitility.Awaitility.await;

import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.SignalClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
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
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.UserRecordValue;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
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

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldWriteDistributingRecordsForOtherPartitions() {
    // given
    final String signalName = newRandomSignal();
    final long deploymentKey =
        deploySignalCatchingProcess(Strings.newRandomValidBpmnId(), signalName);
    assertThat(deploymentKey).isPositive();
    final SignalClient signalClient = ENGINE.signal().withSignalName(signalName);

    // when
    final long signalKey = signalClient.broadcast(DEFAULT_USER.getUsername()).getKey();

    // then
    final Set<Integer> expectedTargets =
        IntStream.rangeClosed(1, PARTITION_COUNT)
            .filter(p -> p != Protocol.DEPLOYMENT_PARTITION)
            .boxed()
            .collect(java.util.stream.Collectors.toSet());

    final var distribution =
        RecordingExporter.commandDistributionRecords()
            .withIntent(CommandDistributionIntent.DISTRIBUTING)
            .valueFilter(v -> v.getValueType() == ValueType.SIGNAL)
            .limit(expectedTargets.size())
            .asList();

    assertThat(distribution).allMatch(r -> r.getKey() == signalKey);
    assertThat(distribution)
        .extracting(r -> r.getValue().getPartitionId())
        .containsExactlyInAnyOrderElementsOf(expectedTargets);
  }

  @Test
  public void shouldTriggerMultipleSignalCatchEvent() {
    // given
    final String signalName = newRandomSignal();
    deployProcess("wf_1", "catch1", signalName);
    deployProcess("wf_2", "catch2", signalName);

    final long pi1 = createProcessInstance("wf_1");
    final long pi2 = createProcessInstance("wf_2");

    waitForSignalSubscriptions(signalName, 2);

    // when
    ENGINE.signal().withSignalName(signalName).broadcast(DEFAULT_USER.getUsername());

    // then
    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(pi1)
                .limitToProcessInstanceCompleted())
        .extracting(r -> r.getValue().getElementId(), Record::getIntent)
        .containsSubsequence(
            tuple("catch1", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("catch1", ProcessInstanceIntent.ELEMENT_COMPLETED),
            tuple("wf_1", ProcessInstanceIntent.ELEMENT_COMPLETING),
            tuple("wf_1", ProcessInstanceIntent.ELEMENT_COMPLETED));

    assertThat(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(pi2)
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
    final String signalName = newRandomSignal();
    final String processId = Strings.newRandomValidBpmnId();
    final String otherProcessId = Strings.newRandomValidBpmnId();
    deployProcess(processId, "catch_main", signalName);
    deployProcess(otherProcessId, "catch_other", signalName);

    createProcessInstance(processId, 1);
    createProcessInstance(otherProcessId, 2);

    final UserRecordValue user = createUser();
    grantProcessPermission(user.getUsername(), processId);

    waitForSignalSubscriptions(signalName, 2);

    // when
    ENGINE.signal().withSignalName(signalName).broadcastWithMetadata(user.getUsername());

    // then (rejection on partition hosting unauthorized process)
    assertThat(
            RecordingExporter.signalRecords(SignalIntent.BROADCAST)
                .withSignalName(signalName)
                .withPartitionId(2)
                .withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionReason(
                    "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                        .formatted(otherProcessId))
                .exists())
        .isTrue();

    // then - the signal distribution is still finished because a redistribution is not necessary
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .withDistributionValueType(ValueType.SIGNAL)
                .withDistributionIntent(SignalIntent.BROADCAST)
                .exists())
        .isTrue();
  }

  @Test
  public void shouldRejectBroadcastForOneUnauthorizedProcess() {
    // given
    final String signalName = newRandomSignal();
    final String processId = Strings.newRandomValidBpmnId();
    final String otherProcessId = Strings.newRandomValidBpmnId();
    deployProcess(processId, "catch_main", signalName);
    deployProcess(otherProcessId, "catch_other", signalName);

    createProcessInstance(processId, 2);
    createProcessInstance(otherProcessId, 2);

    final UserRecordValue user = createUser();
    grantProcessPermission(user.getUsername(), processId);

    waitForSignalSubscriptions(signalName, 2);

    // when
    ENGINE.signal().withSignalName(signalName).broadcastWithMetadata(user.getUsername());

    // then - the command is rejected on the partition that hosts the unauthorized process instance
    assertThat(
            RecordingExporter.signalRecords(SignalIntent.BROADCAST)
                .withSignalName(signalName)
                .withPartitionId(2)
                .withRecordType(RecordType.COMMAND_REJECTION)
                .withRejectionReason(
                    "Insufficient permissions to perform operation 'UPDATE_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION', required resource identifiers are one of '[*, %s]'"
                        .formatted(otherProcessId))
                .exists())
        .isTrue();

    // then - the signal distribution is still finished because a redistribution is not necessary
    assertThat(
            RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
                .withDistributionValueType(ValueType.SIGNAL)
                .withDistributionIntent(SignalIntent.BROADCAST)
                .exists())
        .isTrue();
  }

  // --- helpers -------------------------------------------------------------------------------

  private static String newRandomSignal() {
    return "sig_" + UUID.randomUUID();
  }

  private static long deploySignalCatchingProcess(final String bpmnId, final String signalName) {
    return ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(bpmnId).startEvent().signal(signalName).endEvent().done())
        .deploy(DEFAULT_USER.getUsername())
        .getKey();
  }

  private static void deployProcess(
      final String processId, final String catchId, final String signalName) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .intermediateCatchEvent(catchId)
                .signal(signalName)
                .endEvent()
                .done())
        .deploy(DEFAULT_USER.getUsername());
  }

  private static long createProcessInstance(final String processId) {
    return ENGINE.processInstance().ofBpmnProcessId(processId).create(DEFAULT_USER.getUsername());
  }

  private static void createProcessInstance(final String processId, final int partition) {
    ENGINE
        .processInstance()
        .ofBpmnProcessId(processId)
        .onPartition(partition)
        .create(DEFAULT_USER.getUsername());
  }

  private static UserRecordValue createUser() {
    return ENGINE
        .user()
        .newUser(UUID.randomUUID().toString())
        .withPassword(UUID.randomUUID().toString())
        .withName(UUID.randomUUID().toString())
        .withEmail(UUID.randomUUID().toString())
        .create(DEFAULT_USER.getUsername())
        .getValue();
  }

  private static void grantProcessPermission(final String username, final String processId) {
    ENGINE
        .authorization()
        .newAuthorization()
        .withPermissions(PermissionType.UPDATE_PROCESS_INSTANCE)
        .withOwnerId(username)
        .withOwnerType(AuthorizationOwnerType.USER)
        .withResourceType(AuthorizationResourceType.PROCESS_DEFINITION)
        .withResourceMatcher(AuthorizationResourceMatcher.ID)
        .withResourceId(processId)
        .create(DEFAULT_USER.getUsername());
  }

  private static void waitForSignalSubscriptions(final String signalName, final int processCount) {
    await("signal subscriptions created for " + signalName)
        .pollInterval(Duration.ofMillis(10))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        RecordingExporter.signalSubscriptionRecords(
                                SignalSubscriptionIntent.CREATED)
                            .withSignalName(signalName)
                            .limit(processCount)
                            .count())
                    .isEqualTo(processCount));
  }
}
