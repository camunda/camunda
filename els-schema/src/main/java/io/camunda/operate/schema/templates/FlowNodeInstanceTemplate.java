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
public class FlowNodeInstanceTemplate extends AbstractTemplateDescriptor implements ProcessInstanceDependant,
    Prio3Backup {

  public static final String INDEX_NAME = "flownode-instance";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String INCIDENT_KEY = "incidentKey";
  public static final String STATE = "state";
  public static final String TYPE = "type";
  public static final String TREE_PATH = "treePath";
  public static final String LEVEL = "level";
  public static final String INCIDENT = "incident";     //true/false

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.1.8";
  }
}
