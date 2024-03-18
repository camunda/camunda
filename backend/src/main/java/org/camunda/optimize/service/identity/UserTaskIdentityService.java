/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.identity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.camunda.optimize.dto.optimize.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;

public interface UserTaskIdentityService {

  default List<GroupDto> getCandidateGroupIdentitiesById(final Collection<String> ids) {
    return getIdentities(
            ids.stream()
                .map(id -> new IdentityDto(id, IdentityType.GROUP))
                .collect(Collectors.toSet()))
        .stream()
        .filter(GroupDto.class::isInstance)
        .map(GroupDto.class::cast)
        .toList();
  }

  default List<UserDto> getAssigneesByIds(final Collection<String> assigneeIds) {
    return getIdentities(
            assigneeIds.stream()
                .map(id -> new IdentityDto(id, IdentityType.USER))
                .collect(Collectors.toSet()))
        .stream()
        .filter(UserDto.class::isInstance)
        .map(UserDto.class::cast)
        .toList();
  }

  IdentitySearchResultResponseDto searchAmongIdentitiesWithIds(
      final String terms,
      final Collection<String> identityIds,
      final IdentityType identityType,
      final int resultLimit);

  Optional<IdentityWithMetadataResponseDto> getIdentityByIdAndType(
      final String id, final IdentityType type);

  List<IdentityWithMetadataResponseDto> getIdentities(final Collection<IdentityDto> identities);
}
