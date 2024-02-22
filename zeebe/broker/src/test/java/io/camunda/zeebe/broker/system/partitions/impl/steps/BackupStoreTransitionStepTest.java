/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.DataCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg;
import io.camunda.zeebe.broker.system.configuration.backup.BackupStoreCfg.BackupStoreType;
import io.camunda.zeebe.broker.system.configuration.backup.S3BackupStoreConfig;
import io.camunda.zeebe.broker.system.partitions.TestPartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldCloseService;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldDoNothing;
import io.camunda.zeebe.broker.system.partitions.impl.steps.PartitionTransitionTestArgumentProviders.TransitionsThatShouldInstallService;
import io.camunda.zeebe.scheduler.testing.TestConcurrencyControl;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    final var backupCfg = new BackupStoreCfg();
    backupCfg.setStore(type);
    backupCfg.setS3(s3Config);
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
