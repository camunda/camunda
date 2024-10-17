/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import io.camunda.optimize.dto.optimize.IdentityType;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.entity.EntityData;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityType;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class CollectionDefinitionDto extends BaseCollectionDefinitionDto<CollectionDataDto> {

  public CollectionDefinitionDto(
      final CollectionDataDto data,
      final OffsetDateTime created,
      final String id,
      final String name,
      final OffsetDateTime lastModified,
      final String lastModifier,
      final String owner) {
    super();
    this.data = data;
    this.created = created;
    this.id = id;
    this.name = name;
    this.lastModified = lastModified;
    this.lastModifier = lastModifier;
    this.owner = owner;
    automaticallyCreated = false;
  }

  public CollectionDefinitionDto() {}

  public EntityResponseDto toEntityDto(final RoleType roleType) {
    return new EntityResponseDto(
        getId(),
        getName(),
        null,
        getLastModified(),
        getCreated(),
        getOwner(),
        getLastModifier(),
        EntityType.COLLECTION,
        getEntityDtoData(),
        roleType);
  }

  private EntityData getEntityDtoData() {
    return new EntityData(getSubEntityCounts(), getRoleCounts());
  }

  private Map<EntityType, Long> getSubEntityCounts() {
    // not available from the dto itself
    return new HashMap<>();
  }

  private Map<IdentityType, Long> getRoleCounts() {
    final Map<IdentityType, Long> result =
        Optional.ofNullable(data)
            .map(CollectionDataDto::getRoles)
            .map(
                roles ->
                    roles.stream()
                        .collect(
                            groupingBy(roleDto -> roleDto.getIdentity().getType(), counting())))
            .orElseGet(HashMap::new);
    Stream.of(IdentityType.values()).forEach(identityType -> result.putIfAbsent(identityType, 0L));
    return result;
  }
}
