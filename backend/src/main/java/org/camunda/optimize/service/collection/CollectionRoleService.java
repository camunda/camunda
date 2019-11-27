/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.BaseCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedSimpleCollectionDefinitionDto;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import javax.ws.rs.BadRequestException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
public class CollectionRoleService {
  private final AuthorizedCollectionService authorizedCollectionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final CollectionWriter collectionWriter;
  private final IdentityService identityService;

  @SuppressWarnings("Convert2MethodRef")
  void enrichRoleIdentityMetaData(final BaseCollectionDefinitionDto<?> collectionDefinitionDto) {
    final CollectionDataDto collectionData = collectionDefinitionDto.getData();
    collectionData.setRoles(
      collectionData.getRoles().stream()
        .peek(roleDto -> {
          final IdentityDto roleIdentity = roleDto.getIdentity();
          switch (roleIdentity.getType()) {
            case GROUP:
              // Note: Method reference cannot be used here as it might trigger
              // a compilation AssertionError on newer JDK's.
              // See https://bugs.openjdk.java.net/browse/JDK-8210734
              identityService.getGroupById(roleIdentity.getId()).ifPresent(groupDto -> roleDto.setIdentity(groupDto));
              break;
            case USER:
              // Note: Method reference cannot be used here as it might trigger
              // a compilation AssertionError on newer JDK's.
              // See https://bugs.openjdk.java.net/browse/JDK-8210734
              identityService.getUserById(roleIdentity.getId()).ifPresent(userDto -> roleDto.setIdentity(userDto));
              break;
            default:
              throw new OptimizeRuntimeException("Unsupported identity type " + roleIdentity.getType());
          }
        })
        .collect(Collectors.toList())
    );
  }

  public CollectionRoleDto addRoleToCollection(final String userId,
                                               final String collectionId,
                                               final CollectionRoleDto roleDto) throws
                                                                                OptimizeCollectionConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    verifyIdentityExists(roleDto.getIdentity());
    return collectionWriter.addRoleToCollection(collectionId, roleDto, userId);
  }

  public void updateRoleOfCollection(final String userId,
                                     final String collectionId,
                                     final String roleEntryId,
                                     final CollectionRoleUpdateDto roleUpdateDto) throws OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    collectionWriter.updateRoleInCollection(collectionId, roleEntryId, roleUpdateDto, userId);
  }

  public void removeRoleFromCollectionUnlessIsLastManager(String userId, String collectionId, String roleEntryId)
    throws OptimizeConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    collectionWriter.removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, userId);
  }

  public void verifyIdentityExists(final IdentityDto identity) {
    final boolean identityFound;
    switch (identity.getType()) {
      case USER:
        identityFound = identityService.getUserById(identity.getId()).isPresent();
        break;
      case GROUP:
        identityFound = identityService.getGroupById(identity.getId()).isPresent();
        break;
      default:
        throw new OptimizeRuntimeException("Unsupported identity type: " + identity.getType());
    }
    if (!identityFound) {
      throw new BadRequestException(
        String.format("%s with id %s does not exist in Optimize", identity.getType(), identity.getId())
      );
    }
  }

  public List<CollectionRoleRestDto> getAllRolesOfCollectionSorted(String userId, String collectionId) {
    AuthorizedSimpleCollectionDefinitionDto authCollectionDto = getSimpleCollectionDefinitionWithRoleMetadata(
      userId,
      collectionId
    );

    List<CollectionRoleRestDto> roles = new ArrayList<>();
    authCollectionDto.getDefinitionDto()
      .getData()
      .getRoles()
      .forEach(role -> roles.add(new CollectionRoleRestDto(role)));

    if (authCollectionDto.getCurrentUserRole().equals(RoleType.MANAGER)) {
      for (CollectionRoleRestDto role : roles) {
        List<CollectionScopeEntryDto> scopes = authCollectionDto.getDefinitionDto().getData().getScope();
        Boolean hasFullScopeAuthorizations = scopes.stream()
          .allMatch(s -> definitionAuthorizationService.isAuthorizedToSeeDefinition(
            role.getIdentity().getId(),
            role.getIdentity().getType(),
            s.getDefinitionKey(),
            s.getDefinitionType(),
            s.getTenants()
          ));
        role.setHasFullScopeAuthorizations(hasFullScopeAuthorizations);
      }
    }

    Collections.sort(roles);
    return roles;
  }

  AuthorizedSimpleCollectionDefinitionDto getSimpleCollectionDefinitionWithRoleMetadata(final String userId,
                                                                                        final String collectionId) {
    final AuthorizedSimpleCollectionDefinitionDto simpleCollectionDefinition =
      authorizedCollectionService.getAuthorizedSimpleCollectionDefinitionOrFail(
      userId,
      collectionId
    );

    enrichRoleIdentityMetaData(simpleCollectionDefinition.getDefinitionDto());
    return simpleCollectionDefinition;
  }
}
