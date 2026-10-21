/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.azure.AzureBackupStoreException;
import io.camunda.zeebe.backup.gcs.GcsBackupStoreException.ConfigurationException;
import io.camunda.zeebe.broker.eventplane.PublisherService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.EventPlaneCfg;
import io.camunda.zeebe.broker.system.configuration.backup.AzureBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig;
import io.camunda.zeebe.broker.system.configuration.backup.GcsBackupStoreConfig.GcsBackupStoreAuth;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.restore.EventPlaneLogBackupStore;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackupStoreTransitionStepTest {

  private static final TestConcurrencyControl TEST_CONCURRENCY_CONTROL =
      new TestConcurrencyControl();

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  BrokerCfg brokerCfg;

  @Mock DataCfg dataCfg;
  @Mock BackupStore backupStorePreviousRole;

  private final TestPartitionTransitionContext transitionContext =
      new TestPartitionTransitionContext();
  private BackupStoreTransitionStep step;

  @BeforeEach
  void setup() {
    transitionContext.setConcurrencyControl(TEST_CONCURRENCY_CONTROL);
    transitionContext.setBrokerCfg(brokerCfg);

    step = new BackupStoreTransitionStep();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldCloseService.class)
  void shouldCloseExistingService(final Role currentRole, final Role targetRole) {
    // given
    setUpCurrentRole(currentRole);

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();

    // then
    assertThat(transitionContext.getBackupStore())
        .describedAs("BackupStore must be removed from the context")
        .isNull();
    verify(backupStorePreviousRole).closeAsync();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
  void shouldReInstallServiceWhenStoreTypeAvailable(final Role currentRole, final Role targetRole) {
    // given
    setUpCurrentRole(currentRole);
    configureStore(BackupStoreType.S3);

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getBackupStore())
        .describedAs("New BackupStore must be installed")
        .isNotNull()
        .isNotEqualTo(backupStorePreviousRole);
  }

  @Test
  // This test fails if you have AWS configured locally (eg:- ~/.aws/)
  void shouldFailToInstallWhenS3ConfigurationsAreNotAvailable() {
    // given
    setUpCurrentRole(null);
    configureStore(BackupStoreType.S3, new S3BackupStoreConfig());
    final var targetRole = Role.LEADER;

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();
    final var transitionFuture = step.transitionTo(transitionContext, 1, targetRole);

    // then
    assertThat(transitionFuture)
        .describedAs("Expected to fail installation when s3 configuration is not complete.")
        .failsWithin(Duration.ofMillis(500))
        .withThrowableOfType(ExecutionException.class);
  }

  @Test
  void shouldFailConnectionCheckWhenRequiredAndS3NotReachable() {
    // given
    setUpCurrentRole(null);
    configureStore(BackupStoreType.S3);
    final var s3Config = new S3BackupStoreConfig();
    s3Config.setBucketName("non-existent-bucket");
    s3Config.setRegion("us-east-1");
    s3Config.setEndpoint("http://localhost:1");
    s3Config.setAccessKey("key");
    s3Config.setSecretKey("secret");
    configureRequiredStore(BackupStoreType.S3, s3Config);
    final var targetRole = Role.LEADER;

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();
    final var transitionFuture = step.transitionTo(transitionContext, 1, targetRole);

    // then
    assertThat(transitionFuture)
        .describedAs(
            "Expected to fail installation when backup is required and S3 is not reachable.")
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class);
    assertThat(transitionContext.getBackupStore()).isNull();
  }

  @Test
  void shouldFailConnectionCheckWhenRequiredAndFilesystemBasePathNotWritable() {
    // given
    setUpCurrentRole(null);
    final var filesystemConfig =
        new io.camunda.zeebe.broker.system.configuration.backup.FilesystemBackupStoreConfig();
    filesystemConfig.setBasePath("/proc/non-existent-path-that-cannot-be-created");
    configureRequiredStore(filesystemConfig);
    final var targetRole = Role.LEADER;

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();
    final var transitionFuture = step.transitionTo(transitionContext, 1, targetRole);

    // then
    assertThat(transitionFuture)
        .describedAs(
            "Expected to fail installation when backup is required and filesystem path is not writable.")
        .failsWithin(Duration.ofMillis(500))
        .withThrowableOfType(ExecutionException.class);
    assertThat(transitionContext.getBackupStore()).isNull();
  }

  @Test
  void shouldFailConnectionCheckWhenRequiredAndAzureNotReachable() {
    // given
    setUpCurrentRole(null);
    final var azureConfig = new AzureBackupStoreConfig();
    azureConfig.setEndpoint("http://localhost:1");
    azureConfig.setAccountName("devstoreaccount1");
    azureConfig.setAccountKey(
        "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==");
    azureConfig.setBasePath("non-existent-container");
    configureRequiredStore(BackupStoreType.AZURE, azureConfig);
    final var targetRole = Role.LEADER;

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();
    final var transitionFuture = step.transitionTo(transitionContext, 1, targetRole);

    // then
    assertThat(transitionFuture)
        .describedAs(
            "Expected to fail installation when backup is required and Azure is not reachable.")
        .failsWithin(Duration.ofSeconds(11))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(AzureBackupStoreException.ConfigurationException.class);
    assertThat(transitionContext.getBackupStore()).isNull();
  }

  @Test
  void shouldFailConnectionCheckWhenRequiredAndGcsNotReachable() {
    // given
    setUpCurrentRole(null);
    final var gcsConfig = new GcsBackupStoreConfig();
    gcsConfig.setBucketName("non-existent-bucket");
    gcsConfig.setHost("http://localhost:1");
    gcsConfig.setAuth(GcsBackupStoreAuth.NONE);
    configureRequiredStore(BackupStoreType.GCS, gcsConfig);
    final var targetRole = Role.LEADER;

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();
    final var transitionFuture = step.transitionTo(transitionContext, 1, targetRole);

    // then
    assertThat(transitionFuture)
        .describedAs(
            "Expected to fail installation when backup is required and GCS is not reachable.")
        .failsWithin(Duration.ofSeconds(11))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(ConfigurationException.class);
    assertThat(transitionContext.getBackupStore()).isNull();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldInstallService.class)
  void shouldNotInstallServiceWhenStoreTypeIsNone(final Role currentRole, final Role targetRole) {
    // given
    setUpCurrentRole(currentRole);
    configureStore(BackupStoreType.NONE);

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getBackupStore()).isNull();
  }

  @ParameterizedTest
  @ArgumentsSource(TransitionsThatShouldDoNothing.class)
  void shouldNotReInstallService(final Role currentRole, final Role targetRole) {
    // given
    setUpCurrentRole(currentRole);
    final var existingBackupStore = transitionContext.getBackupStore();

    // when
    transitionTo(targetRole);

    // then
    assertThat(transitionContext.getBackupStore())
        .describedAs("Existing backup store must not be removed from the context.")
        .isEqualTo(existingBackupStore);
  }

  @Test
  void shouldKeepTheLogInTheEventPlaneWhenBackupLogIsOn(@TempDir final Path directory) {
    // given
    setUpCurrentRole(null);
    configureEventPlane(directory, true, true);

    // when
    transitionTo(Role.LEADER);

    // then
    final var installed = transitionContext.getBackupStore();
    assertThat(installed)
        .describedAs("Expected the backup store to keep its backups' log in the Event Plane")
        .isInstanceOf(EventPlaneLogBackupStore.class);
    installed.closeAsync().join();
  }

  @Test
  void shouldCopyTheSegmentsWhenBackupLogIsOff(@TempDir final Path directory) {
    // given
    setUpCurrentRole(null);
    configureEventPlane(directory, true, false);

    // when
    transitionTo(Role.LEADER);

    // then
    final var installed = transitionContext.getBackupStore();
    assertThat(installed).isNotNull().isNotInstanceOf(EventPlaneLogBackupStore.class);
    installed.closeAsync().join();
  }

  @Test
  void shouldCopyTheSegmentsWhenTheEventPlaneIsOff(@TempDir final Path directory) {
    // given
    setUpCurrentRole(null);
    configureEventPlane(directory, false, true);

    // when
    transitionTo(Role.LEADER);

    // then
    final var installed = transitionContext.getBackupStore();
    assertThat(installed).isNotNull().isNotInstanceOf(EventPlaneLogBackupStore.class);
    installed.closeAsync().join();
  }

  @Test
  void shouldFailToInstallWhenTheEventPlaneStoreIsInvalid(@TempDir final Path directory) {
    // given
    setUpCurrentRole(null);
    final var eventPlane = configureEventPlane(directory, true, true);
    eventPlane.getStore().setType("unknown");
    final var targetRole = Role.LEADER;

    // when
    step.prepareTransition(transitionContext, 1, targetRole).join();
    final var transitionFuture = step.transitionTo(transitionContext, 1, targetRole);

    // then
    assertThat(transitionFuture)
        .failsWithin(Duration.ofSeconds(5))
        .withThrowableOfType(ExecutionException.class)
        .withRootCauseInstanceOf(IllegalArgumentException.class);
    assertThat(transitionContext.getBackupStore()).isNull();
  }

  @Test
  void shouldPublishTheHistoryOfThePublisherThatTheContextHolds() {
    // given: read when asked, as the Event Plane step installs the publisher after this step
    final var publishing = BackupStoreTransitionStep.publishing(transitionContext);
    final var firstHistory = UUID.randomUUID();
    final var first = mock(PublisherService.class);
    when(first.publishedHistory()).thenReturn(Optional.of(firstHistory));
    final var secondHistory = UUID.randomUUID();
    final var second = mock(PublisherService.class);
    when(second.publishedHistory()).thenReturn(Optional.of(secondHistory));
    final var notStarted = mock(PublisherService.class);
    when(notStarted.publishedHistory()).thenReturn(Optional.empty());

    // when / then: no publisher
    assertThat(publishing.of(1)).isEmpty();

    // when / then: a publisher that has started a history
    transitionContext.setEventPlanePublisher(first);
    assertThat(publishing.of(1)).hasValue(firstHistory);

    // when / then: the publisher of the next leadership replaces it
    transitionContext.setEventPlanePublisher(second);
    assertThat(publishing.of(1)).hasValue(secondHistory);

    // when / then: a publisher that hasn't started a history yet
    transitionContext.setEventPlanePublisher(notStarted);
    assertThat(publishing.of(1)).isEmpty();

    // when / then: the leadership is gone
    transitionContext.setEventPlanePublisher(first);
    transitionContext.setEventPlanePublisher(null);
    assertThat(publishing.of(1)).isEmpty();
  }

  private EventPlaneCfg configureEventPlane(
      final Path directory, final boolean enabled, final boolean backupLog) {
    final var cfg = new BrokerCfg();
    final var backup = cfg.getData().getBackup();
    backup.setStore(BackupStoreType.FILESYSTEM);
    backup.getFilesystem().setBasePath(directory.resolve("backups").toString());
    final var eventPlane = cfg.getExperimental().getEventPlane();
    eventPlane.setEnabled(enabled);
    eventPlane.setBackupLog(backupLog);
    eventPlane.setClusterId("my-cluster");
    eventPlane.getStore().setPath(directory.resolve("event-plane").toString());
    transitionContext.setBrokerCfg(cfg);
    return eventPlane;
  }

  private void configureStore(final BackupStoreType type) {
    final S3BackupStoreConfig s3Config = new S3BackupStoreConfig();
    s3Config.setBucketName("bucket");
    s3Config.setRegion("region");
    s3Config.setRegion("endpoint");
    s3Config.setAccessKey("user");
    s3Config.setSecretKey("password");
    configureStore(type, s3Config);
  }

  private void configureStore(final BackupStoreType type, final S3BackupStoreConfig s3Config) {
    final var backupCfg = new BackupCfg();
    backupCfg.setStore(type);
    backupCfg.setS3(s3Config);
    when(brokerCfg.getData()).thenReturn(dataCfg);
    when(dataCfg.getBackup()).thenReturn(backupCfg);
  }

  private void configureRequiredStore(
      final BackupStoreType type, final S3BackupStoreConfig s3Config) {
    final var backupCfg = new BackupCfg();
    backupCfg.setStore(type);
    backupCfg.setRequired(true);
    backupCfg.setS3(s3Config);
    when(brokerCfg.getData()).thenReturn(dataCfg);
    when(dataCfg.getBackup()).thenReturn(backupCfg);
  }

  private void configureRequiredStore(
      final BackupStoreType type, final AzureBackupStoreConfig azureConfig) {
    final var backupCfg = new BackupCfg();
    backupCfg.setStore(type);
    backupCfg.setRequired(true);
    backupCfg.setAzure(azureConfig);
    when(brokerCfg.getData()).thenReturn(dataCfg);
    when(dataCfg.getBackup()).thenReturn(backupCfg);
  }

  private void configureRequiredStore(
      final BackupStoreType type, final GcsBackupStoreConfig gcsConfig) {
    final var backupCfg = new BackupCfg();
    backupCfg.setStore(type);
    backupCfg.setRequired(true);
    backupCfg.setGcs(gcsConfig);
    when(brokerCfg.getData()).thenReturn(dataCfg);
    when(dataCfg.getBackup()).thenReturn(backupCfg);
  }

  private void configureRequiredStore(final FilesystemBackupStoreConfig filesystemConfig) {
    final var backupCfg = new BackupCfg();
    backupCfg.setStore(BackupStoreType.FILESYSTEM);
    backupCfg.setRequired(true);
    backupCfg.setFilesystem(filesystemConfig);
    when(brokerCfg.getData()).thenReturn(dataCfg);
    when(dataCfg.getBackup()).thenReturn(backupCfg);
  }

  private void transitionTo(final Role role) {
    step.prepareTransition(transitionContext, 1, role).join();
    step.transitionTo(transitionContext, 1, role).join();
    transitionContext.setCurrentRole(role);
  }

  private void setUpCurrentRole(final Role currentRole) {
    transitionContext.setCurrentRole(currentRole);
    if (currentRole != null && currentRole != Role.INACTIVE) {
      transitionContext.setBackupStore(backupStorePreviousRole);
      lenient()
          .when(backupStorePreviousRole.closeAsync())
          .thenReturn(CompletableFuture.completedFuture(null));
    }
  }
}
