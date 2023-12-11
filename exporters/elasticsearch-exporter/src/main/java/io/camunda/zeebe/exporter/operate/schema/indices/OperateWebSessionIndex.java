/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
 * contributor license agreements. Licensed under a proprietary license. See the License.txt file
 * for more information. You may not use this file except in compliance with the proprietary
 * license.
 */
package io.camunda.zeebe.exporter.operate.schema.indices;

import io.camunda.operate.schema.backup.Prio4Backup;

public class OperateWebSessionIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String ID = "id";
  public static final String CREATION_TIME = "creationTime";
  public static final String LAST_ACCESSED_TIME = "lastAccessedTime";
  public static final String MAX_INACTIVE_INTERVAL_IN_SECONDS = "maxInactiveIntervalInSeconds";
  public static final String ATTRIBUTES = "attributes";

  public static final String INDEX_NAME = "web-session";

  public OperateWebSessionIndex(String indexPrefix) {
    super(indexPrefix);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "1.1.0";
  }
}
