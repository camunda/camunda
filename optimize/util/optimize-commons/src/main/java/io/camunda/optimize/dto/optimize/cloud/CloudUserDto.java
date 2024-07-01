/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.cloud;

import java.util.List;
import java.util.function.Supplier;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CloudUserDto {

  private String userId;
  private String name;
  private String email;
  private List<String> roles;

  public List<Supplier<String>> getSearchableDtoFields() {
    return List.of(this::getUserId, this::getName, this::getEmail);
  }
}
