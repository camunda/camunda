/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.IdentityWithMetadataResponseDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.IdentitySearchResultResponseDto;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

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
