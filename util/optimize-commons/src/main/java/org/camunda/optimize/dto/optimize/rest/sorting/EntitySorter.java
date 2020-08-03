/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import com.google.common.collect.ImmutableMap;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.BadRequestException;
import java.util.Comparator;
import java.util.List;

import static org.camunda.optimize.dto.optimize.query.entity.EntityDto.Fields.entityType;
import static org.camunda.optimize.dto.optimize.query.entity.EntityDto.Fields.lastModified;
import static org.camunda.optimize.dto.optimize.query.entity.EntityDto.Fields.lastModifier;
import static org.camunda.optimize.dto.optimize.query.entity.EntityDto.Fields.name;

@NoArgsConstructor
public class EntitySorter extends Sorter<EntityDto> {

  private static final Comparator<EntityDto> DEFAULT_ENTITY_COMPARATOR =
    Comparator.comparing(EntityDto::getEntityType)
      .thenComparing(Comparator.comparing(EntityDto::getLastModified).reversed());

  private static final ImmutableMap<String, Comparator<EntityDto>> sortComparators = ImmutableMap.of(
    name.toLowerCase(), Comparator.comparing(EntityDto::getName),
    entityType.toLowerCase(), Comparator.comparing(EntityDto::getEntityType),
    lastModified.toLowerCase(), Comparator.comparing(EntityDto::getLastModified),
    lastModifier.toLowerCase(), Comparator.comparing(EntityDto::getLastModifier)
  );

  @Override
  public List<EntityDto> applySort(List<EntityDto> entities) {
    Comparator<EntityDto> entitySorter;
    if (sortBy != null) {
      if (!sortComparators.containsKey(sortBy.toLowerCase())) {
        throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
      }
      Comparator<EntityDto> entityDtoComparator = sortComparators.get(sortBy.toLowerCase());
      if (SortOrder.DESC.equals(sortOrder)) {
        entityDtoComparator = entityDtoComparator.reversed();
      }
      entitySorter = entityDtoComparator.thenComparing(DEFAULT_ENTITY_COMPARATOR);
    } else {
      if (sortOrder != null) {
        throw new BadRequestException("Sort order is not supported when no field selected to sort");
      }
      entitySorter = DEFAULT_ENTITY_COMPARATOR;
    }
    entities.sort(entitySorter);
    return entities;
  }
}
