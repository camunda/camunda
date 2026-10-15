/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.protocol.model.BackupType;
import io.camunda.gateway.protocol.model.CheckpointType;
import io.camunda.gateway.protocol.model.PartitionBackupRange;
import io.camunda.gateway.protocol.model.StateCode;
import io.camunda.service.ClusterHistoryBackupServices.ClusterHistoryBackup;
import io.camunda.service.ClusterHistoryBackupServices.ClusterHistoryBackupTaken;
import io.camunda.service.ClusterHistoryBackupServices.PhysicalTenantBackupState;
import io.camunda.service.ClusterHistoryBackupServices.PhysicalTenantBackupTaken;
import io.camunda.service.ClusterHistoryBackupServices.TenantBackupStateCode;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackup;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackupStates;
import io.camunda.service.ClusterRuntimeBackupServices.ClusterRuntimeBackupTaken;
import io.camunda.service.ClusterRuntimeBackupServices.PhysicalTenantRuntimeBackup;
import io.camunda.service.ClusterRuntimeBackupServices.PhysicalTenantRuntimeBackupState;
import io.camunda.service.ClusterRuntimeBackupServices.PhysicalTenantRuntimeBackupTaken;
import io.camunda.service.ClusterRuntimeBackupServices.TakeOutcome;
import io.camunda.service.RuntimeBackupServices.RuntimeBackupState;
import io.camunda.service.backup.HistoryBackupSnapshot;
import io.camunda.service.backup.HistoryBackupState;
import io.camunda.service.backup.HistoryBackupTaken;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse;
import io.camunda.zeebe.protocol.impl.encoding.BackupRangesResponse.CheckpointInfo;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

final class BackupResponseMapperTest {

  private static final String CREATED_AT = "2024-01-01T10:00:00Z";
  private static final String UPDATED_AT = "2024-01-02T11:30:00Z";
  private static final Instant CHECKPOINT_INSTANT = Instant.parse("2024-03-04T05:06:07Z");

  @Test
  void shouldMapTakeBackupResponse() {
    // when
    final var response = BackupResponseMapper.toTakeBackupResponse(42L);

    // then
    assertThat(response.getBackupId()).isEqualTo(42L);
  }

  @Test
  void shouldMapBackupInfoWithAllPartitionFields() {
    // given
    final var partition =
        new PartitionBackupStatus(
            3,
            BackupStatusCode.COMPLETED,
            Optional.of("partition-failure"),
            Optional.of(CREATED_AT),
            Optional.of(UPDATED_AT),
            Optional.of("snapshot-1"),
            OptionalLong.of(10L),
            OptionalLong.of(20L),
            OptionalInt.of(7),
            Optional.of("8.9.0"));
    final var status =
        new BackupStatus(5L, State.COMPLETED, Optional.of("backup-failure"), List.of(partition));

    // when
    final var info = BackupResponseMapper.toBackupInfo(status);

    // then
    assertThat(info.getBackupId()).isEqualTo(5L);
    assertThat(info.getState()).isEqualTo(StateCode.COMPLETED);
    assertThat(info.getFailureReason()).isEqualTo("backup-failure");
    assertThat(info.getDetails()).hasSize(1);
    final var details = info.getDetails().getFirst();
    assertThat(details.getPartitionId()).isEqualTo(3);
    assertThat(details.getState()).isEqualTo(StateCode.COMPLETED);
    assertThat(details.getFailureReason()).isEqualTo("partition-failure");
    assertThat(details.getCreatedAt()).isEqualTo(formatted(CREATED_AT));
    assertThat(details.getLastUpdatedAt()).isEqualTo(formatted(UPDATED_AT));
    assertThat(details.getSnapshotId()).isEqualTo("snapshot-1");
    assertThat(details.getFirstLogPosition()).isEqualTo(10L);
    assertThat(details.getCheckpointPosition()).isEqualTo(20L);
    assertThat(details.getBrokerId()).isEqualTo(7);
    assertThat(details.getBrokerVersion()).isEqualTo("8.9.0");
  }

