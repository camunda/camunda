/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.schema.templates;

import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceTemplate extends AbstractTemplateDescriptor {

  public static final String INDEX_NAME = "decision-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String STATE = "state";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String DECISION_DEFINITION_ID = "decisionDefinitionId";
  public static final String DECISION_NAME = "decisionName";
  public static final String DECISION_VERSION = "decisionVersion";
  public static final String EVALUATION_DATE = "evaluationDate";
  public static final String RESULT = "result";
  public static final String EVALUATED_INPUTS = "evaluatedInputs";
  public static final String EVALUATED_OUTPUTS = "evaluatedOutputs";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

}
