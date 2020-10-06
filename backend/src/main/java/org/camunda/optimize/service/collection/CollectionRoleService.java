/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionScopeEntryDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import org.camunda.optimize.service.IdentityService;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeConflictException;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.EngineDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Component
@AllArgsConstructor
public class CollectionRoleService {
  private final AuthorizedCollectionService authorizedCollectionService;
  private final EngineDefinitionAuthorizationService definitionAuthorizationService;
  private final CollectionWriter collectionWriter;
  private final IdentityService identityService;

  public void addRolesToCollection(final String userId,
                                   final String collectionId,
                                   final List<CollectionRoleDto> rolesToAdd) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    final List<CollectionRoleDto> resolvedRolesToAdd = validateAndResolveIdentities(userId, rolesToAdd);
    collectionWriter.addRoleToCollection(collectionId, resolvedRolesToAdd, userId);
  }

  private List<CollectionRoleDto> validateAndResolveIdentities(final String userId,
                                                               List<CollectionRoleDto> rolesToAdd) {
    return rolesToAdd.stream()
      .map(roleData -> {
        enrichWithIdentityIfMissing(roleData);
        validateIdentity(roleData);
        identityService.validateUserAuthorizedToAccessRoleOrFail(userId, roleData.getIdentity());
        return roleData;
      })
      .collect(Collectors.toList());
  }

  private void enrichWithIdentityIfMissing(CollectionRoleDto role) {
    final IdentityDto requestIdentityDto = role.getIdentity();
    if (requestIdentityDto.getType() == null) {
      final IdentityDto resolvedIdentityDto =
        identityService.getIdentityWithMetadataForId(requestIdentityDto.getId())
          .orElseThrow(
            () -> new OptimizeUserOrGroupIdNotFoundException(
              String.format("No user or group with ID %s exists in Optimize.", requestIdentityDto.getId())
            )
          )
          .toIdentityDto();
      role.setIdentity(resolvedIdentityDto);
    }
  }

  private void validateIdentity(final CollectionRoleDto role) {
    if (!identityService.doesIdentityExist(role.getIdentity())) {
      throw new OptimizeValidationException(
        String.format(
          "%s with id %s does not exist in Optimize",
          role.getIdentity().getType(),
          role.getIdentity().getId()
        )
      );
    }
  }

  public void updateRoleOfCollection(final String userId,
                                     final String collectionId,
                                     final String roleEntryId,
                                     final CollectionRoleUpdateDto roleUpdateDto) throws OptimizeConflictException {
    collectionWriter.updateRoleInCollection(collectionId, roleEntryId, roleUpdateDto, userId);
  }

  public void removeRoleFromCollectionUnlessIsLastManager(String userId, String collectionId, String roleEntryId)
    throws OptimizeConflictException {
    collectionWriter.removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, userId);
  }

  public List<CollectionRoleRestDto> getAllRolesOfCollectionSorted(String userId, String collectionId) {
    AuthorizedCollectionDefinitionDto authCollectionDto =
      authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);

    List<CollectionRoleRestDto> roles = authCollectionDto.getDefinitionDto()
      .getData()
      .getRoles()
      .stream()
      .filter(role -> identityService.isUserAuthorizedToAccessIdentity(userId, role.getIdentity()))
      .map(this::mapRoleDtoToRoleRestDto)
      .collect(toList());

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