  @Test
  void shouldMapBackupInfoWithAbsentOptionalFieldsAsNull() {
    // given
    final var partition =
        new PartitionBackupStatus(
            1,
            BackupStatusCode.DOES_NOT_EXIST,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            OptionalLong.empty(),
            OptionalLong.empty(),
            OptionalInt.empty(),
            Optional.empty());
    final var status =
        new BackupStatus(5L, State.DOES_NOT_EXIST, Optional.empty(), List.of(partition));

    // when
    final var info = BackupResponseMapper.toBackupInfo(status);

    // then
    assertThat(info.getFailureReason()).isNull();
    final var details = info.getDetails().getFirst();
    assertThat(details.getFailureReason()).isNull();
    assertThat(details.getCreatedAt()).isNull();
    assertThat(details.getLastUpdatedAt()).isNull();
    assertThat(details.getSnapshotId()).isNull();
    assertThat(details.getFirstLogPosition()).isNull();
    assertThat(details.getCheckpointPosition()).isNull();
    assertThat(details.getBrokerId()).isNull();
    assertThat(details.getBrokerVersion()).isNull();
  }

  @Test
  void shouldMapBackupInfoList() {
    // given
    final var first = new BackupStatus(1L, State.COMPLETED, Optional.empty(), List.of());
    final var second = new BackupStatus(2L, State.FAILED, Optional.of("boom"), List.of());

    // when
    final var infos = BackupResponseMapper.toBackupInfoList(List.of(first, second));

    // then
    assertThat(infos).extracting(info -> info.getBackupId()).containsExactly(1L, 2L);
    assertThat(infos)
        .extracting(info -> info.getState())
        .containsExactly(StateCode.COMPLETED, StateCode.FAILED);
  }

  @ParameterizedTest
  @EnumSource(State.class)
  void shouldMapEveryBackupClientStateToStateCode(final State state) {
    // given
    final var status = new BackupStatus(1L, state, Optional.empty(), List.of());

    // when
    final var info = BackupResponseMapper.toBackupInfo(status);

    // then
    assertThat(info.getState().name()).isEqualTo(state.name());
  }

  @ParameterizedTest
  @EnumSource(
      value = BackupStatusCode.class,
      mode = Mode.EXCLUDE,
      names = {"NULL_VAL", "SBE_UNKNOWN"})
  void shouldMapEveryPartitionStatusCodeToStateCode(final BackupStatusCode code) {
    // given
    final var partition = partitionWithStatus(code);
    final var status =
        new BackupStatus(1L, State.IN_PROGRESS, Optional.empty(), List.of(partition));

    // when
    final var info = BackupResponseMapper.toBackupInfo(status);

    // then
    assertThat(info.getDetails().getFirst().getState().name()).isEqualTo(code.name());
  }

  @Test
  void shouldRejectUnknownPartitionStatusCode() {
    // given
    final var partition = partitionWithStatus(BackupStatusCode.NULL_VAL);
    final var status =
        new BackupStatus(1L, State.IN_PROGRESS, Optional.empty(), List.of(partition));

    // when / then
    assertThatThrownBy(() -> BackupResponseMapper.toBackupInfo(status))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Unknown BackupState");
  }

