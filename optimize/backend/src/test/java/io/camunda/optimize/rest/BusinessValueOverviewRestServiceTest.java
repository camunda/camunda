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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.AttainmentDto;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto.CoverageDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.businessvalue.BusinessValueOverviewReadService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class BusinessValueOverviewRestServiceTest {

  private static final String USER = "demo";

  private BusinessValueOverviewReadService readService;
  private SessionService sessionService;
  private HttpServletRequest request;
  private BusinessValueOverviewRestService restService;

  @BeforeEach
  void setUp() {
    readService = mock(BusinessValueOverviewReadService.class);
    sessionService = mock(SessionService.class);
    request = mock(HttpServletRequest.class);
    when(sessionService.getRequestUserOrFailNotAuthorized(any())).thenReturn(USER);
    restService = new BusinessValueOverviewRestService(readService, sessionService);
  }

  @ParameterizedTest(name = "range={0} → resolves to {1}")
  @CsvSource({"7d,  SEVEN_DAYS", "30d, THIRTY_DAYS", "3m,  THREE_MONTHS", "6m,  SIX_MONTHS"})
  void shouldDelegateToReadServiceForEveryValidRangePreset(
      final String rangeParam, final MetricRange expected) {
    // given
    final BusinessValueOverviewResponseDto expectedResponse =
        new BusinessValueOverviewResponseDto(
            false, new CoverageDto(0, 0), new AttainmentDto(0, 0), List.of(), List.of());
    when(readService.getOverview(USER, expected)).thenReturn(expectedResponse);

    // when
    final BusinessValueOverviewResponseDto response = restService.getOverview(rangeParam, request);

    // then
    assertThat(response).isSameAs(expectedResponse);
    verify(readService).getOverview(USER, expected);
  }

  @Test
  void shouldRejectUnknownRangeWithBadRequest() {
    // 12m is intentionally excluded because SaaS retains only 180 days — see tech design §1.
    // given / when / then
    assertThatThrownBy(() -> restService.getOverview("12m", request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("12m");
  }

  @ParameterizedTest(name = "range=\"{0}\" is rejected with 400")
  @ValueSource(strings = {"12m", "1y", "xyz", "30 d", "30D", "7D", "3M", "6M", " 30d", "30d "})
  void shouldRejectAnyOtherRangeStringWithBadRequest(final String rangeParam) {
    // MetricRange.fromId is intentionally case-sensitive and does not trim, so anything that
    // doesn't match one of the four canonical presets — including case variants and stray
    // whitespace — falls into the same failure bucket as an obviously-bad "xyz".
    // given / when / then
    assertThatThrownBy(() -> restService.getOverview(rangeParam, request))
        .isInstanceOf(BadRequestException.class);
  }

  @ParameterizedTest
  @NullAndEmptySource
  void shouldRejectNullOrEmptyRangeWithBadRequest(final String rangeParam) {
    // given / when / then
    assertThatThrownBy(() -> restService.getOverview(rangeParam, request))
        .isInstanceOf(BadRequestException.class);
  }

  @ParameterizedTest(name = "range=\"{0}\" (special-char payload) is rejected safely")
  @ValueSource(
      strings = {
        "' OR '1'='1",
        "\"; DROP INDEX overview; --",
        "<script>alert(1)</script>",
        "../../etc/passwd",
        "%00",
        "%2F30d",
        "30d%00",
        "30d\n7d",
        "30d;7d"
      })
  void shouldRejectSpecialCharacterPayloadsAsBadRequestWithoutReachingTheService(
      final String rangeParam) {
    // Anything that isn't one of the four canonical presets is a plain 400; the important part
    // is that no injection-style payload flows through to the read service or the datastore.
    // given / when / then
    assertThatThrownBy(() -> restService.getOverview(rangeParam, request))
        .isInstanceOf(BadRequestException.class);
    verify(readService, org.mockito.Mockito.never()).getOverview(any(), any());
  }

  @Test
  void shouldPropagateAuthorizationErrorFromSessionService() {
    // given
    when(sessionService.getRequestUserOrFailNotAuthorized(any()))
        .thenThrow(new io.camunda.optimize.rest.exceptions.NotAuthorizedException("nope"));

    // when / then
    assertThatThrownBy(() -> restService.getOverview("30d", request))
        .isInstanceOf(io.camunda.optimize.rest.exceptions.NotAuthorizedException.class);
  }

  @Test
  void shouldNotInvokeReadServiceWhenAuthorizationFailsForAnOtherwiseValidRange() {
    // A malformed range is caught before auth; a valid range with a bad session must still
    // bail out before hitting the read service. This makes the ordering — auth first, then
    // range parsing — an assertion rather than an implementation detail.
    // given
    when(sessionService.getRequestUserOrFailNotAuthorized(any()))
        .thenThrow(new io.camunda.optimize.rest.exceptions.NotAuthorizedException("nope"));

    // when / then
    assertThatThrownBy(() -> restService.getOverview("30d", request))
        .isInstanceOf(io.camunda.optimize.rest.exceptions.NotAuthorizedException.class);
    verify(readService, org.mockito.Mockito.never()).getOverview(any(), any());
  }

  @Test
  void shouldFailWhenReadServiceThrows() {
    // given
    when(readService.getOverview(anyString(), any())).thenThrow(new IllegalStateException("boom"));

    // when / then
    assertThatThrownBy(() -> restService.getOverview("30d", request))
        .isInstanceOf(IllegalStateException.class);
  }
}
