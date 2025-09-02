/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.index;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.backup.Prio5Backup;
import java.util.Optional;

public class CorrelatedMessageIndex extends AbstractIndexDescriptor
    implements ProcessInstanceDependant, Prio5Backup {

  public static final String INDEX_NAME = "correlated-message";
  public static final String INDEX_VERSION = "8.8.0";

  public static final String ID = "id";

  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String CORRELATION_KEY = "correlationKey";
  public static final String CORRELATION_TIME = "correlationTime";
  public static final String FLOW_NODE_ID = "flowNodeId";
  public static final String FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";
  public static final String MESSAGE_KEY = "messageKey";
  public static final String MESSAGE_NAME = "messageName";
  public static final String POSITION = "position";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String SUBSCRIPTION_KEY = "subscriptionKey";
  public static final String TENANT_ID = "tenantId";

  public CorrelatedMessageIndex(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return ComponentNames.CAMUNDA.toString();
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
  public String getProcessInstanceDependantField() {
    return PROCESS_INSTANCE_KEY;
  }
}
