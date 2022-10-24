/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.backup;

import io.micrometer.core.instrument.util.StringUtils;
import lombok.SneakyThrows;
import org.camunda.optimize.service.BackupService;
import org.camunda.optimize.service.es.reader.BackupReader;
import org.camunda.optimize.service.es.reader.BackupWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeSnapshotRepositoryNotFoundException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.elasticsearch.snapshots.Snapshot;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.elasticsearch.snapshots.SnapshotState;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BackupServiceTest {
  @Mock
  private BackupReader backupReader;
  @Mock
  private BackupWriter backupWriter;
  @Mock
  private ConfigurationService configurationService;
  @InjectMocks
  private BackupService backupService;
  private static MockedStatic<StringUtils> stringUtils;

  @BeforeAll
  public static void beforeAll() {
    stringUtils = Mockito.mockStatic(StringUtils.class);
  }

  @AfterAll
  public static void afterAll() {
    stringUtils.close();
  }

  @Test
  public void triggerBackupWithoutSnapshotConfig() {
    // when/then
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(true);
    final OptimizeConfigurationException thrown = assertThrows(
      OptimizeConfigurationException.class,
      () -> backupService.triggerBackup("backupid")
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "Cannot execute backup request because no Elasticsearch snapshot repository name found in Optimize " +
        "configuration.");
  }

  @Test
  public void triggerBackupWithIncorrectSnapshotConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(new OptimizeSnapshotRepositoryNotFoundException("No repository with name [does_not_exist] could be found."))
      .when(backupReader).validateRepositoryExistsOrFail();

    // when/then
    final OptimizeRuntimeException thrown = assertThrows(
      OptimizeRuntimeException.class,
      () -> backupService.triggerBackup("backupid")
    );
    assertThat(thrown.getMessage()).isEqualTo("No repository with name [does_not_exist] could be found.");
  }

  @SneakyThrows
  @Test
  public void triggerBackupWithDuplicateBackupId() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doNothing().when(backupReader).validateRepositoryExistsOrFail();
    // Mock existence of other backup with the same ID
    doThrow(new OptimizeConflictException(
      "A backup with ID [alreadyExists] already exists. Found snapshots: [existingSnapshotName/Xtll5DxHQ56j6rMz8nFDmQ]"))
      .when(backupReader).validateNoDuplicateBackupId(any());

    // when
    final OptimizeConflictException thrown = assertThrows(
      OptimizeConflictException.class,
      () -> backupService.triggerBackup("alreadyExists")
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "A backup with ID [alreadyExists] already exists. Found snapshots: " +
        "[existingSnapshotName/Xtll5DxHQ56j6rMz8nFDmQ]");
  }

  @Test
  public void getBackupStateWithoutSnapshotConfig() {
    // given
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(true);

    // when/then
    final OptimizeConfigurationException thrown = assertThrows(
      OptimizeConfigurationException.class,
      () -> backupService.getBackupState("backupid")
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "Cannot execute backup request because no Elasticsearch snapshot repository name found in Optimize " +
        "configuration.");
  }

  @Test
  public void useBackupApiWithIncorrectSnapshotConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(new OptimizeSnapshotRepositoryNotFoundException("No repository with name [does_not_exist] could be found."))
      .when(backupReader).validateRepositoryExistsOrFail();

    // when/then
    final OptimizeRuntimeException thrown = assertThrows(
      OptimizeRuntimeException.class,
      () -> backupService.getBackupState("backupid")
    );
    assertThat(thrown.getMessage()).isEqualTo("No repository with name [does_not_exist] could be found.");
  }

  @Test
  public void getBackupStateNonExistentBackupId() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doNothing().when(backupReader).validateRepositoryExistsOrFail();
    when(backupReader.getAllOptimizeSnapshots(any())).thenReturn(Collections.emptyList());

    // when/then
    final NotFoundException thrown = assertThrows(
      NotFoundException.class,
      () -> backupService.getBackupState("backupid")
    );
    assertThat(thrown.getMessage()).isEqualTo("No Optimize backup with ID [backupid] could be found.");
  }

  @SneakyThrows
  @Test
  public void getBackupStateTooManySnapshots() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doNothing().when(backupReader).validateRepositoryExistsOrFail();
    when(backupReader.getAllOptimizeSnapshots(any())).thenReturn(List.of(
      new SnapshotInfo(
        new Snapshot("repoName", new SnapshotId("snapshotid1", "123")),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        SnapshotState.SUCCESS
      ),
      new SnapshotInfo(
        new Snapshot("repoName", new SnapshotId("snapshotid2", "456")),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        SnapshotState.SUCCESS
      ),
      new SnapshotInfo(
        new Snapshot("repoName", new SnapshotId("snapshotid3", "789")),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        SnapshotState.SUCCESS
      )
    ));

    // when/then
    final OptimizeRuntimeException thrown = assertThrows(
      OptimizeRuntimeException.class,
      () -> backupService.getBackupState("backupid")
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "Unable to determine backup state because unexpected number of snapshots exist for backupID [backupid]. Expected [2] " +
        "snapshots but found [3]. Found snapshots: [repoName:snapshotid1/123, repoName:snapshotid2/456, " +
        "repoName:snapshotid3/789].");
  }
}
