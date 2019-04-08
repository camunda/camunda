/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.rest;

public class ConflictedItemDto {
  private String id;
  private ConflictedItemType type;
  private String name;

  public ConflictedItemDto() {
  }

  public ConflictedItemDto(String id, ConflictedItemType type, String name) {
    this.id = id;
    this.type = type;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public ConflictedItemType getType() {
    return type;
  }

  public String getName() {
    return name;
  }
}
