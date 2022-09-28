/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.backup;

import com.github.dockerjava.api.exception.ConflictException;
import io.micrometer.core.instrument.util.StringUtils;
import lombok.SneakyThrows;
import org.camunda.optimize.service.BackupService;
import org.camunda.optimize.service.es.reader.BackupReader;
import org.camunda.optimize.service.es.reader.BackupWriter;
import org.camunda.optimize.service.exceptions.OptimizeConfigurationException;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeSnapshotRepositoryNotFoundException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

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
      "Cannot trigger backup because no Elasticsearch snapshot repository name found in Optimize " +
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
    // Mock existence of other backup with the same ID
    doThrow(new ConflictException(
      "A backup with ID [alreadyExists] already exists. Found snapshots: [existingSnapshotName/Xtll5DxHQ56j6rMz8nFDmQ]"))
      .when(backupReader).validateNoDuplicateBackupId(any());

    // when
    final ConflictException thrown = assertThrows(
      ConflictException.class,
      () -> backupService.triggerBackup("alreadyExists")
    );
    assertThat(thrown.getMessage()).isEqualTo(
      "Status 409: A backup with ID [alreadyExists] already exists. Found snapshots: " +
        "[existingSnapshotName/Xtll5DxHQ56j6rMz8nFDmQ]");
  }
}
