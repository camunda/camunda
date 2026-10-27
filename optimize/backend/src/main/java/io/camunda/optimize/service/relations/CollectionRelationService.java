/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.relations;

import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.rest.ConflictedItemDto;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class CollectionRelationService {

  private final List<CollectionReferencingService> referenceServices;

  @Lazy
  public CollectionRelationService(final List<CollectionReferencingService> referenceServices) {
    this.referenceServices = referenceServices;
  }

  public Set<ConflictedItemDto> getConflictedItemsForDelete(
      final CollectionDefinitionDto definition) {
    final Set<ConflictedItemDto> conflictedItems = new LinkedHashSet<>();
    for (final CollectionReferencingService referencingService : referenceServices) {
      conflictedItems.addAll(referencingService.getConflictedItemsForCollectionDelete(definition));
    }
    return conflictedItems;
  }

  public void handleDeleted(final CollectionDefinitionDto definition) {
    for (final CollectionReferencingService referencingService : referenceServices) {
      referencingService.handleCollectionDeleted(definition);
    }
  }
}
