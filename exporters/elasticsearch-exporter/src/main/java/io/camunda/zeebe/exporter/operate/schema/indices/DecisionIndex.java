/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more
 * contributor license agreements. Licensed under a proprietary license. See the License.txt file
 * for more information. You may not use this file except in compliance with the proprietary
 * license.
 */
package io.camunda.zeebe.exporter.operate.schema.indices;

import io.camunda.operate.schema.backup.Prio4Backup;

public class DecisionIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "decision";
  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String DECISION_ID = "decisionId";
  public static final String NAME = "name";
  public static final String VERSION = "version";
  public static final String DECISION_REQUIREMENTS_ID = "decisionRequirementsId";
  public static final String DECISION_REQUIREMENTS_KEY = "decisionRequirementsKey";

  public DecisionIndex(String indexPrefix) {
    super(indexPrefix);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.3.0";
  }
}
