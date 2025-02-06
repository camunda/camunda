/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest.sorting;

import static io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.entityType;
import static io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.lastModified;
import static io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.lastModifier;
import static io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto.Fields.name;
import static java.util.Comparator.nullsFirst;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.sorting.SortOrder;
import io.camunda.optimize.rest.exceptions.BadRequestException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class EntitySorter extends Sorter<EntityResponseDto> {

  private static final Comparator<EntityResponseDto> DEFAULT_ENTITY_COMPARATOR =
      Comparator.comparing(EntityResponseDto::getEntityType)
          .thenComparing(Comparator.comparing(EntityResponseDto::getLastModified).reversed());

  private static final ImmutableMap<String, Comparator<EntityResponseDto>> SORT_COMPARATORS =
      ImmutableMap.of(
          name.toLowerCase(Locale.ENGLISH),
          Comparator.comparing(
              EntityResponseDto::getName, nullsFirst(String.CASE_INSENSITIVE_ORDER)),
          entityType.toLowerCase(Locale.ENGLISH),
          Comparator.comparing(EntityResponseDto::getEntityType),
          lastModified.toLowerCase(Locale.ENGLISH),
          Comparator.comparing(EntityResponseDto::getLastModified),
          lastModifier.toLowerCase(Locale.ENGLISH),
          Comparator.comparing(EntityResponseDto::getLastModifier));

  public EntitySorter(final String sortBy, final SortOrder sortOrder) {
    sortRequestDto = new SortRequestDto(sortBy, sortOrder);
  }

  public EntitySorter() {}

  @Override
  public List<EntityResponseDto> applySort(final List<EntityResponseDto> entities) {
    final Comparator<EntityResponseDto> entitySorter;
    final Optional<SortOrder> sortOrderOpt = getSortOrder();
    final Optional<String> sortByOpt = getSortBy();
    if (sortByOpt.isPresent()) {
      final String sortBy = sortByOpt.get();
      if (!SORT_COMPARATORS.containsKey(sortBy.toLowerCase(Locale.ENGLISH))) {
        throw new BadRequestException(String.format("%s is not a sortable field", sortBy));
      }
      Comparator<EntityResponseDto> entityDtoComparator =
          SORT_COMPARATORS.get(sortBy.toLowerCase(Locale.ENGLISH));
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
