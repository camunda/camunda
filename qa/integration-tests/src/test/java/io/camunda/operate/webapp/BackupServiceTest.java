/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp;

import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.BackupProperties;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.es.backup.BackupManager;
import io.camunda.operate.webapp.management.BackupService;
import io.camunda.operate.webapp.management.dto.TakeBackupRequestDto;
import io.camunda.operate.webapp.rest.exception.InvalidRequestException;
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

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {TestConfig.class})
@ActiveProfiles({"test", "backend-test"})
public class BackupServiceTest {

  @SpyBean
  private BackupManager backupManager;

  @Mock
  private SnapshotClient snapshotClient;

  @MockBean
  @Qualifier("esClient")
  private RestHighLevelClient esClient;

  @MockBean
  private OperateProperties operateProperties;

  @InjectMocks
  private BackupService backupService;

  @Test
  public void shouldFailOnEmptyBackupId() {
    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto());
    });
    String expectedMessage = "BackupId must be provided";
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailOnWrongBackupId() {
    String expectedMessage = "BackupId must not contain any uppercase letters or any of [ , \", *, \\, <, |, ,, >, /, ?].";

    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("UPPERCASEID"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith "));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith\""));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith*"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith\\"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith<"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith|"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith,"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith>"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith/"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));

    exception = assertThrows(InvalidRequestException.class, () -> {
      backupService.takeBackup(new TakeBackupRequestDto().setBackupId("backupIdWith?"));
    });
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void shouldFailOnAbsentRepositoryName() {
    when(operateProperties.getBackup()).thenReturn(new BackupProperties());
    Exception exception = assertThrows(OperateRuntimeException.class, () -> {
      backupManager.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
    });
    String expectedMessage = "Cannot trigger backup because no Elasticsearch snapshot repository name found in Operate configuration.";
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
  }

  @Test
  public void shouldFailOnNonExistingRepository() throws IOException {
    String repoName = "repoName";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    ElasticsearchStatusException elsEx = mock(ElasticsearchStatusException.class);
    when(elsEx.getDetailedMessage()).thenReturn("type=repository_missing_exception");
    when(snapshotClient.getRepository(any(), any())).thenThrow(elsEx);
    when(esClient.snapshot()).thenReturn(snapshotClient);
    Exception exception = assertThrows(OperateRuntimeException.class, () -> {
      backupManager.takeBackup(new TakeBackupRequestDto().setBackupId("backupid"));
    });

    String expectedMessage = String.format("Cannot trigger backup because no repository with name [%s] could be found.",
        repoName);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(1)).snapshot();
  }

  @Test
  public void shouldFailOnBackupIdAlreadyExists() throws IOException {
    String repoName = "repoName";
    String backupid = "backupid";
    when(operateProperties.getBackup()).thenReturn(new BackupProperties().setRepositoryName(repoName));
    SnapshotInfo snapshotInfo = mock(SnapshotInfo.class);
    when(snapshotInfo.snapshotId()).thenReturn(new SnapshotId("snapshotName", "uuid"));
    List<SnapshotInfo> snapshotInfos = Arrays.asList(new SnapshotInfo[] { snapshotInfo });
    when(snapshotClient.get(any(), any())).thenReturn(new GetSnapshotsResponse(snapshotInfos, null, null, 1, 1));
    when(esClient.snapshot()).thenReturn(snapshotClient);

    Exception exception = assertThrows(InvalidRequestException.class, () -> {
      backupManager.takeBackup(new TakeBackupRequestDto().setBackupId(backupid));
    });
    String expectedMessage = String.format("A backup with ID [%s] already exists. Found snapshots:", backupid);
    String actualMessage = exception.getMessage();
    assertTrue(actualMessage.contains(expectedMessage));
    verify(esClient, times(2)).snapshot();
  }
}

@Configuration
@ComponentScan(basePackages = {"io.camunda.operate.schema.indices", "io.camunda.operate.schema.templates"})
@Profile("backend-test")
class TestConfig {

}

