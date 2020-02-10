/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.DefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@AllArgsConstructor
public class CollectionRoleService {
  private final AuthorizedCollectionService authorizedCollectionService;
  private final DefinitionAuthorizationService definitionAuthorizationService;
  private final CollectionWriter collectionWriter;
  private final IdentityService identityService;

  public CollectionRoleDto addRoleToCollection(final String userId,
                                               final String collectionId,
                                               final CollectionRoleDto roleDto) throws
                                                                                OptimizeCollectionConflictException {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    if (!identityService.doesIdentityExist(roleDto.getIdentity())) {
      throw new OptimizeValidationException(
        String.format(
          "%s with id %s does not exist in Optimize",
          roleDto.getIdentity().getType(),
          roleDto.getIdentity().getId()
        )
      );
    }
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

  public List<CollectionRoleRestDto> getAllRolesOfCollectionSorted(String userId, String collectionId) {
    AuthorizedCollectionDefinitionDto authCollectionDto =
      authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);

    List<CollectionRoleRestDto> roles = new ArrayList<>();
    authCollectionDto.getDefinitionDto()
      .getData()
      .getRoles()
      .forEach(role -> roles.add(mapRoleDtoToRoleRestDto(role)));

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

  private CollectionRoleRestDto mapRoleDtoToRoleRestDto(final CollectionRoleDto roleDto) {
    return identityService.resolveToIdentityWithMetadata(roleDto.getIdentity())
      .map(identityDto -> new CollectionRoleRestDto(identityDto, roleDto.getRole()))
      .orElseThrow(() -> new OptimizeRuntimeException(
        "Could not map CollectionRoleDto to CollectionRoleRestDto, identity ["
          + roleDto.getIdentity().toString() + "] could not be found."
      ));
  }

}
