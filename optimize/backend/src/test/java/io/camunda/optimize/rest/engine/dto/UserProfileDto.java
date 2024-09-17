/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.rest.engine.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileDto {

  protected String id;
  protected String firstName;
  protected String lastName;
  protected String email;

  public UserProfileDto(String id, String firstName, String lastName, String email) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
  }

  protected UserProfileDto() {}
}
