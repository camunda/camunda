/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.operate.template;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio2Backup;
import java.util.Optional;

public class ListViewTemplate extends AbstractTemplateDescriptor implements Prio2Backup {

  public static final String INDEX_NAME = "list-view";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String TENANT_ID = "tenantId";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String PROCESS_VERSION = "processVersion";
  public static final String PROCESS_VERSION_TAG = "processVersionTag";
  public static final String PROCESS_KEY = "processDefinitionKey";
  public static final String PROCESS_NAME = "processName";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String STATE = "state";
  public static final String PARENT_PROCESS_INSTANCE_KEY = "parentProcessInstanceKey";
  public static final String PARENT_FLOW_NODE_INSTANCE_KEY = "parentFlowNodeInstanceKey";
  public static final String TREE_PATH = "treePath";

  public static final String ACTIVITY_ID = "activityId";
  public static final String ACTIVITY_STATE = "activityState";
  public static final String ACTIVITY_TYPE = "activityType";
  public static final String ERROR_MSG = "errorMessage";
  public static final String JOB_FAILED_WITH_RETRIES_LEFT = "jobFailedWithRetriesLeft";

  // used both for process instance and flow node instance
  public static final String INCIDENT = "incident"; // true/false

  public static final String INCIDENT_POSITION = "positionIncident";
  public static final String JOB_POSITION = "positionJob";

  public static final String VAR_NAME = "varName";
  public static final String VAR_VALUE = "varValue";
  public static final String SCOPE_KEY = "scopeKey";

  public static final String BATCH_OPERATION_IDS = "batchOperationIds";

  public static final String JOIN_RELATION = "joinRelation";
  public static final String PROCESS_INSTANCE_JOIN_RELATION = "processInstance";
  public static final String ACTIVITIES_JOIN_RELATION =
      "activity"; // now we call it flow node instance
  public static final String VARIABLES_JOIN_RELATION = "variable";

  public ListViewTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public Optional<String> getTenantIdField() {
    return Optional.of(TENANT_ID);
  }

  @Override
  public String getVersion() {
    return "8.3.0";
  }

  @Override
  public String getComponentName() {
    return OPERATE.toString();
  }
}
