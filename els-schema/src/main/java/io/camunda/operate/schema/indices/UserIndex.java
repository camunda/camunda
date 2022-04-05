/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class UserIndex extends AbstractIndexDescriptor {

  public static final String INDEX_NAME = "user";
  public static final String ID = "id";
  public static final String USER_ID = "userId";
  public static final String PASSWORD = "password";
  public static final String ROLES = "roles";
  public static final String DISPLAY_NAME = "displayName";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "1.2.0";
  }
}
