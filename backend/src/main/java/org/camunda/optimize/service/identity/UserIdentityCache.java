/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;

import java.util.Optional;

public interface UserIdentityCache {

  void addIdentity(final IdentityWithMetadataResponseDto identity);

  Optional<UserDto> getUserIdentityById(final String id);

  Optional<GroupDto> getGroupIdentityById(final String id);

  IdentitySearchResultResponseDto searchIdentities(final String terms, final IdentityType[] identityTypes,
                                                   final int resultLimit);

  IdentitySearchResultResponseDto searchIdentitiesAfter(final String terms, final IdentityType[] identityTypes,
                                                        final int resultLimit,
                                                        final IdentitySearchResultResponseDto searchAfter);

}
