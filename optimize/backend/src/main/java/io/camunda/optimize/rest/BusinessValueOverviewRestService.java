/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewDto.MetricRange;
import io.camunda.optimize.dto.optimize.query.businessvalue.BusinessValueOverviewResponseDto;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import io.camunda.optimize.service.businessvalue.BusinessValueOverviewReadService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + BusinessValueOverviewRestService.BUSINESS_VALUE_PATH)
public class BusinessValueOverviewRestService {

  public static final String BUSINESS_VALUE_PATH = "/business-value";
  public static final String OVERVIEW_SUB_PATH = "/overview";

  private final BusinessValueOverviewReadService readService;
  private final SessionService sessionService;

  public BusinessValueOverviewRestService(
      final BusinessValueOverviewReadService readService, final SessionService sessionService) {
    this.readService = readService;
    this.sessionService = sessionService;
  }

  @GetMapping(OVERVIEW_SUB_PATH)
  public BusinessValueOverviewResponseDto getOverview(
      @RequestParam("range") final String range, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    final MetricRange metricRange;
    try {
      metricRange = MetricRange.fromId(range);
    } catch (final IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage(), e);
    }
    return readService.getOverview(userId, metricRange);
  }
}
