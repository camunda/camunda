/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.policy;

import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.apply;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.chunk;
import static io.camunda.zeebe.engine.processing.policy.PolicyUpdateApplyTest.uid;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.protocol.impl.record.value.policyupdate.PolicyUpdateRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.PolicyUpdateIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateRecordValue;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public final class PolicyUpdateSwitchVersionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldSwitchToThePolicyVersionOfTheAppliedUpdate() {
    // given
    final var chunk = chunk().setSeqNo(42);

    // when
    apply(ENGINE, chunk);

    // then
    final var switched = versionSwitched(chunk.getUpdateId()).getValue();
    assertThat(switched.getPolicyId()).isEqualTo(chunk.getPolicyId());
    assertThat(switched.getPolicyVersion()).isEqualTo(chunk.getPolicyVersion());
    assertThat(switched.getSeqNo()).isEqualTo(42);
  }

  @Test
  public void shouldSwitchAfterTheIdentityCommandsOfTheUpdate() {
    // given
    final var roleId = "role-" + uid();
    final var chunk =
        chunk().setRoles(List.<RoleRecordValue>of(new RoleRecord().setRoleId(roleId)));

    // when
    apply(ENGINE, chunk);

    // then
    final var switched = versionSwitched(chunk.getUpdateId());
    final var roleCreated =
        RecordingExporter.roleRecords(RoleIntent.CREATED).withRoleId(roleId).getFirst();
    assertThat(roleCreated.getPosition()).isLessThan(switched.getPosition());
  }

  @Test
  public void shouldRejectASwitchToAVersionThatIsNotNewer() {
    // given
    final var chunk = chunk();
    apply(ENGINE, chunk);
    versionSwitched(chunk.getUpdateId());
    final var again = chunk().setPolicyVersion(chunk.getPolicyVersion());

    // when
    switchVersion(RecordToWrite.commandWithoutClaims(), again);

    // then
    final var rejection = switchRejection(again.getUpdateId());
    assertThat(rejection.getRejectionType()).isEqualTo(RejectionType.INVALID_STATE);
    assertThat(rejection.getRejectionReason()).contains("already " + chunk.getPolicyVersion());
  }

  @Test
  public void shouldRejectASwitchSentAsClientRequest() {
    // given
    final var update = chunk();

    // when
    switchVersion(RecordToWrite.commandFromRequest("user-" + uid()), update);

    // then
    assertThat(switchRejection(update.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.FORBIDDEN);
  }

  @Test
  public void shouldRejectAClientRequestThatLooksLikeADistributedSwitch() {
    // given a client request with the key of another partition
    final var update = chunk();

    // when
    ENGINE.writeRecords(
        RecordToWrite.commandFromRequest("user-" + uid())
            .key(Protocol.encodePartitionId(2, 1L))
            .policyUpdate(PolicyUpdateIntent.SWITCH_VERSION, update));

    // then
    assertThat(switchRejection(update.getUpdateId()).getRejectionType())
        .isEqualTo(RejectionType.FORBIDDEN);
  }

  /** Writes the switch as a new command, like the follow-up of the last applied chunk. */
  private static void switchVersion(final RecordToWrite command, final PolicyUpdateRecord update) {
    ENGINE.writeRecords(command.key(-1L).policyUpdate(PolicyUpdateIntent.SWITCH_VERSION, update));
  }

  static Record<PolicyUpdateRecordValue> versionSwitched(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.VERSION_SWITCHED)
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }

  private static Record<PolicyUpdateRecordValue> switchRejection(final String updateId) {
    return RecordingExporter.policyUpdateRecords(PolicyUpdateIntent.SWITCH_VERSION)
        .onlyCommandRejections()
        .filter(record -> record.getValue().getUpdateId().equals(updateId))
        .getFirst();
  }
}
