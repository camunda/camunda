/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.schema.indices;

import io.camunda.tasklist.schema.backup.Prio2Backup;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceIndex extends AbstractIndexDescriptor implements Prio2Backup {

  public static final String INDEX_NAME = "process-instance";
  public static final String INDEX_VERSION = "8.3.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String END_DATE = "endDate";
  public static final String TENANT_ID = "tenantId";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }
}
