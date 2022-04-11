/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.relations;

import org.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
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

  public Set<ConflictedItemDto> getConflictedItemsForDelete(CollectionDefinitionDto definition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (CollectionReferencingService referencingService : referenceServices) {
      conflictedItems.addAll(referencingService.getConflictedItemsForCollectionDelete(definition));
    }
    return conflictedItems;
  }

  public void handleDeleted(CollectionDefinitionDto definition) {
    for (CollectionReferencingService referencingService : referenceServices) {
      referencingService.handleCollectionDeleted(definition);
    }
  }
}
