/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.rest.SnapshotInfoDto;
import io.camunda.optimize.service.BackupService;
import io.camunda.optimize.service.db.reader.BackupReader;
import io.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.exceptions.OptimizeSnapshotRepositoryNotFoundException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import jakarta.ws.rs.NotFoundException;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BackupServiceTest {

  private static MockedStatic<StringUtils> stringUtils;
  @Mock private BackupReader backupReader;
  @InjectMocks private BackupService backupService;

  @BeforeAll
  public static void beforeAll() {
    stringUtils = Mockito.mockStatic(StringUtils.class);
  }

  @AfterAll
  public static void afterAll() {
    stringUtils.close();
  }

  @Test
  public void triggerBackupWithoutSnapshotRepositoryConfig() {
    // given mock no repository in config
    final OptimizeConfigurationException optimizeConfigurationException =
        new OptimizeConfigurationException(
            "Cannot execute backup request because no snapshot repository name found in Optimize configuration.");
    doThrow(optimizeConfigurationException).when(backupReader).validateRepositoryExists();

    // when/then
    final OptimizeConfigurationException thrown =
        assertThrows(OptimizeConfigurationException.class, () -> backupService.triggerBackup(123L));
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Cannot execute backup request because no snapshot repository name found in Optimize "
                + "configuration.");
  }

  @Test
  public void triggerBackupWithIncorrectSnapshotRepositoryConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(
            new OptimizeSnapshotRepositoryNotFoundException(
                "No repository with name [does_not_exist] could be found."))
        .when(backupReader)
        .validateRepositoryExists();

    // when/then
    final OptimizeRuntimeException thrown =
        assertThrows(OptimizeRuntimeException.class, () -> backupService.triggerBackup(123L));
    assertThat(thrown.getMessage())
        .isEqualTo("No repository with name [does_not_exist] could be found.");
  }

  @Test
  public void triggerBackupWithDuplicateBackupId() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    // Mock existence of other backup with the same ID
    final SnapshotInfoDto snapshotInfoDtoMock = mock(SnapshotInfoDto.class);
    when(snapshotInfoDtoMock.getSnapshotName())
        .thenReturn("existingSnapshotName/Xtll5DxHQ56j6rMz8nFDmQ");
    doReturn(List.of(snapshotInfoDtoMock))
        .when(backupReader)
        .getOptimizeSnapshotsForBackupId(any());

    // when
    final OptimizeConflictException thrown =
        assertThrows(OptimizeConflictException.class, () -> backupService.triggerBackup(123L));
    assertThat(thrown.getMessage())
        .isEqualTo(
            "A backup with ID [123] already exists. Found snapshots: "
                + "[existingSnapshotName/Xtll5DxHQ56j6rMz8nFDmQ]");
  }

  @Test
  public void getSpecificBackupStateWithoutSnapshotRepositoryConfig() {
    // given mock no repository in config
    final OptimizeConfigurationException optimizeConfigurationException =
        new OptimizeConfigurationException(
            "Cannot execute backup request because no snapshot repository name found in Optimize configuration.");
    doThrow(optimizeConfigurationException).when(backupReader).validateRepositoryExists();

    // when/then
    final OptimizeConfigurationException thrown =
        assertThrows(
            OptimizeConfigurationException.class, () -> backupService.getSingleBackupInfo(123L));
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Cannot execute backup request because no snapshot repository name found in Optimize "
                + "configuration.");
  }

  @Test
  public void getSpecificBackupStateWithIncorrectSnapshotRepositoryConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(
            new OptimizeSnapshotRepositoryNotFoundException(
                "No repository with name [does_not_exist] could be found."))
        .when(backupReader)
        .validateRepositoryExists();

    // when/then
    final OptimizeRuntimeException thrown =
        assertThrows(OptimizeRuntimeException.class, () -> backupService.getSingleBackupInfo(123L));
    assertThat(thrown.getMessage())
        .isEqualTo("No repository with name [does_not_exist] could be found.");
  }

  @Test
  public void getSpecificBackupStateNonExistentBackupId() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doNothing().when(backupReader).validateRepositoryExists();
    when(backupReader.getOptimizeSnapshotsForBackupId(anyLong()))
        .thenReturn(Collections.emptyList());

    // when/then
    final NotFoundException thrown =
        assertThrows(NotFoundException.class, () -> backupService.getSingleBackupInfo(123L));
    assertThat(thrown.getMessage()).isEqualTo("No Optimize backup with ID [123] could be found.");
  }

  @Test
  public void getAllBackupStateWithoutSnapshotRepositoryConfig() {
    // given mock no repository in config
    final OptimizeConfigurationException optimizeConfigurationException =
        new OptimizeConfigurationException(
            "Cannot execute backup request because no snapshot repository name found in Optimize configuration.");
    doThrow(optimizeConfigurationException).when(backupReader).validateRepositoryExists();

    // when/then
    final OptimizeConfigurationException thrown =
        assertThrows(OptimizeConfigurationException.class, () -> backupService.getAllBackupInfo());
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Cannot execute backup request because no snapshot repository name found in Optimize "
                + "configuration.");
  }

  @Test
  public void getAllBackupStateWithIncorrectSnapshotRepositoryConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(
            new OptimizeSnapshotRepositoryNotFoundException(
                "No repository with name [does_not_exist] could be found."))
        .when(backupReader)
        .validateRepositoryExists();

    // when/then
    final OptimizeRuntimeException thrown =
        assertThrows(OptimizeRuntimeException.class, () -> backupService.getAllBackupInfo());
    assertThat(thrown.getMessage())
        .isEqualTo("No repository with name [does_not_exist] could be found.");
  }

  @Test
  public void getAllBackupStateNonExistentBackupId() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doNothing().when(backupReader).validateRepositoryExists();
    when(backupReader.getAllOptimizeSnapshotsByBackupId()).thenReturn(Collections.emptyMap());

    // when/then
    assertThat(backupService.getAllBackupInfo()).isEmpty();
  }

  @Test
  public void deleteBackupWithoutSnapshotRepositoryConfig() {
    // given mock no repository in config
    final OptimizeConfigurationException optimizeConfigurationException =
        new OptimizeConfigurationException(
            "Cannot execute backup request because no snapshot repository name found in Optimize configuration.");
    doThrow(optimizeConfigurationException).when(backupReader).validateRepositoryExists();

    // when/then
    final OptimizeConfigurationException thrown =
        assertThrows(OptimizeConfigurationException.class, () -> backupService.deleteBackup(123L));
    assertThat(thrown.getMessage())
        .isEqualTo(
            "Cannot execute backup request because no snapshot repository name found in Optimize "
                + "configuration.");
  }

  @Test
  public void deleteBackupWithIncorrectSnapshotRepositoryConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(
            new OptimizeSnapshotRepositoryNotFoundException(
                "No repository with name [does_not_exist] could be found."))
        .when(backupReader)
        .validateRepositoryExists();

    // when/then
    final OptimizeRuntimeException thrown =
        assertThrows(OptimizeRuntimeException.class, () -> backupService.deleteBackup(123L));
    assertThat(thrown.getMessage())
        .isEqualTo("No repository with name [does_not_exist] could be found.");
  }
}
