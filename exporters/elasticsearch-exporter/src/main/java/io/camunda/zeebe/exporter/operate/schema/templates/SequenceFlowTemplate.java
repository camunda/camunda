/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.zeebe.exporter.operate.schema.templates;

import io.camunda.operate.schema.backup.Prio3Backup;
import io.camunda.operate.schema.templates.ProcessInstanceDependant;

public class SequenceFlowTemplate extends AbstractTemplateDescriptor implements ProcessInstanceDependant, Prio3Backup {


  public static final String INDEX_NAME = "sequence-flow";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String ACTIVITY_ID = "activityId";

  public SequenceFlowTemplate(String indexPrefix) {
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
