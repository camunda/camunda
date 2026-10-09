/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreParameters;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RestoreRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.TenantRestoreArguments;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class RecoveryServicesTest {

  private static final String PHYSICAL_TENANT_ID = "testtenant";
  private static final String MODE_CHANGE_FORBIDDEN_MESSAGE =
      "Unauthorized to perform any of the operations: "
          + "'RESTORE' on 'BACKUP' or 'UPDATE' on 'SYSTEM'";
  private static final String RESTORE_FORBIDDEN_MESSAGE =
      "Unauthorized to perform operation 'RESTORE' on resource 'BACKUP'";

  private static final TenantRestoreEnvironment RESTORE_ENVIRONMENT =
      new TenantRestoreEnvironment("elasticsearch", false);

  private RecoveryServices services;
  private final ClusterConfigurationManagementRequestSender clusterConfigurationRequestSender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final AuthorizationChecker authorizationChecker = mock(AuthorizationChecker.class);
  private final AuthorizationsConfiguration authorizationsConfig =
      new AuthorizationsConfiguration();
  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);

  @BeforeEach
  public void before() {
    authorizationsConfig.setEnabled(false);
    services =
        new RecoveryServices(
            PHYSICAL_TENANT_ID,
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            clusterConfigurationRequestSender,
            authorizationChecker,
            authorizationsConfig,
            mock(ApiServicesExecutorProvider.class),
            mock(BrokerRequestAuthorizationConverter.class),
            RESTORE_ENVIRONMENT);
  }

  @ParameterizedTest
  @EnumSource(Mode.class)
  public void shouldStampPhysicalTenantIdIntoModeChangeRequest(final Mode mode) {
    // given
    stubModeChangeSuccess();

    // when
    final var future = services.changeMode(mode, true, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(clusterConfigurationRequestSender)
        .modeChange(new ModeChangeRequest(Optional.of(PHYSICAL_TENANT_ID), mode, true));
  }

  @Test
  public void shouldChangeModeWhenAuthorizationsDisabled() {
    // given
    stubModeChangeSuccess();

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(clusterConfigurationRequestSender).modeChange(any());
  }

  @Test
  public void shouldChangeModeWhenUserHasSystemUpdatePermission() {
    // given
    grant(AuthorizationResourceType.SYSTEM, PermissionType.UPDATE);
    stubModeChangeSuccess();

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(clusterConfigurationRequestSender).modeChange(any());
  }

  @Test
  public void shouldChangeModeWhenUserHasBackupRestorePermission() {
    // given - entering and leaving recovery mode is a mandatory step of the restore procedure, so
    // BACKUP:RESTORE alone must authorize it without a second grant
    grant(AuthorizationResourceType.BACKUP, PermissionType.RESTORE);
    stubModeChangeSuccess();

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(clusterConfigurationRequestSender).modeChange(any());
  }

  @Test
  public void shouldFailWithForbiddenWhenUserHasNoAuthorizations() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertForbidden(future, MODE_CHANGE_FORBIDDEN_MESSAGE);
    verify(clusterConfigurationRequestSender, never()).modeChange(any());
  }

  @Test
  public void shouldFailWithForbiddenWhenUserHasAnUnrelatedPermission() {
    // given
    grant(AuthorizationResourceType.BACKUP, PermissionType.READ);

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertForbidden(future, MODE_CHANGE_FORBIDDEN_MESSAGE);
    verify(clusterConfigurationRequestSender, never()).modeChange(any());
  }

  @Test
  public void shouldFailWithForbiddenWhenUpdateIsGrantedOnAnotherResourceType() {
    // given - the caller holds UPDATE, but not on the SYSTEM resource type
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.UPDATE));
    when(authorizationChecker.collectPermissionTypes(
            any(), eq(AuthorizationResourceType.SYSTEM), any()))
        .thenReturn(Collections.emptySet());
    when(authorizationChecker.collectPermissionTypes(
            any(), eq(AuthorizationResourceType.BACKUP), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertForbidden(future, MODE_CHANGE_FORBIDDEN_MESSAGE);
    verify(clusterConfigurationRequestSender, never()).modeChange(any());
  }

  @Test
  public void shouldPassCoordinatorRejectionThroughUnchanged() {
    // given - the caller maps ErrorResponse to a status itself, so a rejection must not be
    // translated or swallowed here
    final var rejection =
        new ErrorResponse(ErrorCode.CONCURRENT_MODIFICATION, "a change is ongoing");
    when(clusterConfigurationRequestSender.modeChange(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.left(rejection)));

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    assertThat(future.join().getLeft()).isEqualTo(rejection);
  }

  @Test
  public void shouldStampItsBoundRestoreEnvironmentIntoTheRequest() {
    // given — the tenant's own database type and continuous-backups flag, not a global default
    final var parameters = new RestoreParameters(List.of(100L, 101L), null, null);
    stubRestoreSuccess();

    // when
    final var future = services.restore(parameters, false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(clusterConfigurationRequestSender)
        .restore(
            new RestoreRequest(
                PHYSICAL_TENANT_ID,
                new TenantRestoreArguments(
                    parameters,
                    RESTORE_ENVIRONMENT.databaseType(),
                    RESTORE_ENVIRONMENT.continuousBackups()),
                false));
  }

  @Test
  public void shouldRestoreWhenAuthorizationsDisabled() {
    // given
    stubRestoreSuccess();

    // when
    final var future = services.restore(restoreParameters(), false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(clusterConfigurationRequestSender).restore(any());
  }

  @Test
  public void shouldRestoreWhenUserHasBackupRestorePermission() {
    // given
    grant(AuthorizationResourceType.BACKUP, PermissionType.RESTORE);
    stubRestoreSuccess();

    // when
    final var future = services.restore(restoreParameters(), false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(clusterConfigurationRequestSender).restore(any());
  }

  @Test
  public void shouldFailRestoreWithForbiddenWhenUserHasNoAuthorizations() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.restore(restoreParameters(), false, authentication);

    // then
    assertForbidden(future, RESTORE_FORBIDDEN_MESSAGE);
    verify(clusterConfigurationRequestSender, never()).restore(any());
  }

  @Test
  public void shouldFailRestoreWithForbiddenWhenUserOnlyHasSystemUpdatePermission() {
    // given - unlike the mode change, restoring is not OR-gated: SYSTEM:UPDATE alone must not
    // authorize consuming a backup
    grant(AuthorizationResourceType.SYSTEM, PermissionType.UPDATE);

    // when
    final var future = services.restore(restoreParameters(), false, authentication);

    // then
    assertForbidden(future, RESTORE_FORBIDDEN_MESSAGE);
    verify(clusterConfigurationRequestSender, never()).restore(any());
  }

  @Test
  public void shouldReportBackupRestoreWhenRestoreIsDenied() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.restore(restoreParameters(), false, authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .withMessageContaining("RESTORE")
        .withMessageContaining("BACKUP");
  }

  @Test
  public void shouldPassRestoreCoordinatorRejectionThroughUnchanged() {
    // given
    final var rejection = new ErrorResponse(ErrorCode.INVALID_STATE, "restore already running");
    when(clusterConfigurationRequestSender.restore(any()))
        .thenReturn(CompletableFuture.completedFuture(Either.left(rejection)));

    // when
    final var future = services.restore(restoreParameters(), false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    assertThat(future.join().getLeft()).isEqualTo(rejection);
  }

  @Test
  public void shouldGetRestoreStatusWhenAuthorizationsDisabled() {
    // given
    final var configuration = stubTopologySuccess();

    // when
    final var future = services.restoreStatus(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    assertThat(future.join().get()).isEqualTo(configuration);
    verify(clusterConfigurationRequestSender).getTopology();
  }

  @Test
  public void shouldGetRestoreStatusWhenUserHasBackupRestorePermission() {
    // given
    grant(AuthorizationResourceType.BACKUP, PermissionType.RESTORE);
    final var configuration = stubTopologySuccess();

    // when
    final var future = services.restoreStatus(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    assertThat(future.join().get()).isEqualTo(configuration);
    verify(clusterConfigurationRequestSender).getTopology();
  }

  @Test
  public void shouldFailGetRestoreStatusWithForbiddenWhenUserHasNoAuthorizations() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.restoreStatus(authentication);

    // then
    assertForbidden(future, RESTORE_FORBIDDEN_MESSAGE);
    verify(clusterConfigurationRequestSender, never()).getTopology();
  }

  @Test
  public void shouldFailGetRestoreStatusWithForbiddenWhenUserOnlyHasSystemUpdatePermission() {
    // given - reading the restore status is gated the same as triggering one: SYSTEM:UPDATE alone
    // must not suffice
    grant(AuthorizationResourceType.SYSTEM, PermissionType.UPDATE);

    // when
    final var future = services.restoreStatus(authentication);

    // then
    assertForbidden(future, RESTORE_FORBIDDEN_MESSAGE);
    verify(clusterConfigurationRequestSender, never()).getTopology();
  }

  @Test
  public void shouldPassGetRestoreStatusCoordinatorRejectionThroughUnchanged() {
    // given
    final var rejection = new ErrorResponse(ErrorCode.INTERNAL_ERROR, "topology is unavailable");
    when(clusterConfigurationRequestSender.getTopology())
        .thenReturn(CompletableFuture.completedFuture(Either.left(rejection)));

    // when
    final var future = services.restoreStatus(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    assertThat(future.join().getLeft()).isEqualTo(rejection);
  }

  private static RestoreParameters restoreParameters() {
    return new RestoreParameters(List.of(100L), null, null);
  }

  private CurrentClusterConfiguration stubTopologySuccess() {
    final var configuration = CurrentClusterConfiguration.fromLegacy(ClusterConfiguration.init());
    when(clusterConfigurationRequestSender.getTopology())
        .thenReturn(CompletableFuture.completedFuture(Either.right(configuration)));
    return configuration;
  }

  private void stubRestoreSuccess() {
    when(clusterConfigurationRequestSender.restore(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new ClusterConfigurationChangeResponse(
                        0L,
                        new LegacyConfigurationChangeResponse(Map.of(), Map.of(), List.of()),
                        null))));
  }

  private void grant(
      final AuthorizationResourceType resourceType, final PermissionType permissionType) {
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());
    when(authorizationChecker.collectPermissionTypes(any(), eq(resourceType), any()))
        .thenReturn(Set.of(permissionType));
  }

  private void stubModeChangeSuccess() {
    when(clusterConfigurationRequestSender.modeChange(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.right(
                    new ClusterConfigurationChangeResponse(
                        0L,
                        new LegacyConfigurationChangeResponse(Map.of(), Map.of(), List.of()),
                        null))));
  }

  private static void assertForbidden(
      final CompletableFuture<?> future, final String expectedMessage) {
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .satisfies(
            exception -> {
              assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
              assertThat(exception.getMessage()).isEqualTo(expectedMessage);
            });
  }
}
