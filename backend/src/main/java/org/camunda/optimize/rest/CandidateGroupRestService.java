/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.rest;

import static org.camunda.optimize.rest.CandidateGroupRestService.CANDIDATE_GROUP_RESOURCE_PATH;

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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupDefinitionSearchRequestDto;
import org.camunda.optimize.dto.optimize.query.definition.AssigneeCandidateGroupReportSearchRequestDto;
import org.camunda.optimize.service.AssigneeCandidateGroupService;
import org.camunda.optimize.service.security.SessionService;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Path(CANDIDATE_GROUP_RESOURCE_PATH)
@Component
@Slf4j
public class CandidateGroupRestService {

  public static final String CANDIDATE_GROUP_RESOURCE_PATH = "/candidateGroup";
  public static final String CANDIDATE_GROUP_DEFINITION_SEARCH_SUB_PATH = "/search";
  public static final String CANDIDATE_GROUP_REPORTS_SEARCH_SUB_PATH = "/search/reports";

  private final SessionService sessionService;
  private final AssigneeCandidateGroupService assigneeCandidateGroupService;

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
