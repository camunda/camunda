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

  @Test
  void shouldDelegateToReadServiceForValidRange() {
    // given
    final BusinessValueOverviewResponseDto expected =
        new BusinessValueOverviewResponseDto(
            false, new CoverageDto(0, 0), new AttainmentDto(0, 0), List.of(), List.of());
    when(readService.getOverview(USER, MetricRange.THIRTY_DAYS)).thenReturn(expected);

    // when
    final BusinessValueOverviewResponseDto response = restService.getOverview("30d", request);

    // then
    assertThat(response).isSameAs(expected);
    verify(readService).getOverview(USER, MetricRange.THIRTY_DAYS);
  }

  @Test
  void shouldRejectUnknownRangeWithBadRequest() {
    // 12m is intentionally excluded — SaaS retains only 180 days, see tech design §1
    // given
    assertThatThrownBy(() -> restService.getOverview("12m", request))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("12m");
  }

  @Test
  void shouldRejectNullRangeWithBadRequest() {
    // given
    assertThatThrownBy(() -> restService.getOverview(null, request))
        .isInstanceOf(BadRequestException.class);
  }

  @Test
  void shouldPropagateAuthorizationErrorFromSessionService() {
    // given
    when(sessionService.getRequestUserOrFailNotAuthorized(any()))
        .thenThrow(new io.camunda.optimize.rest.exceptions.NotAuthorizedException("nope"));

    // when
    assertThatThrownBy(() -> restService.getOverview("30d", request))
        .isInstanceOf(io.camunda.optimize.rest.exceptions.NotAuthorizedException.class);
  }

  @Test
  void shouldFailWhenReadServiceThrows() {
    // given
    when(readService.getOverview(anyString(), any())).thenThrow(new IllegalStateException("boom"));

    // when
    assertThatThrownBy(() -> restService.getOverview("30d", request))
        .isInstanceOf(IllegalStateException.class);
  }
}
