/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Slf4j
@Component
public class CollectionRoleCleanupService implements SyncedIdentityCacheListener {
  private final CollectionReader collectionReader;
  private final CollectionWriter collectionWriter;

  @Override
  public void onFinishIdentitySync(final SearchableIdentityCache newIdentityCache) {
    List<CollectionDefinitionDto> allCollections = collectionReader.getAllCollections();
    Map<String, HashSet<String>> rolesToRemove = new HashMap<>();

    for (CollectionDefinitionDto collection : allCollections) {
      rolesToRemove.put(collection.getId(), new HashSet<>());
      final CollectionDataDto collectionData = collection.getData();
      for (CollectionRoleRequestDto role : collectionData.getRoles()) {
        final IdentityDto roleIdentity = role.getIdentity();
        switch (roleIdentity.getType()) {
          case GROUP:
            if (!newIdentityCache.getGroupIdentityById(roleIdentity.getId()).isPresent()) {
              rolesToRemove.get(collection.getId()).add(role.getId());
            }
            break;
          case USER:
            if (!newIdentityCache.getUserIdentityById(roleIdentity.getId()).isPresent()) {
              rolesToRemove.get(collection.getId()).add(role.getId());
            }
            break;
          default:
            throw new OptimizeRuntimeException("Unsupported identity type: " + roleIdentity.getType());
        }
      }

      for (Map.Entry<String, HashSet<String>> e : rolesToRemove.entrySet()) {
        final String collectionId = e.getKey();
        final HashSet<String> roleIds = e.getValue();
        for (String roleId : roleIds) {
          try {
            log.info("Removing role with ID [{}] from collection with ID [{}].", roleId, collectionId);
            collectionWriter.removeRoleFromCollection(collectionId, roleId);
          } catch (Exception ex) {
            log.error("Could not remove role with ID [{}] from collection with ID [{}]", roleId, collectionId);
          }
        }
      }
    }
  }
}
