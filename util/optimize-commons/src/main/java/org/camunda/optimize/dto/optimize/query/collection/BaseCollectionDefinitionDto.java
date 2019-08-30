/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.collection;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.IdentityType;
import org.camunda.optimize.dto.optimize.query.entity.EntityData;
import org.camunda.optimize.dto.optimize.query.entity.EntityDto;
import org.camunda.optimize.dto.optimize.query.entity.EntityType;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@Data
@FieldNameConstants(asEnum = true)
public class BaseCollectionDefinitionDto<DATA_TYPE extends CollectionDataDto> {

  protected String id;
  protected String name;
  protected OffsetDateTime lastModified;
  protected OffsetDateTime created;
  protected String owner;
  protected String lastModifier;
  protected DATA_TYPE data;

  public EntityDto toEntityDto() {
    return new EntityDto(
      getId(),
      getName(),
      getLastModified(),
      getCreated(),
      getOwner(),
      getLastModifier(),
      EntityType.COLLECTION,
      getEntityDtoData()
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
