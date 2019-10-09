/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true
)
@JsonSubTypes({
  @JsonSubTypes.Type(value = UserDto.class, name = "user"),
  @JsonSubTypes.Type(value = GroupDto.class, name = "group"),
})
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants(asEnum = true)
public abstract class IdentityDto {
  private String id;
  private IdentityType type;
  protected String name;

  public IdentityDto(final String id, final IdentityType type) {
    this.id = id;
    this.type = type;
  }
}
