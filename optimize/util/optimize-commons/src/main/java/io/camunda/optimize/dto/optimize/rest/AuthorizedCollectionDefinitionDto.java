/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.entity.EntityResponseDto;

public class AuthorizedCollectionDefinitionDto extends AuthorizedEntityDto {

  @JsonUnwrapped private CollectionDefinitionDto definitionDto;

  public AuthorizedCollectionDefinitionDto(
      final RoleType currentUserRole, final CollectionDefinitionDto definitionDto) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }

  public AuthorizedCollectionDefinitionDto(final CollectionDefinitionDto definitionDto) {
    this.definitionDto = definitionDto;
  }

  protected AuthorizedCollectionDefinitionDto() {}

  public EntityResponseDto toEntityDto() {
    return definitionDto.toEntityDto(getCurrentUserRole());
  }

  @JsonIgnore
  public RoleType getCollectionResourceRole() {
    switch (getCurrentUserRole()) {
      case EDITOR:
      case MANAGER:
        return RoleType.EDITOR;
      case VIEWER:
      default:
        return RoleType.VIEWER;
    }
  }

  public CollectionDefinitionDto getDefinitionDto() {
    return definitionDto;
  }

  @JsonUnwrapped
  public void setDefinitionDto(final CollectionDefinitionDto definitionDto) {
    this.definitionDto = definitionDto;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedCollectionDefinitionDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $definitionDto = getDefinitionDto();
    result = result * PRIME + ($definitionDto == null ? 43 : $definitionDto.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof AuthorizedCollectionDefinitionDto)) {
      return false;
    }
    final AuthorizedCollectionDefinitionDto other = (AuthorizedCollectionDefinitionDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$definitionDto = getDefinitionDto();
    final Object other$definitionDto = other.getDefinitionDto();
    if (this$definitionDto == null
        ? other$definitionDto != null
        : !this$definitionDto.equals(other$definitionDto)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "AuthorizedCollectionDefinitionDto(definitionDto=" + getDefinitionDto() + ")";
  }
}
