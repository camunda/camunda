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
import java.util.Objects;

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
    return Objects.hash(super.hashCode(), definitionDto);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final AuthorizedCollectionDefinitionDto that = (AuthorizedCollectionDefinitionDto) o;
    return Objects.equals(definitionDto, that.definitionDto);
  }

  @Override
  public String toString() {
    return "AuthorizedCollectionDefinitionDto(definitionDto=" + getDefinitionDto() + ")";
  }
}
