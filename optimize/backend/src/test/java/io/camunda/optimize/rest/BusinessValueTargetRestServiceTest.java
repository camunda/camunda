/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetResponseDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueTargetUpsertRequestDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.CycleTimeTargetDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.target_value.TargetValueUnit;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.businessvalue.BusinessValueTargetService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BusinessValueTargetRestServiceTest {

  private static final String USER = "demo";
  private static final String TENANT = "tenant-a";
  private static final String PROCESS_KEY = "invoice-automation";

  private BusinessValueTargetService targetService;
  private SessionService sessionService;
  private HttpServletRequest request;
  private BusinessValueTargetRestService restService;

  @BeforeEach
  void setUp() {
    targetService = mock(BusinessValueTargetService.class);
    sessionService = mock(SessionService.class);
    request = mock(HttpServletRequest.class);
    when(sessionService.getRequestUserOrFailNotAuthorized(any())).thenReturn(USER);
    restService = new BusinessValueTargetRestService(targetService, sessionService);
  }

  // --- delegation happy path ---

  @Test
  void shouldDelegateGetToService() {
    // given
    final BusinessValueTargetResponseDto expected =
        new BusinessValueTargetResponseDto(TENANT, PROCESS_KEY, null, 85, null, null);
    when(targetService.readTarget(USER, TENANT, PROCESS_KEY)).thenReturn(expected);

    // when
    final BusinessValueTargetResponseDto response =
        restService.getTarget(PROCESS_KEY, TENANT, request);

    // then
    assertThat(response).isSameAs(expected);
    verify(targetService).readTarget(USER, TENANT, PROCESS_KEY);
  }

  @Test
  void shouldDelegatePutToService() {
    // given
    final BusinessValueTargetUpsertRequestDto body =
        new BusinessValueTargetUpsertRequestDto(
            new CycleTimeTargetDto(8L, TargetValueUnit.HOURS, null), 85);
    final BusinessValueTargetResponseDto expected =
        new BusinessValueTargetResponseDto(
            TENANT,
            PROCESS_KEY,
            new CycleTimeTargetDto(8L, TargetValueUnit.HOURS, 28_800_000L),
            85,
            null,
            USER);
    when(targetService.upsertTarget(USER, TENANT, PROCESS_KEY, body)).thenReturn(expected);

    // when
    final BusinessValueTargetResponseDto response =
        restService.putTarget(PROCESS_KEY, TENANT, body, request);

    // then
    assertThat(response).isSameAs(expected);
    verify(targetService).upsertTarget(USER, TENANT, PROCESS_KEY, body);
  }

  // --- delegation with null / empty body ---

  @Test
  void shouldDelegatePutWithClearBody() {
    // given — both fields null is a valid "clear both" upsert
    final BusinessValueTargetUpsertRequestDto clear =
        new BusinessValueTargetUpsertRequestDto(null, null);
    final BusinessValueTargetResponseDto expected =
        new BusinessValueTargetResponseDto(TENANT, PROCESS_KEY, null, null, null, USER);
    when(targetService.upsertTarget(USER, TENANT, PROCESS_KEY, clear)).thenReturn(expected);

    // when
    final BusinessValueTargetResponseDto response =
        restService.putTarget(PROCESS_KEY, TENANT, clear, request);

    // then
    assertThat(response).isSameAs(expected);
    verify(targetService).upsertTarget(USER, TENANT, PROCESS_KEY, clear);
  }

  // --- exception propagation from the service ---

  @Test
  void shouldPropagateForbiddenFromService() {
    // given
    when(targetService.readTarget(USER, TENANT, PROCESS_KEY))
        .thenThrow(new ForbiddenException("nope"));

    // when + then
    assertThatThrownBy(() -> restService.getTarget(PROCESS_KEY, TENANT, request))
        .isInstanceOf(ForbiddenException.class);
  }

  @Test
  void shouldPropagateNotFoundFromService() {
    // given
    final BusinessValueTargetUpsertRequestDto body =
        new BusinessValueTargetUpsertRequestDto(null, 50);
    when(targetService.upsertTarget(USER, TENANT, "ghost", body))
        .thenThrow(new NotFoundException("no such definition"));

    // when + then
    assertThatThrownBy(() -> restService.putTarget("ghost", TENANT, body, request))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void shouldPropagateBadRequestFromService() {
    // given — service-level semantic validation (cross-field, unit-subset, overflow)
    final BusinessValueTargetUpsertRequestDto invalid =
        new BusinessValueTargetUpsertRequestDto(new CycleTimeTargetDto(8L, null, null), null);
    when(targetService.upsertTarget(USER, TENANT, PROCESS_KEY, invalid))
        .thenThrow(new BadRequestException("value and unit must both be set or null"));

    // when + then
    assertThatThrownBy(() -> restService.putTarget(PROCESS_KEY, TENANT, invalid, request))
        .isInstanceOf(BadRequestException.class);
  }

  // --- URL-safe special characters that browsers/HTTP clients pass through untouched ---

  @ParameterizedTest(name = "shouldPassSpecialTenantIdVerbatim[{0}]")
  @ValueSource(
      strings = {
        "<default>",
        "tenant-with-hyphens",
        "tenant_with_underscores",
        "tenant.with.dots",
        "Mixed-Case_Tenant.01"
      })
  void shouldPassSpecialTenantIdVerbatim(final String tenantId) {
    // given — the controller receives the tenantId already decoded by Spring's @RequestParam
    when(targetService.readTarget(USER, tenantId, PROCESS_KEY))
        .thenReturn(
            new BusinessValueTargetResponseDto(tenantId, PROCESS_KEY, null, null, null, null));

    // when
    final BusinessValueTargetResponseDto response =
        restService.getTarget(PROCESS_KEY, tenantId, request);

    // then
    assertThat(response.tenantId()).isEqualTo(tenantId);
    verify(targetService).readTarget(USER, tenantId, PROCESS_KEY);
  }

  @ParameterizedTest(name = "shouldPassSpecialProcessDefinitionKeyVerbatim[{0}]")
  @ValueSource(
      strings = {
        "invoice-automation",
        "invoice_automation",
        "com.acme.invoice",
        "Order-01",
      })
  void shouldPassSpecialProcessDefinitionKeyVerbatim(final String processKey) {
    // given
    when(targetService.readTarget(USER, TENANT, processKey))
        .thenReturn(new BusinessValueTargetResponseDto(TENANT, processKey, null, null, null, null));

    // when
    final BusinessValueTargetResponseDto response =
        restService.getTarget(processKey, TENANT, request);

    // then
    assertThat(response.processKey()).isEqualTo(processKey);
    verify(targetService).readTarget(USER, TENANT, processKey);
  }
}
