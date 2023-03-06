/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.templates;

import io.camunda.operate.schema.backup.Prio3Backup;
import org.springframework.stereotype.Component;

@Component
public class DecisionInstanceTemplate extends AbstractTemplateDescriptor implements ProcessInstanceDependant,
    Prio3Backup {

  public static final String INDEX_NAME = "decision-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String EXECUTION_INDEX = "executionIndex";
  public static final String STATE = "state";
  public static final String ROOT_DECISION_NAME = "rootDecisionName";
  public static final String ROOT_DECISION_ID = "rootDecisionId";
  public static final String ROOT_DECISION_DEFINITION_ID = "rootDecisionDefinitionId";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String ELEMENT_INSTANCE_KEY = "elementInstanceKey";
  public static final String DECISION_DEFINITION_ID = "decisionDefinitionId";
  public static final String DECISION_ID = "decisionId";
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

  @Override
  public String getVersion() {
    return "8.2.0";
  }
}
