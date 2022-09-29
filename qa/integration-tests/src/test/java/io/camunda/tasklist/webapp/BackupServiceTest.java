/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.property.BackupProperties;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.es.backup.BackupManager;
import io.camunda.tasklist.webapp.management.BackupService;
import io.camunda.tasklist.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.tasklist.webapp.rest.exception.InvalidRequestException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.action.admin.cluster.snapshots.get.GetSnapshotsResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.SnapshotClient;
import org.elasticsearch.snapshots.SnapshotId;
import org.elasticsearch.snapshots.SnapshotInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestConfig.class})
@ActiveProfiles({"test", "backend-test"})
public class BackupServiceTest {

  @SpyBean private BackupManager backupManager;

  @Mock private SnapshotClient snapshotClient;

  @MockBean
  @Qualifier("esClient")
  private RestHighLevelClient esClient;

  @MockBean private TasklistProperties tasklistProperties;

  @InjectMocks private BackupService backupService;

  @Test
  public void shouldFailOnEmptyBackupId() {
    final Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto());
            });
    final String expectedMessage = "BackupId must be provided";
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailOnWrongBackupId() {
    final String expectedMessage =
        "BackupId must not contain any uppercase letters or any of [ , \", *, \\, <, |, ,, >, /, ?].";

    Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("UPPERCASEID"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith "));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith\""));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith*"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith\\"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith<"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith|"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith,"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith>"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith/"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith?"));
            });
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void shouldFailOnAbsentRepositoryName() {
    when(tasklistProperties.getBackup()).thenReturn(new BackupProperties());
    final Exception exception =
        assertThrows(
            TasklistRuntimeException.class,
            () -> {
              backupManager.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
            });
    final String expectedMessage =
        "Cannot trigger backup because no Elasticsearch snapshot repository name found in Tasklist configuration.";
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailOnNonExistingRepository() throws IOException {
    final String repoName = "repoName";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    final Exception exception =
        assertThrows(
            TasklistRuntimeException.class,
            () -> {
              backupManager.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
            });

    final String expectedMessage =
        String.format(
            "Cannot trigger backup because no repository with name [%s] could be found.", repoName);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailOnBackupIdAlreadyExists() throws IOException {
    final String repoName = "repoName";
    final String backupid = "backupid";
    when(tasklistProperties.getBackup())
        .thenReturn(new BackupProperties().setRepositoryName(repoName));
    final SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", "uuid"));
    final List<SnapshotInfo> snapshotInfos = Arrays.asList(new SnapshotInfo[] {snapshotInfo});
    when(snapshotClient.get(any(), any()))
        .thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    final Exception exception =
        assertThrows(
            InvalidRequestException.class,
            () -> {
              backupManager.takeBackup(new TakeBackupRequestDto().setBackupId(backupid));
            });
    final String expectedMessage =
        String.format("A backup with ID [%s] already exists. Found snapshots:", backupid);
    final String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(2)).snapshot();
  }
}

@Configuration
@ComponentScan(
    basePackages = {"io.camunda.tasklist.schema.indices", "io.camunda.tasklist.schema.templates"})
@Profile("backend-test")
class TestConfig {}
