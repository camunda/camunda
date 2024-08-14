/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;

@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
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

  @Override
  @JsonIgnore
  public List<Supplier<String>> getSearchableDtoFields() {
    return List.of(this::getId, this::getName);
  }

  public static final class Fields {

    public static final String memberCount = "memberCount";
  }
}
