/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.camunda.optimize.dto.optimize.AuthorizedEntityDto;
import io.camunda.optimize.dto.optimize.RoleType;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionDto;
import io.camunda.optimize.dto.optimize.query.collection.CollectionDefinitionRestDto;

public class AuthorizedCollectionDefinitionRestDto extends AuthorizedEntityDto {

  @JsonUnwrapped private CollectionDefinitionRestDto definitionDto;

  public AuthorizedCollectionDefinitionRestDto(
      final RoleType currentUserRole, final CollectionDefinitionRestDto definitionDto) {
    super(currentUserRole);
    this.definitionDto = definitionDto;
  }

  protected AuthorizedCollectionDefinitionRestDto() {}

  public static AuthorizedCollectionDefinitionRestDto from(
      final AuthorizedCollectionDefinitionDto authorizedCollectionDto) {

    final CollectionDefinitionDto collectionDefinitionDto =
        authorizedCollectionDto.getDefinitionDto();
    final CollectionDefinitionRestDto resolvedCollection = new CollectionDefinitionRestDto();
    resolvedCollection.setId(collectionDefinitionDto.getId());
    resolvedCollection.setName(collectionDefinitionDto.getName());
    resolvedCollection.setLastModifier(collectionDefinitionDto.getLastModifier());
    resolvedCollection.setOwner(collectionDefinitionDto.getOwner());
    resolvedCollection.setCreated(collectionDefinitionDto.getCreated());
    resolvedCollection.setLastModified(collectionDefinitionDto.getLastModified());
    resolvedCollection.setAutomaticallyCreated(collectionDefinitionDto.isAutomaticallyCreated());

    resolvedCollection.setData(collectionDefinitionDto.getData());
    return new AuthorizedCollectionDefinitionRestDto(
        authorizedCollectionDto.getCurrentUserRole(), resolvedCollection);
  }

  public CollectionDefinitionRestDto getDefinitionDto() {
    return definitionDto;
  }

  @JsonUnwrapped
  public void setDefinitionDto(final CollectionDefinitionRestDto definitionDto) {
    this.definitionDto = definitionDto;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof AuthorizedCollectionDefinitionRestDto;
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
    return "AuthorizedCollectionDefinitionRestDto(definitionDto=" + getDefinitionDto() + ")";
  }
}
