/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class UserIndex extends AbstractIndexDescriptor {

  public static final String ID = "id";
  public static final String USERNAME = "username";
  public static final String PASSWORD = "password";
  public static final String FIRSTNAME = "firstname";
  public static final String LASTNAME = "lastname";
  public static final String ROLE = "role";
  private static final String INDEX_NAME = "user";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }
}
