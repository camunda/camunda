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
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeSnapshotRepositoryNotFoundException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
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
  public void triggerBackupWithoutSnapshotRepositoryConfig() {
    // given mock no repository in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(true);
    // when/then
    final OptimizeConfigurationException thrown = assertThrows(
      OptimizeConfigurationException.class,
      () -> backupService.triggerBackup(123)
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "Cannot execute backup request because no Elasticsearch snapshot repository name found in Optimize " +
        "configuration.");
  }

  @Test
  public void triggerBackupWithIncorrectSnapshotRepositoryConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(new OptimizeSnapshotRepositoryNotFoundException("No repository with name [does_not_exist] could be found."))
      .when(backupReader).validateRepositoryExistsOrFail();

    // when/then
    final OptimizeRuntimeException thrown = assertThrows(
      OptimizeRuntimeException.class,
      () -> backupService.triggerBackup(123)
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
      "A backup with ID [123] already exists. Found snapshots: [existingSnapshotName/Xtll5DxHQ56j6rMz8nFDmQ]"))
      .when(backupReader).validateNoDuplicateBackupId(any());

    // when
    final OptimizeConflictException thrown = assertThrows(
      OptimizeConflictException.class,
      () -> backupService.triggerBackup(123)
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "A backup with ID [123] already exists. Found snapshots: " +
        "[existingSnapshotName/Xtll5DxHQ56j6rMz8nFDmQ]");
  }

  @Test
  public void getSpecificBackupStateWithoutSnapshotRepositoryConfig() {
    // given mock no repository in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(true);

    // when/then
    final OptimizeConfigurationException thrown = assertThrows(
      OptimizeConfigurationException.class,
      () -> backupService.getSingleBackupInfo(123)
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "Cannot execute backup request because no Elasticsearch snapshot repository name found in Optimize " +
        "configuration.");
  }

  @Test
  public void getSpecificBackupStateWithIncorrectSnapshotRepositoryConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(new OptimizeSnapshotRepositoryNotFoundException("No repository with name [does_not_exist] could be found."))
      .when(backupReader).validateRepositoryExistsOrFail();

    // when/then
    final OptimizeRuntimeException thrown = assertThrows(
      OptimizeRuntimeException.class,
      () -> backupService.getSingleBackupInfo(123)
    );
    assertThat(thrown.getMessage()).isEqualTo("No repository with name [does_not_exist] could be found.");
  }

  @Test
  public void getSpecificBackupStateNonExistentBackupId() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doNothing().when(backupReader).validateRepositoryExistsOrFail();
    when(backupReader.getOptimizeSnapshotsForBackupId(any())).thenReturn(Collections.emptyList());

    // when/then
    final NotFoundException thrown = assertThrows(
      NotFoundException.class,
      () -> backupService.getSingleBackupInfo(123)
    );
    assertThat(thrown.getMessage()).isEqualTo("No Optimize backup with ID [123] could be found.");
  }

  @Test
  public void getAllBackupStateWithoutSnapshotRepositoryConfig() {
    // given mock no repository in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(true);

    // when/then
    final OptimizeConfigurationException thrown = assertThrows(
      OptimizeConfigurationException.class,
      () -> backupService.getAllBackupInfo()
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "Cannot execute backup request because no Elasticsearch snapshot repository name found in Optimize " +
        "configuration.");
  }

  @Test
  public void getAllBackupStateWithIncorrectSnapshotRepositoryConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(new OptimizeSnapshotRepositoryNotFoundException("No repository with name [does_not_exist] could be found."))
      .when(backupReader).validateRepositoryExistsOrFail();

    // when/then
    final OptimizeRuntimeException thrown = assertThrows(
      OptimizeRuntimeException.class,
      () -> backupService.getAllBackupInfo()
    );
    assertThat(thrown.getMessage()).isEqualTo("No repository with name [does_not_exist] could be found.");
  }

  @Test
  public void getAllBackupStateNonExistentBackupId() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doNothing().when(backupReader).validateRepositoryExistsOrFail();
    when(backupReader.getAllOptimizeSnapshotsByBackupId()).thenReturn(Collections.emptyMap());

    // when/then
    assertThat(backupService.getAllBackupInfo()).isEmpty();
  }

  @Test
  public void deleteBackupWithoutSnapshotRepositoryConfig() {
    // given mock no repository in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(true);

    // when/then
    final OptimizeConfigurationException thrown = assertThrows(
      OptimizeConfigurationException.class,
      () -> backupService.deleteBackup(123)
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "Cannot execute backup request because no Elasticsearch snapshot repository name found in Optimize " +
        "configuration.");
  }

  @Test
  public void deleteBackupWithIncorrectSnapshotRepositoryConfig() {
    // given
    // Mock existence of repository name field in config
    stringUtils.when(() -> StringUtils.isEmpty(any())).thenReturn(false);
    doThrow(new OptimizeSnapshotRepositoryNotFoundException("No repository with name [does_not_exist] could be found."))
      .when(backupReader).validateRepositoryExistsOrFail();

    // when/then
    final OptimizeRuntimeException thrown = assertThrows(
      OptimizeRuntimeException.class,
      () -> backupService.deleteBackup(123)
    );
    assertThat(thrown.getMessage()).isEqualTo("No repository with name [does_not_exist] could be found.");
  }
}
