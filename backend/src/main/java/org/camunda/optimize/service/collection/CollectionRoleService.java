/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.collection;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.UserDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import org.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import org.camunda.optimize.service.db.reader.CollectionReader;
import org.camunda.optimize.service.db.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;
import org.camunda.optimize.service.exceptions.OptimizeValidationException;
import org.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import org.camunda.optimize.service.identity.AbstractIdentityService;
import org.camunda.optimize.service.security.AuthorizedCollectionService;
import org.camunda.optimize.service.security.util.definition.DataSourceDefinitionAuthorizationService;
import org.springframework.stereotype.Component;

import jakarta.ws.rs.NotAuthorizedException;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@AllArgsConstructor
@Slf4j
public class CollectionRoleService {
  private final AuthorizedCollectionService authorizedCollectionService;
  private final DataSourceDefinitionAuthorizationService definitionAuthorizationService;
  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;
  private final AbstractIdentityService identityService;

  public void addRolesToCollection(final String userId,
                                   final String collectionId,
                                   final List<CollectionRoleRequestDto> rolesToAdd) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(userId, collectionId);
    final List<CollectionRoleRequestDto> resolvedRolesToAdd = validateAndResolveIdentities(userId, rolesToAdd);
    collectionWriter.addRoleToCollection(collectionId, resolvedRolesToAdd, userId);
  }

  private List<CollectionRoleRequestDto> validateAndResolveIdentities(final String userId,
                                                                      List<CollectionRoleRequestDto> rolesToAdd) {
    return rolesToAdd.stream()
      .map(roleData -> {
        enrichWithIdentityIfMissing(roleData);
        validateIdentity(roleData);
        identityService.validateUserAuthorizedToAccessRoleOrFail(userId, roleData.getIdentity());
        return roleData;
      })
      .collect(Collectors.toList());
  }

  private void enrichWithIdentityIfMissing(CollectionRoleRequestDto role) {
    final IdentityDto requestIdentityDto = role.getIdentity();
    if (requestIdentityDto.getType() == null) {
      final IdentityDto resolvedIdentityDto =
        identityService.getIdentityWithMetadataForId(requestIdentityDto.getId())
          .orElseThrow(() -> new OptimizeUserOrGroupIdNotFoundException(
                         String.format("No user or group with ID %s exists in Optimize.", requestIdentityDto.getId())
                       )
          )
          .toIdentityDto();
      role.setIdentity(resolvedIdentityDto);
    }
  }

  private void validateIdentity(final CollectionRoleRequestDto role) {
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
                                     final CollectionRoleUpdateRequestDto roleUpdateDto) {
    collectionWriter.updateRoleInCollection(collectionId, roleEntryId, roleUpdateDto, userId);
  }

  public void removeRoleFromCollectionUnlessIsLastManager(String userId, String collectionId, String roleEntryId) {
    collectionWriter.removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, userId);
  }

  public void removeRolesFromCollection(String userId, String collectionId, List<String> roleEntryIds) {
    verifyCollectionExists(collectionId);
    roleEntryIds.forEach(roleEntryId -> authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(
      userId,
      collectionId,
      roleEntryId
    ));
    for (String roleId : roleEntryIds) {
      try {
        collectionWriter.removeRoleFromCollectionUnlessIsLastManager(collectionId, roleId, userId);
      } catch (NotFoundException e) {
        log.debug("Could not delete role with id {}. The role is already deleted.", roleId);
      } catch (OptimizeCollectionConflictException e) {
        log.debug("Could not delete role with id {}, because the user with that id is a manager.", roleId);
      }
    }
  }

  private void verifyCollectionExists(String collectionId) {
    final Optional<CollectionDefinitionDto> collectionDefinition = collectionReader.getCollection(collectionId);
    if (collectionDefinition.isEmpty()) {
      log.error("Was not able to retrieve collection with id [{}] from Elasticsearch.", collectionId);
      throw new NotFoundException("Collection does not exist! Tried to retrieve collection with id " + collectionId);
    }
  }

  public List<CollectionRoleResponseDto> getAllRolesOfCollectionSorted(String userId, String collectionId) {
    AuthorizedCollectionDefinitionDto authCollectionDto =
      authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);

    return authCollectionDto.getDefinitionDto()
      .getData()
      .getRoles()
      .stream()
      .filter(role -> identityService.isUserAuthorizedToAccessIdentity(userId, role.getIdentity()))
      .map(roleDto -> identityService.getIdentityWithMetadataForId(roleDto.getIdentity().getId())
        .map(identity -> CollectionRoleResponseDto.from(roleDto, identity))
        .orElseGet(() -> {
          log.info("Identity with id {} is present in roles but does not exist anymore.", roleDto.getId());
          return CollectionRoleResponseDto.from(roleDto, new UserDto(roleDto.getIdentity().getId()));
        })
      ).sorted().toList();
  }

}
