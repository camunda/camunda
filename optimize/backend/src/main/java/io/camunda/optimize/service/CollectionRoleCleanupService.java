/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service;

import io.camunda.optimize.dto.optimize.IdentityDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDataDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionRoleRequestDto;
import io.camunda.optimize.service.db.reader.CollectionReader;
import io.camunda.optimize.service.db.writer.CollectionWriter;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import io.camunda.optimize.service.identity.IdentityCacheSyncListener;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CamundaPlatformCondition;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@AllArgsConstructor
@Slf4j
@Component
@Conditional(CamundaPlatformCondition.class)
public class CollectionRoleCleanupService implements IdentityCacheSyncListener {

  private final CollectionReader collectionReader;
  private final CollectionWriter collectionWriter;
  private final ConfigurationService configurationService;

  @Override
  public void onFinishIdentitySync(final SearchableIdentityCache newIdentityCache) {
    if (configurationService.getUserIdentityCacheConfiguration().isCollectionRoleCleanupEnabled()) {
      if (newIdentityCache.getSize() > 0) {
        final List<CollectionDefinitionDto> allCollections = collectionReader.getAllCollections();
        for (final CollectionDefinitionDto collection : allCollections) {
          final Set<String> roleIdsToRemove =
              collectNonExistingIdentityRoleIds(newIdentityCache, collection);
          if (!roleIdsToRemove.isEmpty()) {
            removeRolesFromCollections(collection.getId(), roleIdsToRemove);
          }
        }
      } else {
        log.info("Identity cache is empty, will thus not perform collection role cleanup.");
      }
    } else {
      log.info("Collection role cleanup not enabled.");
    }
  }

  private Set<String> collectNonExistingIdentityRoleIds(
      final SearchableIdentityCache newIdentityCache, final CollectionDefinitionDto collection) {
    final Set<String> invalidIdentities = new HashSet<>();
    final CollectionDataDto collectionData = collection.getData();
    for (CollectionRoleRequestDto role : collectionData.getRoles()) {
      final IdentityDto roleIdentity = role.getIdentity();
      switch (roleIdentity.getType()) {
        case GROUP:
          if (newIdentityCache.getGroupIdentityById(roleIdentity.getId()).isEmpty()) {
            invalidIdentities.add(role.getId());
          }
          break;
        case USER:
          if (newIdentityCache.getUserIdentityById(roleIdentity.getId()).isEmpty()) {
            invalidIdentities.add(role.getId());
          }
          break;
        default:
          throw new OptimizeRuntimeException(
              "Unsupported identity type: " + roleIdentity.getType());
      }
    }
    return invalidIdentities;
  }

  private void removeRolesFromCollections(
      final String collectionId, final Set<String> roleIdsToRemove) {
    for (final String roleId : roleIdsToRemove) {
      try {
        log.info("Removing role with ID [{}] from collection with ID [{}].", roleId, collectionId);
        collectionWriter.removeRoleFromCollection(collectionId, roleId);
      } catch (Exception ex) {
        log.error(
            "Could not remove role with ID [{}] from collection with ID [{}]",
            roleId,
            collectionId);
      }
    }
  }
}
