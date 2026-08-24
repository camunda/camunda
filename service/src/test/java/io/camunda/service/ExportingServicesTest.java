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
import io.camunda.zeebe.dynamic.config.api.ExportingStateController.ByTenant;
import io.camunda.zeebe.dynamic.config.api.ExportingStatus;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExportingServicesTest {

  private static final String PHYSICAL_TENANT_ID = "testtenant";

  private ExportingServices services;
  private final ByTenant exportingStateController = mock(ByTenant.class);
  private final AuthorizationChecker authorizationChecker = mock(AuthorizationChecker.class);
  private final AuthorizationsConfiguration authorizationsConfig =
      new AuthorizationsConfiguration();
  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);

  @BeforeEach
  public void before() {
    authorizationsConfig.setEnabled(false);
    services =
        new ExportingServices(
            PHYSICAL_TENANT_ID,
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            exportingStateController,
            authorizationChecker,
            authorizationsConfig,
            mock(ApiServicesExecutorProvider.class),
            mock(BrokerRequestAuthorizationConverter.class));
  }

  @Test
  public void shouldDelegatePauseWhenAuthorizationsDisabled() {
    // given
    when(exportingStateController.pauseExporting())
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingStateController).pauseExporting();
  }

  @Test
  public void shouldDelegateSoftPauseToSoftPause() {
    // given
    when(exportingStateController.softPauseExporting())
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.pauseExporting(true, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingStateController).softPauseExporting();
  }

  @Test
  public void shouldDelegateResume() {
    // given
    when(exportingStateController.resumeExporting())
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingStateController).resumeExporting();
  }

  @Test
  public void shouldDelegateStatusQuery() {
    // given
    when(exportingStateController.getExportingStatus())
        .thenReturn(CompletableFuture.completedFuture(ExportingStatus.SOFT_PAUSED));

    // when
    final var future = services.getExportingStatus(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1)).isEqualTo(ExportingStatus.SOFT_PAUSED);
    verify(exportingStateController).getExportingStatus();
  }

  @Test
  public void shouldFailStatusQueryWithForbiddenWhenUserHasNoAuthorizations() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.getExportingStatus(authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.FORBIDDEN);
    verify(exportingStateController, never()).getExportingStatus();
  }

  @Test
  public void shouldFailPauseWithForbiddenWhenUserHasNoAuthorizations() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.FORBIDDEN);
    verify(exportingStateController, never()).pauseExporting();
    verify(exportingStateController, never()).softPauseExporting();
  }

  @Test
  public void shouldDelegatePauseWhenUserHasExporterPausePermission() {
    // given
    grantExporterPausePermission();
    when(exportingStateController.pauseExporting())
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingStateController).pauseExporting();
  }

  @Test
  public void shouldDelegateResumeWhenUserHasExporterPausePermission() {
    // given
    grantExporterPausePermission();
    when(exportingStateController.resumeExporting())
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingStateController).resumeExporting();
  }

  @Test
  public void shouldFailPauseWithForbiddenWhenPausePermissionIsGrantedOnAnotherResourceType() {
    // given - the caller holds PAUSE, but not on the EXPORTER resource type
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.PAUSE));
    when(authorizationChecker.collectPermissionTypes(
            any(), eq(AuthorizationResourceType.EXPORTER), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.FORBIDDEN);
    verify(exportingStateController, never()).pauseExporting();
  }

  private void grantExporterPausePermission() {
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());
    when(authorizationChecker.collectPermissionTypes(
            any(), eq(AuthorizationResourceType.EXPORTER), any()))
        .thenReturn(Set.of(PermissionType.PAUSE));
  }

  @Test
  public void shouldFailResumeWithForbiddenWhenUserIsNotAuthorized() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.FORBIDDEN);
    verify(exportingStateController, never()).resumeExporting();
  }

  @Test
  public void shouldMapSynchronousFailureToServiceException() {
    // given - a synchronous throw before a future is even returned must still be mapped
    when(exportingStateController.pauseExporting())
        .thenThrow(new IncompleteTopologyException("Topology is incomplete"));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  public void shouldMapAsyncFailureToServiceException() {
    // given - an exceptionally-completed future must be mapped the same way as a synchronous throw
    when(exportingStateController.pauseExporting())
        .thenReturn(
            CompletableFuture.failedFuture(
                new IncompleteTopologyException("Topology is incomplete")));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  public void shouldMapCompletionExceptionWrappedAsyncFailureToServiceException() {
    // given - a CompletionException-wrapped cause must still be unwrapped to the correct status
    when(exportingStateController.pauseExporting())
        .thenReturn(
            CompletableFuture.failedFuture(
                new CompletionException(
                    new IncompleteTopologyException("Topology is incomplete"))));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.UNAVAILABLE);
  }
}
