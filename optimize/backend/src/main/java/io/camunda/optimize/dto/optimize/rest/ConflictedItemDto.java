/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

import lombok.Getter;
import lombok.experimental.FieldNameConstants;

@Getter
@FieldNameConstants
public class ConflictedItemDto {

  private String id;
  private ConflictedItemType type;
  private String name;

  public ConflictedItemDto() {
  }

  public ConflictedItemDto(final String id, final ConflictedItemType type, final String name) {
    this.id = id;
    this.type = type;
    this.name = name;
  }
}
