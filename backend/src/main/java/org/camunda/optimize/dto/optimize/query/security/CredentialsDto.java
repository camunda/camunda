/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.query.security;

import lombok.Data;

import java.io.Serializable;

@Data
public class CredentialsDto implements Serializable {

  protected String username;
  protected String password;
}
