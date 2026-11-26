/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.index;

import static io.camunda.webapps.schema.descriptors.ComponentNames.CAMUNDA;

import io.camunda.webapps.schema.descriptors.AbstractIndexDescriptor;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import java.util.Optional;

public class CaseIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "case";
  public static final String INDEX_VERSION = "8.11.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String PROCESS_DEFINITION_KEY = "processDefinitionKey";
  public static final String BPMN_PROCESS_ID = "bpmnProcessId";
  public static final String TENANT_ID = "tenantId";
  public static final String BUSINESS_ID = "businessId";
  public static final String TITLE = "title";
  public static final String STATE = "state";
  public static final String START_DATE = "startDate";
  public static final String END_DATE = "endDate";
  public static final String START_ELEMENT_IDS = "startElementIds";
  public static final String STAGES = "stages";

  public CaseIndex(final String indexPrefix, final boolean isElasticsearch) {
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
    return INDEX_VERSION;
  }

  @Override
  public String getComponentName() {
    return CAMUNDA.toString();
  }
}
