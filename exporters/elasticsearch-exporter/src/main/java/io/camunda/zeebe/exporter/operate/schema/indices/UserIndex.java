/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
 * contributor license agreements. Licensed under a proprietary license. See the License.txt file
 * for more information. You may not use this file except in compliance with the proprietary
 * license.
 */
package io.camunda.zeebe.exporter.operate.schema.indices;

import io.camunda.operate.schema.backup.Prio4Backup;

public class UserIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "user";
  public static final String ID = "id";
  public static final String USER_ID = "userId";
  public static final String PASSWORD = "password";
  public static final String ROLES = "roles";
  public static final String DISPLAY_NAME = "displayName";

  public UserIndex(String indexPrefix) {
    super(indexPrefix);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "1.2.0";
  }
}
