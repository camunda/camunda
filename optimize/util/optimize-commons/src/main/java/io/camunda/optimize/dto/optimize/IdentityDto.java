/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import lombok.Data;
import lombok.NonNull;

@Data
public class IdentityDto {

  @NonNull private String id;
  private IdentityType type;

  public IdentityDto(@NonNull String id, IdentityType type) {
    this.id = id;
    this.type = type;
  }

  protected IdentityDto() {}

  public static final class Fields {

    public static final String id = "id";
    public static final String type = "type";
  }
}
