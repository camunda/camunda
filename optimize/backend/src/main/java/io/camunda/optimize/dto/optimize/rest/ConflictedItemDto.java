/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.rest;

public class ConflictedItemDto {

  private String id;
  private ConflictedItemType type;
  private String name;

  public ConflictedItemDto() {}

  public ConflictedItemDto(final String id, final ConflictedItemType type, final String name) {
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

  public static final class Fields {

    public static final String id = "id";
    public static final String type = "type";
    public static final String name = "name";
  }
}
