/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.collection;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Data
public class PartialCollectionDefinitionRequestDto {

  protected String ownerId;
  protected String name;
  protected PartialCollectionDataDto data;

  public PartialCollectionDefinitionRequestDto(final String name) {
    this.name = name;
  }

  public PartialCollectionDefinitionRequestDto(final String name, final String ownerId) {
    this.name = name;
    this.ownerId = ownerId;
  }
}
