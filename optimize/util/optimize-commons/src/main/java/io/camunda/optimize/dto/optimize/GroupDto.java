/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class GroupDto extends IdentityWithMetadataResponseDto {

  private Long memberCount;

  public GroupDto(final String id) {
    this(id, null);
  }

  public GroupDto(final String id, final String name) {
    this(id, name, null);
  }

  public GroupDto(final String id, final String name, final Long memberCount) {
    super(id, IdentityType.GROUP, Optional.ofNullable(name).orElse(id));
    if (id == null) {
      throw new OptimizeRuntimeException("id is null");
    }

    this.memberCount = memberCount;
  }

  protected GroupDto() {}

  public Long getMemberCount() {
    return memberCount;
  }

  public void setMemberCount(final Long memberCount) {
    this.memberCount = memberCount;
  }

  @Override
  @JsonIgnore
  public List<Supplier<String>> getSearchableDtoFields() {
    return List.of(this::getId, this::getName);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof GroupDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof GroupDto)) {
      return false;
    }
    final GroupDto other = (GroupDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "GroupDto(super=" + super.toString() + ", memberCount=" + getMemberCount() + ")";
  }

  public static final class Fields {

    public static final String memberCount = "memberCount";
  }
}
