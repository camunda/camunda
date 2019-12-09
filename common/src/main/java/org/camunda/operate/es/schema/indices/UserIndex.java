/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.es.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class UserIndex extends AbstractIndexDescriptor {

  private static final String INDEX_NAME = "user";
  public static final String ID = "id";
  public static final String USERNAME = "username";
  public static final String PASSWORD = "password";
  public static final String ROLE = "role";

  @Override
  protected String getMainIndexName() {
    return INDEX_NAME;
  }

}