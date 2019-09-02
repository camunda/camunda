/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.security;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.engine.GroupDto;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedSimpleCollectionDefinitionDto;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@AllArgsConstructor
@Component
public class CollectionAuthorizationService {

  private final IdentityService identityService;

  public Optional<AuthorizedSimpleCollectionDefinitionDto> resolveToAuthorizedSimpleCollection(
    final SimpleCollectionDefinitionDto collectionDefinition, final String userId) {
    final List<CollectionRoleDto> collectionRoles = collectionDefinition.getData().getRoles();
    final Set<String> userGroupIds = identityService.getAllGroupsOfUser(userId).stream()
      .map(GroupDto::getId)
      .collect(Collectors.toSet());

    final Optional<CollectionRoleDto> highestGrantedAuthorizationFromUsersGroups = collectionRoles.stream()
      .filter(roleDto -> roleDto.getIdentity().getType().equals(IdentityType.GROUP))
      .filter(roleDto -> userGroupIds.contains(roleDto.getIdentity().getId()))
      .reduce(BinaryOperator.maxBy(Comparator.comparing(CollectionRoleDto::getRole)));

    final Optional<CollectionRoleDto> highestGrantedAuthorizationByUserId = collectionRoles.stream()
      .filter(roleDto -> roleDto.getIdentity().getType().equals(IdentityType.USER))
      .filter(roleDto -> userId.equals(roleDto.getIdentity().getId()))
      .reduce(BinaryOperator.maxBy(Comparator.comparing(CollectionRoleDto::getRole)));

    // the stream order reflects the priority here, user assigned roles have priority over group roles
    return Stream.of(highestGrantedAuthorizationByUserId, highestGrantedAuthorizationFromUsersGroups)
      .filter(Optional::isPresent)
      .map(Optional::get)
      .findFirst()
      .map(roleDto -> new AuthorizedSimpleCollectionDefinitionDto(roleDto.getRole(), collectionDefinition));
  }

}
