/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.indices;

import org.springframework.stereotype.Component;

@Component
public class DecisionIndex extends AbstractIndexDescriptor {

  public static final String INDEX_NAME = "decision";
  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String DECISION_ID = "decisionId";
  public static final String NAME = "name";
  public static final String VERSION = "version";
  public static final String DECISION_REQUIREMENTS_ID = "decisionRequirementsId";
  public static final String DECISION_REQUIREMENTS_KEY = "decisionRequirementsKey";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

}
