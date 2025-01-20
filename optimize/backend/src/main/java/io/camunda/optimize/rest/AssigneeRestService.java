/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.AssigneeRestService.ASSIGNEE_RESOURCE_PATH;
import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupDefinitionSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import io.camunda.optimize.service.AssigneeCandidateGroupService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping(REST_API_PATH + ASSIGNEE_RESOURCE_PATH)
public class AssigneeRestService {

  public static final String ASSIGNEE_RESOURCE_PATH = "/assignee";
  public static final String ASSIGNEE_DEFINITION_SEARCH_SUB_PATH = "/search";
  public static final String ASSIGNEE_REPORTS_SEARCH_SUB_PATH = "/search/reports";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(AssigneeRestService.class);

  private final SessionService sessionService;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;

  public AssigneeRestService(
      final SessionService sessionService,
      final AssigneeCandidateGroupService assigneeCandidateGroupService) {
    this.sessionService = sessionService;
    this.assigneeCandidateGroupService = assigneeCandidateGroupService;
  }

  @GetMapping
  public List<UserDto> getAssigneesByIds(
      @RequestParam(name = "idIn", required = false) final String commaSeparatedIdn) {
    if (StringUtils.isEmpty(commaSeparatedIdn)) {
      return Collections.emptyList();
    }
    return assigneeCandidateGroupService.getAssigneesByIds(
        Arrays.asList(commaSeparatedIdn.split(",")));
  }

  @PostMapping(ASSIGNEE_DEFINITION_SEARCH_SUB_PATH)
  public IdentitySearchResultResponseDto searchAssignees(
      @Valid @RequestBody final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return assigneeCandidateGroupService.searchForAssigneesAsUser(userId, requestDto);
  }

  @PostMapping(ASSIGNEE_REPORTS_SEARCH_SUB_PATH)
  public IdentitySearchResultResponseDto searchAssignees(
      @Valid @RequestBody final AssigneeCandidateGroupReportSearchRequestDto requestDto,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return assigneeCandidateGroupService.searchForAssigneesAsUser(userId, requestDto);
  }
}
