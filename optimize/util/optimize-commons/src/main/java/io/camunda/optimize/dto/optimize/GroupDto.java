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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "GroupDto(super=" + super.toString() + ", memberCount=" + getMemberCount() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String memberCount = "memberCount";
  }
}
