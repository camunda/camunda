/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.collection;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.UserDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleResponseDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleUpdateRequestDto;
import io.camunda.optimize.dto.optimize.rest.AuthorizedCollectionDefinitionDto;
import io.camunda.optimize.rest.exceptions.NotFoundException;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.db.writer.CollectionWriter;
import io.camunda.optimize.service.exceptions.OptimizeUserOrGroupIdNotFoundException;
import io.camunda.optimize.service.exceptions.OptimizeValidationException;
import io.camunda.optimize.service.exceptions.conflict.OptimizeCollectionConflictException;
import io.camunda.optimize.service.identity.AbstractIdentityService;
import io.camunda.optimize.service.security.AuthorizedCollectionService;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class CollectionRoleService {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(CollectionRoleService.class);
  private final AuthorizedCollectionService authorizedCollectionService;
  private final CollectionWriter collectionWriter;
  private final CollectionReader collectionReader;
  private final AbstractIdentityService identityService;

  public CollectionRoleService(
      final AuthorizedCollectionService authorizedCollectionService,
      final CollectionWriter collectionWriter,
      final CollectionReader collectionReader,
      final AbstractIdentityService identityService) {
    this.authorizedCollectionService = authorizedCollectionService;
    this.collectionWriter = collectionWriter;
    this.collectionReader = collectionReader;
    this.identityService = identityService;
  }

  public void addRolesToCollection(
      final String userId,
      final String collectionId,
      final List<CollectionRoleRequestDto> rolesToAdd) {
    authorizedCollectionService.getAuthorizedCollectionAndVerifyUserAuthorizedToManageOrFail(
        userId, collectionId);
    final List<CollectionRoleRequestDto> resolvedRolesToAdd =
        validateAndResolveIdentities(userId, rolesToAdd);
    collectionWriter.addRoleToCollection(collectionId, resolvedRolesToAdd, userId);
  }

  private List<CollectionRoleRequestDto> validateAndResolveIdentities(
      final String userId, final List<CollectionRoleRequestDto> rolesToAdd) {
    return rolesToAdd.stream()
        .map(
            roleData -> {
              enrichWithIdentityIfMissing(roleData);
              validateIdentity(roleData);
              identityService.validateUserAuthorizedToAccessRoleOrFail(
                  userId, roleData.getIdentity());
              return roleData;
            })
        .collect(Collectors.toList());
  }

  private void enrichWithIdentityIfMissing(final CollectionRoleRequestDto role) {
    final IdentityDto requestIdentityDto = role.getIdentity();
    if (requestIdentityDto.getType() == null) {
      final IdentityDto resolvedIdentityDto =
          identityService
              .getIdentityWithMetadataForId(requestIdentityDto.getId())
              .orElseThrow(
                  () ->
                      new OptimizeUserOrGroupIdNotFoundException(
                          String.format(
                              "No user or group with ID %s exists in Optimize.",
                              requestIdentityDto.getId())))
              .toIdentityDto();
      role.setIdentity(resolvedIdentityDto);
    }
  }

  private void validateIdentity(final CollectionRoleRequestDto role) {
    if (!identityService.doesIdentityExist(role.getIdentity())) {
      throw new OptimizeValidationException(
          String.format(
              "%s with id %s does not exist in Optimize",
              role.getIdentity().getType(), role.getIdentity().getId()));
    }
  }

  public void updateRoleOfCollection(
      final String userId,
      final String collectionId,
      final String roleEntryId,
      final CollectionRoleUpdateRequestDto roleUpdateDto) {
    collectionWriter.updateRoleInCollection(collectionId, roleEntryId, roleUpdateDto, userId);
  }

  public void removeRoleFromCollectionUnlessIsLastManager(
      final String userId, final String collectionId, final String roleEntryId) {
    collectionWriter.removeRoleFromCollectionUnlessIsLastManager(collectionId, roleEntryId, userId);
  }

  public void removeRolesFromCollection(
      final String userId, final String collectionId, final List<String> roleEntryIds) {
    verifyCollectionExists(collectionId);
    roleEntryIds.forEach(
        roleEntryId ->
            authorizedCollectionService.verifyUserAuthorizedToEditCollectionRole(
                userId, collectionId, roleEntryId));
    for (final String roleId : roleEntryIds) {
      try {
        collectionWriter.removeRoleFromCollectionUnlessIsLastManager(collectionId, roleId, userId);
      } catch (final NotFoundException e) {
        LOG.debug("Could not delete role with id {}. The role is already deleted.", roleId);
      } catch (final OptimizeCollectionConflictException e) {
        LOG.debug(
            "Could not delete role with id {}, because the user with that id is a manager.",
            roleId);
      }
    }
  }

  private void verifyCollectionExists(final String collectionId) {
    final Optional<CollectionDefinitionDto> collectionDefinition =
        collectionReader.getCollection(collectionId);
    if (collectionDefinition.isEmpty()) {
      LOG.error(
          "Was not able to retrieve collection with id [{}] from the database.", collectionId);
      throw new NotFoundException(
          "Collection does not exist! Tried to retrieve collection with id " + collectionId);
    }
  }

  public List<CollectionRoleResponseDto> getAllRolesOfCollectionSorted(
      final String userId, final String collectionId) {
    final AuthorizedCollectionDefinitionDto authCollectionDto =
        authorizedCollectionService.getAuthorizedCollectionDefinitionOrFail(userId, collectionId);

    return authCollectionDto.getDefinitionDto().getData().getRoles().stream()
        .filter(
            role -> identityService.isUserAuthorizedToAccessIdentity(userId, role.getIdentity()))
        .map(
            roleDto ->
                identityService
                    .getIdentityWithMetadataForId(roleDto.getIdentity().getId())
                    .map(identity -> CollectionRoleResponseDto.from(roleDto, identity))
                    .orElseGet(
                        () -> {
                          LOG.info(
                              "Identity with id {} is present in roles but does not exist anymore.",
                              roleDto.getId());
                          return CollectionRoleResponseDto.from(
                              roleDto, new UserDto(roleDto.getIdentity().getId()));
                        }))
        .sorted()
        .toList();
  }
}
