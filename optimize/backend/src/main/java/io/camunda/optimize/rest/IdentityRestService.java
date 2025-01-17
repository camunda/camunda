/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.REST_API_PATH;

import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import io.camunda.optimize.dto.optimize.rest.UserServiceResponseDto;
import io.camunda.optimize.rest.security.newwork.UserService;
import io.camunda.optimize.rest.security.newwork.UserServiceUserDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(REST_API_PATH + IdentityRestService.IDENTITY_RESOURCE_PATH)
public class IdentityRestService {
  public static final String IDENTITY_RESOURCE_PATH = "/identity";
  public static final String IDENTITY_SEARCH_SUB_PATH = "/search";
  public static final String CURRENT_USER_IDENTITY_SUB_PATH = "/current/user";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(IdentityRestService.class);

  private final AbstractIdentityService identityService;
  private final SessionService sessionService;
  @Autowired private UserService userService;

  public IdentityRestService(
      final AbstractIdentityService identityService, final SessionService sessionService) {
    this.identityService = identityService;
    this.sessionService = sessionService;
  }

  @GetMapping(path = IDENTITY_SEARCH_SUB_PATH)
  public IdentitySearchResultResponseDto searchIdentity(
      @RequestParam(value = "terms", required = false) final String searchTerms,
      @RequestParam(value = "limit", defaultValue = "25") final int limit,
      @RequestParam(value = "excludeUserGroups", required = false) final boolean excludeUserGroups,
      final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return identityService.searchForIdentitiesAsUser(
        userId, Optional.ofNullable(searchTerms).orElse(""), limit, excludeUserGroups);
  }

  @GetMapping(path = "/{id}")
  public IdentityWithMetadataResponseDto getIdentityById(
      @PathVariable("id") final String identityId, final HttpServletRequest request) {
    final String userId = sessionService.getRequestUserOrFailNotAuthorized(request);
    return identityService
        .getIdentityWithMetadataForIdAsUser(userId, identityId)
        .orElseThrow(
            () ->
                new OptimizeRuntimeException(
                    "Could find neither a user nor a group with the id [" + identityId + "]."));
  }

  @GetMapping(path = CURRENT_USER_IDENTITY_SUB_PATH)
  public UserServiceResponseDto getCurrentUser(final HttpServletRequest request) {
    final UserServiceUserDto x = userService.getCurrentUser();
    final String userId = x.getUserId();
    // this throws an error related to 'invalid issuer URL'
    // final UserDto currentUserDto =
    //    identityService.getCurrentUserById(userId, request).orElseGet(() -> new UserDto(userId));
    // figure out how to fetch authorizations from identity with `userService`, so that
    // i can remove the call to indentityService

    // demo/demo has no tenant/group/role, those will need to be set up

    // will need to massage the new data into the existing dto
    return new UserServiceResponseDto(x, identityService.getEnabledAuthorizations());
  }
}
