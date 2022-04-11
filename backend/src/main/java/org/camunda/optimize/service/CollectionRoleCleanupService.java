/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.IdentityDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import org.camunda.optimize.service.es.reader.CollectionReader;
import org.camunda.optimize.service.es.writer.CollectionWriter;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import org.camunda.optimize.service.identity.IdentityCacheSyncListener;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@AllArgsConstructor
@Slf4j
@Component
public class CollectionRoleCleanupService implements IdentityCacheSyncListener {
  private final CollectionReader collectionReader;
  private final CollectionWriter collectionWriter;

  @Override
  public void onFinishIdentitySync(final SearchableIdentityCache newIdentityCache) {
    if (newIdentityCache.getSize() > 0) {
      final List<CollectionDefinitionDto> allCollections = collectionReader.getAllCollections();
      for (final CollectionDefinitionDto collection : allCollections) {
        final Set<String> roleIdsToRemove = collectNonExistingIdentityRoleIds(newIdentityCache, collection);
        if (!roleIdsToRemove.isEmpty()) {
          removeRolesFromCollections(collection.getId(), roleIdsToRemove);
        }
      }
    } else {
      log.info("Identity cache is empty, will thus not perform collection role cleanup.");
    }
  }

  private Set<String> collectNonExistingIdentityRoleIds(final SearchableIdentityCache newIdentityCache,
                                                        final CollectionDefinitionDto collection) {
    final Set<String> invalidIdentities = new HashSet<>();
    final CollectionDataDto collectionData = collection.getData();
    for (CollectionRoleRequestDto role : collectionData.getRoles()) {
      final IdentityDto roleIdentity = role.getIdentity();
      switch (roleIdentity.getType()) {
        case GROUP:
          if (!newIdentityCache.getGroupIdentityById(roleIdentity.getId()).isPresent()) {
            invalidIdentities.add(role.getId());
          }
          break;
        case USER:
          if (!newIdentityCache.getUserIdentityById(roleIdentity.getId()).isPresent()) {
            invalidIdentities.add(role.getId());
          }
          break;
        default:
          throw new OptimizeRuntimeException("Unsupported identity type: " + roleIdentity.getType());
      }
    }
    return invalidIdentities;
  }

  private void removeRolesFromCollections(final String collectionId, final Set<String> roleIdsToRemove) {
    for (final String roleId : roleIdsToRemove) {
      try {
        log.info("Removing role with ID [{}] from collection with ID [{}].", roleId, collectionId);
        collectionWriter.removeRoleFromCollection(collectionId, roleId);
      } catch (Exception ex) {
        log.error("Could not remove role with ID [{}] from collection with ID [{}]", roleId, collectionId);
      }
    }
  }
}
