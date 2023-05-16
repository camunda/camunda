/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.RoleType;
import org.camunda.optimize.dto.optimize.query.entity.EntityData;
import org.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@NoArgsConstructor
public class CollectionDefinitionDto extends BaseCollectionDefinitionDto<CollectionDataDto> {

  public CollectionDefinitionDto(CollectionDataDto data, OffsetDateTime created, String id, String name,
                                 OffsetDateTime lastModified, String lastModifier, String owner) {
    super();
    this.data = data;
    this.created = created;
    this.id = id;
    this.name = name;
    this.lastModified = lastModified;
    this.lastModifier = lastModifier;
    this.owner = owner;
    this.automaticallyCreated = false;
  }

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
      roleType
    );
  }

  private EntityData getEntityDtoData() {
    return new EntityData(getSubEntityCounts(), getRoleCounts());
  }

  private Map<EntityType, Long> getSubEntityCounts() {
    // not available from the dto itself
    return new HashMap<>();
  }

  private Map<IdentityType, Long> getRoleCounts() {
    final Map<IdentityType, Long> result = Optional.ofNullable(data)
      .map(CollectionDataDto::getRoles)
      .map(roles -> roles.stream().collect(groupingBy(roleDto -> roleDto.getIdentity().getType(), counting())))
      .orElseGet(HashMap::new);
    Stream.of(IdentityType.values()).forEach(identityType -> result.putIfAbsent(identityType, 0L));
    return result;
  }
}
