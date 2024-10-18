/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;

import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupDefinitionSearchRequestDto;
import io.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import io.camunda.optimize.service.AssigneeCandidateGroupService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Path(CANDIDATE_GROUP_RESOURCE_PATH)
@Component
public class CandidateGroupRestService {

  public static final String CANDIDATE_GROUP_RESOURCE_PATH = "/candidateGroup";
  public static final String CANDIDATE_GROUP_DEFINITION_SEARCH_SUB_PATH = "/search";
  public static final String CANDIDATE_GROUP_REPORTS_SEARCH_SUB_PATH = "/search/reports";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(CandidateGroupRestService.class);

  private final SessionService sessionService;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;

  public CandidateGroupRestService(
      final SessionService sessionService,
      final AssigneeCandidateGroupService assigneeCandidateGroupService) {
    this.sessionService = sessionService;
    this.assigneeCandidateGroupService = assigneeCandidateGroupService;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public List<GroupDto> getCandidateGroupsByIds(
      @QueryParam("idIn") final String commaSeparatedIdn) {
    if (StringUtils.isEmpty(commaSeparatedIdn)) {
      return Collections.emptyList();
    }
    return assigneeCandidateGroupService.getCandidateGroupsByIds(
        Arrays.asList(commaSeparatedIdn.split(",")));
  }

  @POST
  @Path(CANDIDATE_GROUP_DEFINITION_SEARCH_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdentitySearchResultResponseDto searchCandidateGroups(
      @Context final ContainerRequestContext requestContext,
      @Valid final AssigneeCandidateGroupDefinitionSearchRequestDto requestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return assigneeCandidateGroupService.searchForCandidateGroupsAsUser(userId, requestDto);
  }

  @POST
  @Path(CANDIDATE_GROUP_REPORTS_SEARCH_SUB_PATH)
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  public IdentitySearchResultResponseDto searchCandidateGroups(
      @Context final ContainerRequestContext requestContext,
      @Valid final AssigneeCandidateGroupReportSearchRequestDto requestDto) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(requestContext);
    return assigneeCandidateGroupService.searchForCandidateGroupsAsUser(userId, requestDto);
  }
}
