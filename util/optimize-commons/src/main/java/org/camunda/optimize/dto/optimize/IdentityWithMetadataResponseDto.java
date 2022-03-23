/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
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
@FieldNameConstants
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public abstract class IdentityWithMetadataResponseDto extends IdentityDto {
  private String name;

  public IdentityWithMetadataResponseDto(final String id, final IdentityType type) {
    this(id, type, null);
  }

  public IdentityWithMetadataResponseDto(final String id, final IdentityType type, final String name) {
    super(id, type);
    this.name = name;
  }

  public IdentityDto toIdentityDto() {
    return new IdentityDto(getId(), getType());
  }
}
