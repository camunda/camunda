/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.relations;

import org.camunda.optimize.dto.optimize.query.collection.SimpleCollectionDefinitionDto;
import org.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class CollectionRelationService {

  private final List<CollectionReferencingService> referenceServices;

  @Lazy
  public CollectionRelationService(final List<CollectionReferencingService> referenceServices) {
    this.referenceServices = referenceServices;
  }

  public Set<ConflictedItemDto> getConflictedItemsForDelete(SimpleCollectionDefinitionDto definition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (CollectionReferencingService referencingService : referenceServices) {
      conflictedItems.addAll(referencingService.getConflictedItemsForCollectionDelete(definition));
    }
    return conflictedItems;
  }

  public void handleDeleted(SimpleCollectionDefinitionDto definition) {
    for (CollectionReferencingService referencingService : referenceServices) {
      referencingService.handleCollectionDeleted(definition);
    }
  }
}
