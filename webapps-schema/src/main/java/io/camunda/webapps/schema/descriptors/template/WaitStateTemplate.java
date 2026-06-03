/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.backup.Prio4Backup;
import java.util.Optional;

public class WaitStateTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio4Backup {

  public static final String INDEX_NAME = "wait-state";
  public static final String INDEX_VERSION = "8.10.0";

  public static final String ID = "id";
  public static final String ROOT_PROCESS_INSTANCE_KEY = "rootProcessInstanceKey";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String ELEMENT_INSTANCE_KEY = "elementInstanceKey";
  public static final String ELEMENT_ID = "elementId";
  public static final String ELEMENT_TYPE = "elementType";
  public static final String WAIT_STATE_TYPE = "waitStateType";
  public static final String DETAILS = "details";
  public static final String TENANT_ID = "tenantId";
  public static final String PARTITION_ID = "partitionId";

  public WaitStateTemplate(final String indexPrefix, final boolean isElasticsearch) {
    super(indexPrefix, isElasticsearch);
  }

  @Override
  public String getIndexName() {
    return INDEX_NAME;
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
  public Optional<String> getTenantIdField() {
    return Optional.of(TENANT_ID);
  }
}
