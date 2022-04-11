/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.rest.sorting;

import com.google.common.collect.ImmutableMap;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.sorting.SortOrder;

import javax.ws.rs.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static java.util.Comparator.nullsFirst;
import static org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.entityType;
import static org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.lastModified;
import static org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.lastModifier;
import static org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.name;

@NoArgsConstructor
public class EntitySorter extends Sorter<EntityResponseDto> {

  private static final Comparator<EntityResponseDto> DEFAULT_ENTITY_COMPARATOR =
    Comparator.comparing(EntityResponseDto::getEntityType)
      .thenComparing(Comparator.comparing(EntityResponseDto::getLastModified).reversed());

  private static final ImmutableMap<String, Comparator<EntityResponseDto>> sortComparators = ImmutableMap.of(
    name.toLowerCase(), Comparator.comparing(EntityResponseDto::getName, nullsFirst(String.CASE_INSENSITIVE_ORDER)),
    entityType.toLowerCase(), Comparator.comparing(EntityResponseDto::getEntityType),
    lastModified.toLowerCase(), Comparator.comparing(EntityResponseDto::getLastModified),
    lastModifier.toLowerCase(), Comparator.comparing(EntityResponseDto::getLastModifier)
  );

  public EntitySorter(final String sortBy, final SortOrder sortOrder) {
    this.sortRequestDto = new SortRequestDto(sortBy, sortOrder);
  }

  @Override
  public List<EntityResponseDto> applySort(List<EntityResponseDto> entities) {
    Comparator<EntityResponseDto> entitySorter;
    final Optional<SortOrder> sortOrderOpt = getSortOrder();
    final Optional<String> sortByOpt = getSortBy();
    if (sortByOpt.isPresent()) {
      final String sortBy = sortByOpt.get();
      if (!sortComparators.containsKey(sortBy.toLowerCase())) {
        throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
      }
      Comparator<EntityResponseDto> entityDtoComparator = sortComparators.get(sortBy.toLowerCase());
      if (sortOrderOpt.isPresent() && SortOrder.DESC.equals(sortOrderOpt.get())) {
        entityDtoComparator = entityDtoComparator.reversed();
      }
      entitySorter = entityDtoComparator.thenComparing(DEFAULT_ENTITY_COMPARATOR);
    } else {
      if (sortOrderOpt.isPresent()) {
        throw new BadRequestException("Sort order is not supported when no field selected to sort");
      }
      entitySorter = DEFAULT_ENTITY_COMPARATOR;
    }
    entities.sort(entitySorter);
    return entities;
  }
}
