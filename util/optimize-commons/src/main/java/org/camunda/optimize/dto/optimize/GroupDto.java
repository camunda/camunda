/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

import java.util.Optional;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants
@ToString(callSuper = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
public class GroupDto extends IdentityWithMetadataResponseDto {
  private Long memberCount;

  public GroupDto(final String id) {
    this(id, null);
  }

  public GroupDto(final String id, final String name) {
    this(id, name, null);
  }

  public GroupDto(@NonNull final String id, final String name, final Long memberCount) {
    super(id, IdentityType.GROUP, Optional.ofNullable(name).orElse(id));
    this.memberCount = memberCount;
  }
}
