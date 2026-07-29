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
import io.camunda.zeebe.gateway.admin.ExportingRequestBroadcaster;
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
  private final ExportingRequestBroadcaster exportingRequestBroadcaster =
      mock(ExportingRequestBroadcaster.class);
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
            exportingRequestBroadcaster,
            authorizationChecker,
            authorizationsConfig,
            mock(ApiServicesExecutorProvider.class),
            mock(BrokerRequestAuthorizationConverter.class));
  }

  @Test
  public void shouldDelegatePauseWhenAuthorizationsDisabled() {
    // given
    when(exportingRequestBroadcaster.pauseExporting(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingRequestBroadcaster).pauseExporting(PHYSICAL_TENANT_ID);
  }

  @Test
  public void shouldDelegateSoftPauseToSoftPause() {
    // given
    when(exportingRequestBroadcaster.softPauseExporting(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.pauseExporting(true, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingRequestBroadcaster).softPauseExporting(PHYSICAL_TENANT_ID);
  }

  @Test
  public void shouldDelegateResume() {
    // given
    when(exportingRequestBroadcaster.resumeExporting(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingRequestBroadcaster).resumeExporting(PHYSICAL_TENANT_ID);
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
    verify(exportingRequestBroadcaster, never()).pauseExporting(any());
    verify(exportingRequestBroadcaster, never()).softPauseExporting(any());
  }

  @Test
  public void shouldDelegatePauseWhenUserHasExporterPausePermission() {
    // given
    grantExporterPausePermission();
    when(exportingRequestBroadcaster.pauseExporting(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingRequestBroadcaster).pauseExporting(PHYSICAL_TENANT_ID);
  }

  @Test
  public void shouldDelegateResumeWhenUserHasExporterPausePermission() {
    // given
    grantExporterPausePermission();
    when(exportingRequestBroadcaster.resumeExporting(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(exportingRequestBroadcaster).resumeExporting(PHYSICAL_TENANT_ID);
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
    verify(exportingRequestBroadcaster, never()).pauseExporting(any());
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
    verify(exportingRequestBroadcaster, never()).resumeExporting(any());
  }

  @Test
  public void shouldMapSynchronousIncompleteTopologyExceptionToUnavailable() {
    // given - the broadcaster validates topology synchronously, throwing before it returns a future
    when(exportingRequestBroadcaster.pauseExporting(PHYSICAL_TENANT_ID))
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
    // given - the broadcaster dispatches broker requests asynchronously, so failures surface as an
    // exceptionally-completed future rather than a synchronous throw
    when(exportingRequestBroadcaster.pauseExporting(PHYSICAL_TENANT_ID))
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
    // given - CompletableFuture.allOf inside the broadcaster delivers the cause wrapped in a
    // CompletionException; the ErrorMapper must still unwrap it to the correct status
    when(exportingRequestBroadcaster.pauseExporting(PHYSICAL_TENANT_ID))
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
