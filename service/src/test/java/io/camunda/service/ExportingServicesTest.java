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

import io.atomix.cluster.MemberId;
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
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState;
import io.camunda.zeebe.gateway.admin.ExportingStatus;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class ExportingServicesTest {

  private static final String PHYSICAL_TENANT_ID = "testtenant";

  private ExportingServices services;
  private final ClusterConfigurationManagementRequestSender requestSender =
      mock(ClusterConfigurationManagementRequestSender.class);
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
            requestSender,
            authorizationChecker,
            authorizationsConfig,
            mock(ApiServicesExecutorProvider.class),
            mock(BrokerRequestAuthorizationConverter.class));
  }

  @Test
  public void shouldDelegatePauseWhenAuthorizationsDisabled() {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExportingState(captor.capture())).thenReturn(emptyPlan());

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    assertThat(captor.getValue().state()).isEqualTo(ExportingState.PAUSED);
  }

  @Test
  public void shouldDelegateSoftPauseToSoftPause() {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExportingState(captor.capture())).thenReturn(emptyPlan());

    // when
    final var future = services.pauseExporting(true, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    assertThat(captor.getValue().state()).isEqualTo(ExportingState.SOFT_PAUSED);
  }

  @Test
  public void shouldDelegateResume() {
    // given
    final var captor = ArgumentCaptor.forClass(ExportingStateChangeRequest.class);
    when(requestSender.changeExportingState(captor.capture())).thenReturn(emptyPlan());

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    assertThat(captor.getValue().state()).isEqualTo(ExportingState.EXPORTING);
  }

  @Test
  public void shouldDelegateStatusQuery() {
    // given
    when(requestSender.getTopology())
        .thenReturn(topology(Map.of(MemberId.from("0"), ExportingState.SOFT_PAUSED)));

    // when
    final var future = services.getExportingStatus(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1)).isEqualTo(ExportingStatus.SOFT_PAUSED);
  }

  @Test
  public void shouldReportMixedStatusWhenReplicasDisagree() {
    // given
    when(requestSender.getTopology())
        .thenReturn(
            topology(
                Map.of(
                    MemberId.from("0"), ExportingState.PAUSED,
                    MemberId.from("1"), ExportingState.EXPORTING)));

    // when
    final var future = services.getExportingStatus(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1)).isEqualTo(ExportingStatus.MIXED);
  }

  @Test
  public void shouldReportExportingStatusWhenNeverControlledByConfig() {
    // given - a replica config never touched by a state-change operation is UNKNOWN
    when(requestSender.getTopology())
        .thenReturn(topology(Map.of(MemberId.from("0"), ExportingState.UNKNOWN)));

    // when
    final var future = services.getExportingStatus(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1)).isEqualTo(ExportingStatus.EXPORTING);
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
    verify(requestSender, never()).getTopology();
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
    verify(requestSender, never()).changeExportingState(any());
  }

  @Test
  public void shouldDelegatePauseWhenUserHasExporterPausePermission() {
    // given
    grantExporterPausePermission();
    when(requestSender.changeExportingState(any())).thenReturn(emptyPlan());

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(requestSender).changeExportingState(any());
  }

  @Test
  public void shouldDelegateResumeWhenUserHasExporterPausePermission() {
    // given
    grantExporterPausePermission();
    when(requestSender.changeExportingState(any())).thenReturn(emptyPlan());

    // when
    final var future = services.resumeExporting(authentication);

    // then
    assertThat(future).succeedsWithin(ofSeconds(1));
    verify(requestSender).changeExportingState(any());
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
    verify(requestSender, never()).changeExportingState(any());
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
    verify(requestSender, never()).changeExportingState(any());
  }

  @Test
  public void shouldMapFailedSubmissionToServiceException() {
    // given - the coordinator rejected the request outright
    when(requestSender.changeExportingState(any()))
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new ErrorResponse(ErrorCode.INVALID_REQUEST, "nope"))));

    // when
    final var future = services.pauseExporting(false, authentication);

    // then
    assertThat(future)
        .failsWithin(ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .asInstanceOf(type(ServiceException.class))
        .extracting(ServiceException::getStatus)
        .isEqualTo(Status.INTERNAL);
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> emptyPlan() {
    return CompletableFuture.completedFuture(
        Either.right(new ClusterConfigurationChangeResponse(0, Map.of(), Map.of(), List.of())));
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfiguration>> topology(
      final Map<MemberId, ExportingState> statesByMember) {
    final Map<MemberId, MemberState> members =
        statesByMember.entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    entry ->
                        MemberState.initializeAsActive(
                            Map.of(
                                1,
                                PartitionState.active(
                                    1,
                                    DynamicPartitionConfig.init()
                                        .updateExporting(
                                            config -> config.withState(entry.getValue())))))));
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfiguration(
                1,
                members,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty())));
  }
}
