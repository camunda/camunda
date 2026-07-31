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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ModeChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class RecoveryServicesTest {

  private static final String PHYSICAL_TENANT_ID = "testtenant";

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
            mock(BrokerRequestAuthorizationConverter.class));
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
        .modeChange(new ModeChangeRequest(PHYSICAL_TENANT_ID, mode, true));
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
    assertForbidden(future);
    verify(clusterConfigurationRequestSender, never()).modeChange(any());
  }

  @Test
  public void shouldFailWithForbiddenWhenUserHasAnUnrelatedPermission() {
    // given
    grant(AuthorizationResourceType.BACKUP, PermissionType.READ);

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertForbidden(future);
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
    assertForbidden(future);
    verify(clusterConfigurationRequestSender, never()).modeChange(any());
  }

  @Test
  public void shouldReportSystemUpdateWhenDenied() {
    // given - the denial names the permission that exists for this operation rather than every
    // accepted alternative
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.changeMode(Mode.RECOVERING, false, authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .withMessageContaining("UPDATE")
        .withMessageContaining("SYSTEM");
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
                    new ClusterConfigurationChangeResponse(0L, Map.of(), Map.of(), List.of()))));
  }

  private static void assertForbidden(final CompletableFuture<?> future) {
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.FORBIDDEN);
  }
}
