/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.optimize.dto.optimize.GroupDto;
import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import io.camunda.optimize.rest.exceptions.ForbiddenException;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class AuthorizedCollectionService {

  private static final String VIEW_NOT_AUTHORIZED_MESSAGE =
      "User [%s] is not authorized to access collection [%s].";
  private static final String EDIT_NOT_AUTHORIZED_MESSAGE =
      "User [%s] is not authorized to edit/delete collection [%s].";
  private static final String RESOURCE_EDIT_NOT_AUTHORIZED_MESSAGE =
      "User [%s] does not have the role to add/edit collection [%s] resources.";
  private static final Logger LOG =
      org.slf4j.LoggerFactory.getLogger(AuthorizedCollectionService.class);

  private final CollectionReader collectionReader;
  private final AbstractIdentityService identityService;

  public AuthorizedCollectionService(
      final CollectionReader collectionReader, final AbstractIdentityService identityService) {
    this.collectionReader = collectionReader;
    this.identityService = identityService;
  }

  public Optional<RoleType> getUsersCollectionResourceRole(
      final String userId, final String collectionId) throws NotFoundException, ForbiddenException {
    return getAuthorizedCollectionDefinition(userId, collectionId)
        .map(AuthorizedCollectionDefinitionDto::getCollectionResourceRole);
  }

  public AuthorizedCollectionDefinitionDto getAuthorizedCollectionDefinitionOrFail(
      final String userId, final String collectionId) {
    return getAuthorizedCollectionDefinition(userId, collectionId)
        .orElseThrow(
            () ->
                new ForbiddenException(
                    String.format(VIEW_NOT_AUTHORIZED_MESSAGE, userId, collectionId)));
  }

  public AuthorizedCollectionDefinitionDto
      getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(
          final String userId, final String collectionId) {

    final AuthorizedCollectionDefinitionDto collectionDefinition =
        getAuthorizedCollectionDefinitionOrFail(userId, collectionId);
    if (collectionDefinition.getCurrentUserRole().ordinal() < RoleType.MANAGER.ordinal()) {
      throw new ForbiddenException(
          String.format(EDIT_NOT_AUTHORIZED_MESSAGE, userId, collectionId));
    }
    return collectionDefinition;
  }

  public void verifyUserAuthorizedToEditCollectionResources(
      final String userId, final String collectionId) throws NotFoundException, ForbiddenException {
    if (collectionId != null) {
      final AuthorizedCollectionDefinitionDto authorizedCollection =
          getAuthorizedCollectionDefinitionOrFail(userId, collectionId);
      if (authorizedCollection.getCurrentUserRole().ordinal() < RoleType.EDITOR.ordinal()) {
        throw new ForbiddenException(
            String.format(RESOURCE_EDIT_NOT_AUTHORIZED_MESSAGE, userId, collectionId));
      }
    }
  }

  public void verifyUserAuthorizedToEditCollectionRole(
      final String userId, final String collectionId, final String roleId)
      throws NotFoundException, ForbiddenException {
    final AuthorizedCollectionDefinitionDto authCollectionDto =
        getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);

    authCollectionDto.getDefinitionDto().getData().getRoles().stream()
        .filter(role -> role.getId().equals(roleId))
        .forEach(
            role ->
                identityService.validateUserAuthorizedToAccessRoleOrFail(
                    userId, role.getIdentity()));
  }

  public List<AuthorizedCollectionDefinitionDto> getAuthorizedCollectionDefinitions(
      final String userId) {
    return collectionReader.getAllCollections().stream()
        .map(definitionDto -> resolveToAuthorizedCollection(userId, definitionDto))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private Optional<AuthorizedCollectionDefinitionDto> getAuthorizedCollectionDefinition(
      final String userId, final String collectionId) {
    final Optional<CollectionDefinitionDto> collectionDefinition =
        collectionReader.getCollection(collectionId);

    if (collectionDefinition.isEmpty()) {
      LOG.error(
          "Was not able to retrieve collection with id [{}] from the database.", collectionId);
      throw new NotFoundException(
          "Collection does not exist! Tried to retrieve collection with id " + collectionId);
    }

    return resolveToAuthorizedCollection(userId, collectionDefinition.get());
  }

  private Optional<AuthorizedCollectionDefinitionDto> resolveToAuthorizedCollection(
      final String userId, final CollectionDefinitionDto collectionDefinition) {

    final Optional<RoleType> userRole;
    final List<CollectionRoleRequestDto> collectionRoles =
        collectionDefinition.getData().getRoles();
    final Set<String> userGroupIds =
        identityService.getAllGroupsOfUser(userId).stream()
            .map(GroupDto::getId)
            .collect(Collectors.toSet());

    Optional<CollectionRoleRequestDto> highestGrantedAuthorization =
        collectionRoles.stream()
            .filter(roleDto -> roleDto.getIdentity().getType().equals(IdentityType.USER))
            .filter(roleDto -> userId.equals(roleDto.getIdentity().getId()))
            .reduce(BinaryOperator.maxBy(Comparator.comparing(CollectionRoleRequestDto::getRole)));
    // user roles have priority so we only fetch groups if no user role is defined
    if (highestGrantedAuthorization.isEmpty()) {
      highestGrantedAuthorization =
          collectionRoles.stream()
              .filter(roleDto -> roleDto.getIdentity().getType().equals(IdentityType.GROUP))
              .filter(roleDto -> userGroupIds.contains(roleDto.getIdentity().getId()))
              .reduce(
                  BinaryOperator.maxBy(Comparator.comparing(CollectionRoleRequestDto::getRole)));
    }
    userRole = highestGrantedAuthorization.map(CollectionRoleRequestDto::getRole);
    return userRole.map(
        roleType -> new AuthorizedCollectionDefinitionDto(roleType, collectionDefinition));
  }
}
