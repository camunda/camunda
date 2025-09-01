/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import static io.camunda.webapps.schema.descriptors.ComponentNames.OPERATE;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;

public class CorrelatedMessageTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio4Backup {

  public static final String INDEX_NAME = "correlated-message";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String MESSAGE_KEY = "messageKey";
  public static final String MESSAGE_NAME = "messageName";
  public static final String CORRELATION_KEY = "correlationKey";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String FLOW_NODE_INSTANCE_KEY = "flowNodeInstanceKey";
  public static final String START_EVENT_ID = "startEventId";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String VARIABLES = "variables";
  public static final String TENANT_ID = "tenantId";
  public static final String DATE_TIME = "dateTime";

  public CorrelatedMessageTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return "8.7.0";
  }

  @Override
  public String getFullQualifiedName() {
    return getIndexPrefix() + "-" + OPERATE + "-" + getIndexName() + "*";
  }

  @Override
  public String getAlias() {
    return getIndexPrefix() + "-" + OPERATE + "-" + getIndexName();
  }

  @Override
  public String getProcessInstanceDependentField() {
    return PROCESS_INSTANCE_KEY;
  }
}