/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.indices;

import io.camunda.operate.schema.backup.Prio1Backup;
import org.springframework.stereotype.Component;

@Component
public class ImportPositionIndex extends AbstractIndexDescriptor implements Prio1Backup {

  public static final String INDEX_NAME = "import-position";
  public static final String ALIAS_NAME = "aliasName";
  public static final String ID = "id";
  public static final String POSITION = "position";
  public static final String SEQUENCE = "sequence";
  public static final String FIELD_INDEX_NAME = "indexName";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.2.0";
  }

}