  @Test
  void shouldMapRuntimeBackupStateSortedByPartition() {
    // given
    final var checkpointState = new CheckpointStateResponse();
    checkpointState.setCheckpointStates(
        Set.of(
            checkpointState(
                2,
                11L,
                io.camunda.zeebe.protocol.record.value.management.CheckpointType.MANUAL_BACKUP),
            checkpointState(
                1,
                10L,
                io.camunda.zeebe.protocol.record.value.management.CheckpointType
                    .SCHEDULED_BACKUP)));
    checkpointState.setBackupStates(
        Set.of(
            checkpointState(
                2,
                21L,
                io.camunda.zeebe.protocol.record.value.management.CheckpointType.MANUAL_BACKUP),
            checkpointState(
                1,
                20L,
                io.camunda.zeebe.protocol.record.value.management.CheckpointType
                    .SCHEDULED_BACKUP)));
    final var ranges = new BackupRangesResponse();
    ranges.setRanges(
        List.of(
            new BackupRangesResponse.PartitionBackupRange(
                2,
                checkpointInfo(
                    30L,
                    io.camunda.zeebe.protocol.record.value.management.CheckpointType.MANUAL_BACKUP),
                checkpointInfo(
                    31L,
                    io.camunda.zeebe.protocol.record.value.management.CheckpointType
                        .SCHEDULED_BACKUP)),
            new BackupRangesResponse.PartitionBackupRange(1, null, null)));

    // when
    final var state =
        BackupResponseMapper.toRuntimeBackupState(new RuntimeBackupState(checkpointState, ranges));

    // then
    assertThat(state.getCheckpointStates())
        .extracting(s -> s.getPartitionId(), s -> s.getCheckpointId(), s -> s.getCheckpointType())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, 10L, CheckpointType.SCHEDULED_BACKUP),
            org.assertj.core.groups.Tuple.tuple(2, 11L, CheckpointType.MANUAL_BACKUP));
    assertThat(state.getCheckpointStates().getFirst().getCheckpointTimestamp())
        .isEqualTo(ResponseMapper.formatDate(OffsetDateTime.parse("2024-03-04T05:06:07Z")));
    assertThat(state.getBackupStates())
        .extracting(s -> s.getPartitionId(), s -> s.getCheckpointId(), s -> s.getCheckpointType())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(1, 20L, BackupType.SCHEDULED_BACKUP),
            org.assertj.core.groups.Tuple.tuple(2, 21L, BackupType.MANUAL_BACKUP));
    assertThat(state.getRanges())
        .extracting(PartitionBackupRange::getPartitionId)
        .containsExactly(1, 2);
  }

  @Test
  void shouldMapRangeWithoutBoundsAsNull() {
    // given
    final var ranges = new BackupRangesResponse();
    ranges.setRanges(List.of(new BackupRangesResponse.PartitionBackupRange(1, null, null)));

    // when
    final var state =
        BackupResponseMapper.toRuntimeBackupState(
            new RuntimeBackupState(new CheckpointStateResponse(), ranges));

    // then
    assertThat(state.getRanges()).hasSize(1);
    assertThat(state.getRanges().getFirst().getStart()).isNull();
    assertThat(state.getRanges().getFirst().getEnd()).isNull();
  }

  @Test
  void shouldMapRangeBoundsWithoutPartitionId() {
    // given
    final var ranges = new BackupRangesResponse();
    ranges.setRanges(
        List.of(
            new BackupRangesResponse.PartitionBackupRange(
                4,
                checkpointInfo(
                    30L,
                    io.camunda.zeebe.protocol.record.value.management.CheckpointType.MANUAL_BACKUP),
                checkpointInfo(
                    31L,
                    io.camunda.zeebe.protocol.record.value.management.CheckpointType
                        .SCHEDULED_BACKUP))));

    // when
    final var state =
        BackupResponseMapper.toRuntimeBackupState(
            new RuntimeBackupState(new CheckpointStateResponse(), ranges));

    // then
    final var range = state.getRanges().getFirst();
    assertThat(range.getPartitionId()).isEqualTo(4);
    assertThat(range.getStart().getCheckpointId()).isEqualTo(30L);
    assertThat(range.getStart().getCheckpointType()).isEqualTo(BackupType.MANUAL_BACKUP);
    assertThat(range.getStart().getPartitionId()).isNull();
    assertThat(range.getStart().getFirstLogPosition()).isEqualTo(100L);
    assertThat(range.getStart().getCheckpointPosition()).isEqualTo(200L);
    assertThat(range.getStart().getCheckpointTimestamp())
        .isEqualTo(ResponseMapper.formatDate(OffsetDateTime.parse("2024-03-04T05:06:07Z")));
    assertThat(range.getEnd().getCheckpointId()).isEqualTo(31L);
    assertThat(range.getEnd().getCheckpointType()).isEqualTo(BackupType.SCHEDULED_BACKUP);
  }

  @Test
  void shouldMapMarkerCheckpointTypeToCheckpointTypeButNotToBackupType() {
    // given
    final var checkpointState = new CheckpointStateResponse();
    final var marker =
        checkpointState(
            1, 10L, io.camunda.zeebe.protocol.record.value.management.CheckpointType.MARKER);
    checkpointState.setCheckpointStates(Set.of(marker));
    checkpointState.setBackupStates(Set.of(marker));

    // when
    final var state =
        BackupResponseMapper.toRuntimeBackupState(
            new RuntimeBackupState(checkpointState, new BackupRangesResponse()));

    // then
    assertThat(state.getCheckpointStates().getFirst().getCheckpointType())
        .isEqualTo(CheckpointType.MARKER);
    assertThat(state.getBackupStates().getFirst().getCheckpointType()).isNull();
  }

  @Test
  void shouldMapMissingCheckpointTypeAsNull() {
    // given
    final var checkpointState = new CheckpointStateResponse();
    final var untyped = checkpointState(1, 10L, null);
    checkpointState.setCheckpointStates(Set.of(untyped));
    checkpointState.setBackupStates(Set.of(untyped));

    // when
    final var state =
        BackupResponseMapper.toRuntimeBackupState(
            new RuntimeBackupState(checkpointState, new BackupRangesResponse()));

    // then
    assertThat(state.getCheckpointStates().getFirst().getCheckpointType()).isNull();
    assertThat(state.getBackupStates().getFirst().getCheckpointType()).isNull();
  }

  @Test
  void shouldMapTakeHistoryBackupResponse() {
    // when
    final var response =
        BackupResponseMapper.toTakeHistoryBackupResponse(
            new HistoryBackupTaken(8L, List.of("snap-a", "snap-b")));

    // then
    assertThat(response.getBackupId()).isEqualTo(8L);
    assertThat(response.getScheduledSnapshots()).containsExactly("snap-a", "snap-b");
  }

  @Test
  void shouldMapHistoryBackupInfoWithSnapshots() {
    // given
    final var startTime = OffsetDateTime.parse("2024-05-06T07:08:09Z");
    final var state =
        new HistoryBackupState(
            9L,
            io.camunda.service.backup.HistoryBackupStateCode.FAILED,
            "history-failure",
            List.of(
                new HistoryBackupSnapshot("snap-a", "SUCCESS", startTime, List.of()),
                new HistoryBackupSnapshot("snap-b", null, null, List.of("shard failed"))));

    // when
    final var info = BackupResponseMapper.toHistoryBackupInfo(state);

    // then
    assertThat(info.getBackupId()).isEqualTo(9L);
    assertThat(info.getFailureReason()).isEqualTo("history-failure");
    assertThat(info.getDetails()).hasSize(2);
    assertThat(info.getDetails().get(0).getSnapshotName()).isEqualTo("snap-a");
    assertThat(info.getDetails().get(0).getState()).isEqualTo("SUCCESS");
    assertThat(info.getDetails().get(0).getStartTime())
        .isEqualTo(ResponseMapper.formatDate(startTime));
    assertThat(info.getDetails().get(1).getStartTime()).isNull();
    assertThat(info.getDetails().get(1).getFailures()).containsExactly("shard failed");
  }

  @ParameterizedTest
  @EnumSource(io.camunda.service.backup.HistoryBackupStateCode.class)
  void shouldMapEveryHistoryBackupStateCode(
      final io.camunda.service.backup.HistoryBackupStateCode code) {
    // given
    final var state = new HistoryBackupState(1L, code, null, List.of());

    // when
    final var info = BackupResponseMapper.toHistoryBackupInfo(state);

    // then
    assertThat(info.getState().name()).isEqualTo(code.name());
  }

  @Test
  void shouldMapHistoryBackupInfoList() {
    // given
    final var first =
        new HistoryBackupState(
            1L, io.camunda.service.backup.HistoryBackupStateCode.COMPLETED, null, List.of());
    final var second =
        new HistoryBackupState(
            2L, io.camunda.service.backup.HistoryBackupStateCode.INCOMPLETE, null, List.of());

    // when
    final var infos = BackupResponseMapper.toHistoryBackupInfoList(List.of(first, second));

    // then
    assertThat(infos).extracting(info -> info.getBackupId()).containsExactly(1L, 2L);
  }

  @ParameterizedTest
  @EnumSource(TakeOutcome.class)
  void shouldMapClusterRuntimeTakeOutcomes(final TakeOutcome outcome) {
    // given
    final var taken =
        new ClusterRuntimeBackupTaken(
            List.of(new PhysicalTenantRuntimeBackupTaken("tenant-a", outcome, 12L, "because")),
            null);

    // when
    final var response = BackupResponseMapper.toClusterTakeRuntimeBackupResponse(taken);

    // then
    assertThat(response.getPhysicalTenants()).hasSize(1);
    final var result = response.getPhysicalTenants().getFirst();
    assertThat(result.getPhysicalTenantId()).isEqualTo("tenant-a");
    assertThat(result.getBackupId()).isEqualTo(12L);
    assertThat(result.getOutcome().name()).isEqualTo(outcome.name());
    assertThat(result.getReason()).isEqualTo("because");
  }

  @Test
  void shouldMapClusterRuntimeBackupInfo() {
    // given
    final var partition = partitionWithStatus(BackupStatusCode.IN_PROGRESS);
    final var tenantStatus =
        new BackupStatus(3L, State.IN_PROGRESS, Optional.of("tenant-failure"), List.of(partition));
    final var backup =
        new ClusterRuntimeBackup(
            3L,
            State.INCOMPLETE,
            "cluster-failure",
            List.of(new PhysicalTenantRuntimeBackup("tenant-a", tenantStatus)));

    // when
    final var info = BackupResponseMapper.toClusterRuntimeBackupInfo(backup);

    // then
    assertThat(info.getBackupId()).isEqualTo(3L);
    assertThat(info.getState()).isEqualTo(StateCode.INCOMPLETE);
    assertThat(info.getFailureReason()).isEqualTo("cluster-failure");
    assertThat(info.getPhysicalTenants()).hasSize(1);
    final var tenant = info.getPhysicalTenants().getFirst();
    assertThat(tenant.getPhysicalTenantId()).isEqualTo("tenant-a");
    assertThat(tenant.getState()).isEqualTo(StateCode.IN_PROGRESS);
    assertThat(tenant.getFailureReason()).isEqualTo("tenant-failure");
    assertThat(tenant.getDetails()).hasSize(1);
  }

  @Test
  void shouldMapClusterRuntimeBackupInfoWithoutTenantFailureReason() {
    // given
    final var tenantStatus = new BackupStatus(3L, State.COMPLETED, Optional.empty(), List.of());
    final var backup =
        new ClusterRuntimeBackup(
            3L,
            State.COMPLETED,
            null,
            List.of(new PhysicalTenantRuntimeBackup("tenant-a", tenantStatus)));

    // when
    final var info = BackupResponseMapper.toClusterRuntimeBackupInfo(backup);

    // then
    assertThat(info.getFailureReason()).isNull();
    assertThat(info.getPhysicalTenants().getFirst().getFailureReason()).isNull();
  }

  @Test
  void shouldMapClusterRuntimeBackupInfoList() {
    // given
    final var first = new ClusterRuntimeBackup(1L, State.COMPLETED, null, List.of());
    final var second = new ClusterRuntimeBackup(2L, State.FAILED, "boom", List.of());

    // when
    final var infos = BackupResponseMapper.toClusterRuntimeBackupInfoList(List.of(first, second));

    // then
    assertThat(infos).extracting(info -> info.getBackupId()).containsExactly(1L, 2L);
  }

  @Test
  void shouldMapClusterRuntimeBackupState() {
    // given
    final var checkpointState = new CheckpointStateResponse();
    checkpointState.setCheckpointStates(
        Set.of(
            checkpointState(
                1,
                10L,
                io.camunda.zeebe.protocol.record.value.management.CheckpointType
                    .SCHEDULED_BACKUP)));
    final var states =
        new ClusterRuntimeBackupStates(
            List.of(
                new PhysicalTenantRuntimeBackupState(
                    "tenant-a",
                    new RuntimeBackupState(checkpointState, new BackupRangesResponse()))));

    // when
    final var state = BackupResponseMapper.toClusterRuntimeBackupState(states);

    // then
    assertThat(state.getPhysicalTenants()).hasSize(1);
    final var tenant = state.getPhysicalTenants().getFirst();
    assertThat(tenant.getPhysicalTenantId()).isEqualTo("tenant-a");
    assertThat(tenant.getState().getCheckpointStates()).hasSize(1);
  }

  @Test
  void shouldMapClusterTakeHistoryBackupResponse() {
    // given
    final var taken =
        new ClusterHistoryBackupTaken(
            4L,
            List.of(
                new PhysicalTenantBackupTaken("tenant-a", List.of("snap-a")),
                new PhysicalTenantBackupTaken("tenant-b", List.of())));

    // when
    final var response = BackupResponseMapper.toClusterTakeHistoryBackupResponse(taken);

    // then
    assertThat(response.getBackupId()).isEqualTo(4L);
    assertThat(response.getPhysicalTenants())
        .extracting(r -> r.getPhysicalTenantId())
        .containsExactly("tenant-a", "tenant-b");
    assertThat(response.getPhysicalTenants().getFirst().getScheduledSnapshots())
        .containsExactly("snap-a");
  }

  @ParameterizedTest
  @EnumSource(TenantBackupStateCode.class)
  void shouldMapClusterHistoryBackupInfoForEveryTenantState(final TenantBackupStateCode code) {
    // given
    final var snapshot =
        new HistoryBackupSnapshot(
            "snap-a", "SUCCESS", OffsetDateTime.parse("2024-05-06T07:08:09Z"), List.of());
    final var backup =
        new ClusterHistoryBackup(
            6L,
            List.of(
                new PhysicalTenantBackupState(
                    "tenant-a", code, "tenant-failure", List.of(snapshot))));

    // when
    final var info = BackupResponseMapper.toClusterHistoryBackupInfo(backup);

    // then
    assertThat(info.getBackupId()).isEqualTo(6L);
    final var tenant = info.getPhysicalTenants().getFirst();
    assertThat(tenant.getPhysicalTenantId()).isEqualTo("tenant-a");
    assertThat(tenant.getState().name()).isEqualTo(code.name());
    assertThat(tenant.getFailureReason()).isEqualTo("tenant-failure");
    assertThat(tenant.getDetails()).extracting(d -> d.getSnapshotName()).containsExactly("snap-a");
  }

  @Test
  void shouldMapClusterHistoryBackupInfoList() {
    // given
    final var first = new ClusterHistoryBackup(1L, List.of());
    final var second = new ClusterHistoryBackup(2L, List.of());

    // when
    final var infos = BackupResponseMapper.toClusterHistoryBackupInfoList(List.of(first, second));

    // then
    assertThat(infos).extracting(info -> info.getBackupId()).containsExactly(1L, 2L);
  }

  private static PartitionBackupStatus partitionWithStatus(final BackupStatusCode code) {
    return new PartitionBackupStatus(
        1,
        code,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        OptionalLong.empty(),
        OptionalLong.empty(),
        OptionalInt.empty(),
        Optional.empty());
  }

  private static PartitionCheckpointState checkpointState(
      final int partitionId,
      final long checkpointId,
      final io.camunda.zeebe.protocol.record.value.management.CheckpointType type) {
    return new PartitionCheckpointState(
        partitionId, checkpointId, type, CHECKPOINT_INSTANT.toEpochMilli(), 200L, 100L);
  }

  private static CheckpointInfo checkpointInfo(
      final long checkpointId,
      final io.camunda.zeebe.protocol.record.value.management.CheckpointType type) {
    return new CheckpointInfo(checkpointId, 100L, 200L, type, CHECKPOINT_INSTANT);
  }

  private static String formatted(final String instant) {
    return ResponseMapper.formatDate(OffsetDateTime.parse(instant));
  }
}
