/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.template;

import static io.camunda.webapps.schema.descriptors.ComponentNames.CAMUNDA;

import io.camunda.webapps.schema.descriptors.AbstractTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.backup.Prio3Backup;
import java.util.Optional;

public class WaitingStateTemplate extends AbstractTemplateDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "waiting-state";

  public static final String ELEMENT_INSTANCE_KEY = "elementInstanceKey";
  public static final String PROCESS_INSTANCE_KEY = "processInstanceKey";
  public static final String ELEMENT_TYPE = "elementType";
  public static final String DETAILS = "details";
  public static final String TENANT_ID = "tenantId";
  public static final String PARTITION_ID = "partitionId";
  public static final String POSITION = "position";

  public WaitingStateTemplate(final String indexPrefix, final boolean isElasticsearch) {
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
    return "8.10.0";
  }

  @Override
  public String getComponentName() {
    return CAMUNDA.toString();
  }
}
