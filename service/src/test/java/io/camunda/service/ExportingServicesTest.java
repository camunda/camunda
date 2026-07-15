/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.api.model.authz.PermissionType;
import io.camunda.security.api.model.config.AuthorizationsConfiguration;
import io.camunda.security.core.authz.AuthorizationChecker;
import io.camunda.service.admin.IncompleteTopologyException;
import io.camunda.service.admin.exporting.ExportingControlApi;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ExportingServicesTest {

  private static final String PHYSICAL_TENANT_ID = "test-tenant";

  private ExportingServices services;
  private final ExportingControlApi exportingControlApi = mock(ExportingControlApi.class);
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
            exportingControlApi,
            authorizationChecker,
            authorizationsConfig,
            mock(ApiServicesExecutorProvider.class),
            null);
  }

  @Test
  public void pauseExportingShouldDelegateWhenAuthorizationsDisabled() {
    // given
    when(exportingControlApi.pauseExporting(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(exportingControlApi).pauseExporting(PHYSICAL_TENANT_ID);
  }

  @Test
  public void softPauseExportingShouldDelegateToSoftPause() {
    // given
    when(exportingControlApi.softPauseExporting(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.pauseExporting(true, authentication);

    // then
    assertThat(future).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(exportingControlApi).softPauseExporting(PHYSICAL_TENANT_ID);
  }

  @Test
  public void resumeExportingShouldDelegate() {
    // given
    when(exportingControlApi.resumeExporting(PHYSICAL_TENANT_ID))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future).succeedsWithin(java.time.Duration.ofSeconds(1));
    verify(exportingControlApi).resumeExporting(PHYSICAL_TENANT_ID);
  }

  @Test
  public void pauseExportingShouldCompleteExceptionallyWhenUserHasNoAuthorizations() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Collections.emptySet());

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    verify(exportingControlApi, never()).pauseExporting(any());
    verify(exportingControlApi, never()).softPauseExporting(any());
  }

  @Test
  public void resumeExportingShouldCompleteExceptionallyWhenUserIsNotAuthorized() {
    // given
    authorizationsConfig.setEnabled(true);
    when(authorizationChecker.collectPermissionTypes(any(), any(), any()))
        .thenReturn(Set.of(PermissionType.READ));

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    verify(exportingControlApi, never()).resumeExporting(any());
  }

  @Test
  public void pauseExportingShouldMapIncompleteTopologyExceptionToUnavailable() {
    // given
    when(exportingControlApi.pauseExporting(PHYSICAL_TENANT_ID))
        .thenThrow(new IncompleteTopologyException("Topology is incomplete"));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future.isCompletedExceptionally()).isTrue();
    final var exception = org.assertj.core.api.Assertions.catchThrowable(future::join).getCause();
    assertThat(exception).isInstanceOf(ServiceException.class);
    assertThat(((ServiceException) exception).getStatus()).isEqualTo(Status.UNAVAILABLE);
  }
}
