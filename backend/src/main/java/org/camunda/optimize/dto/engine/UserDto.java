/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.engine;

import lombok.Data;

@Data
public class UserDto {
  private String firstName;
  private String lastName;
  private String displayName;

  private String id;
}
