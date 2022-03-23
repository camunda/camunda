/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@Conditional(CCSMCondition.class)
public class CCSMIdentityService extends AbstractIdentityService {

  public CCSMIdentityService(final ConfigurationService configurationService) {
    super(configurationService);
  }

  @Override
  public Optional<UserDto> getUserById(final String userId) {
    return Optional.empty();
  }

  @Override
  public Optional<GroupDto> getGroupById(final String groupId) {
    return Optional.empty();
  }

  @Override
  public List<GroupDto> getAllGroupsOfUser(final String userId) {
    return Collections.emptyList();
  }

  @Override
  public boolean isUserAuthorizedToAccessIdentity(final String userId, final IdentityDto identity) {
    return true;
  }

  @Override
  public IdentitySearchResultResponseDto searchForIdentitiesAsUser(final String userId, final String searchString,
                                                                   final int maxResults) {
    return new IdentitySearchResultResponseDto(0, Collections.emptyList());
  }

}
