/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.es.backup.es;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.backup.Metadata;
import io.camunda.tasklist.webapp.management.dto.BackupStateDto;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Qualifier;

@ExtendWith(MockitoExtension.class)
class BackupManagerElasticsearchTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  final Metadata metadata =
      new Metadata().setBackupId(2L).setPartCount(3).setPartNo(1).setVersion("8.6.1");

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private RestHighLevelClient searchClient;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private TasklistProperties tasklistProperties;

  @InjectMocks private BackupManagerElasticSearch backupManager;

  @Mock(strictness = Strictness.LENIENT)
  @Qualifier("tasklistObjectMapper")
  private ObjectMapper objectMapper;

  @BeforeEach
  public void setUp() {
    when(objectMapper.convertValue(any(), eq(Metadata.class)))
        .thenAnswer(invocation -> MAPPER.convertValue(invocation.getArgument(0), Metadata.class));
    when(tasklistProperties.getBackup().getRepositoryName()).thenReturn("test-repo");
  }

  @ParameterizedTest
  @EnumSource(value = SnapshotState.class)
  void shouldReturnPartialDataWhenVerboseIsFalse(final SnapshotState state) throws IOException {
    // given
    final var snapshots = new ArrayList<SnapshotInfo>(metadata.getPartCount());
    for (int i = 1; i <= metadata.getPartCount(); i++) {
      final var copy = new Metadata(metadata);
      copy.setPartNo(i);
      final var snapshotInfo = mock(SnapshotInfo.class, Answers.RETURNS_DEEP_STUBS);
      snapshots.add(snapshotInfo);
      when(snapshotInfo.snapshotId().getName()).thenReturn(copy.buildSnapshotName());
      when(snapshotInfo.state()).thenReturn(state);
    }

    final var snapshotResponse = mock(GetSnapshotsResponse.class, Answers.RETURNS_DEEP_STUBS);
    when(snapshotResponse.getSnapshots()).thenReturn(snapshots);
    when(searchClient.snapshot().get(any(), any())).thenReturn(snapshotResponse);

    // when
    final var backupResponse = backupManager.getBackups(false, null);

    // then
    assertThat(backupResponse)
        .singleElement()
        .satisfies(
            snap -> {
              final var expectedState =
                  switch (state) {
                    case SUCCESS -> BackupStateDto.COMPLETED;
                    case IN_PROGRESS -> BackupStateDto.IN_PROGRESS;
                    case PARTIAL, FAILED -> BackupStateDto.FAILED;
                    case INCOMPATIBLE -> BackupStateDto.INCOMPATIBLE;
                    default -> null;
                  };
              assertThat(snap.getState()).isEqualTo(expectedState);
              assertThat(snap.getBackupId()).isEqualTo(metadata.getBackupId());
              assertThat(snap.getDetails())
                  .hasSize(metadata.getPartCount())
                  .zipSatisfy(
                      snapshots,
                      (detail, snapshotInfo) -> {
                        assertThat(detail.getState()).isEqualTo(state.toString());
                        assertThat(detail.getSnapshotName())
                            .isEqualTo(snapshotInfo.snapshotId().getName());
                      });
            });
  }

  @Test
  public void shouldForwardPatternToES() throws IOException {
    // given
    final var snapshotResponse = mock(GetSnapshotsResponse.class, Answers.RETURNS_DEEP_STUBS);
    when(snapshotResponse.getSnapshots()).thenReturn(new ArrayList<>());
    when(searchClient.snapshot().get(any(), any())).thenReturn(snapshotResponse);

    // when
    final var backupResponse = backupManager.getBackups(false, "2025*");

    // then
    verify(searchClient.snapshot())
        .get(argThat(r -> Arrays.asList(r.snapshots()).contains("camunda_tasklist_2025*")), any());
  }
}
