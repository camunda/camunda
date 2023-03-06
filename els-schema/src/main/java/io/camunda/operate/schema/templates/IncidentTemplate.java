/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.schema.templates;

import static io.camunda.operate.util.ElasticsearchUtil.joinWithAnd;
import static org.elasticsearch.index.query.QueryBuilders.termQuery;

import io.camunda.operate.entities.IncidentState;
import io.camunda.operate.schema.backup.Prio3Backup;
import org.elasticsearch.index.query.QueryBuilder;
import org.springframework.stereotype.Component;

@Component
public class IncidentTemplate extends AbstractTemplateDescriptor implements ProcessInstanceDependant, Prio3Backup {

  public static QueryBuilder ACTIVE_INCIDENT_QUERY = joinWithAnd(termQuery(IncidentTemplate.STATE,
      IncidentState.ACTIVE), termQuery(IncidentTemplate.PENDING, false));

  public static final String INDEX_NAME = "incident";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";
  public static final String JOB_KEY = "jobKey";
  public static final String ERROR_TYPE = "errorType";
  public static final String ERROR_MSG = "errorMessage";
  public static final String ERROR_MSG_HASH = "errorMessageHash";
  public static final String STATE = "state";
  public static final String CREATION_TIME = "creationTime";
  public static final String TREE_PATH = "treePath";
  public static final String PENDING = "pending";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.2.0";
  }
}
