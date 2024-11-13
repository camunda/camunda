/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.indices;

import io.camunda.tasklist.schema.v86.backup.Prio4Backup;
import org.springframework.stereotype.Component;

@Component
public class FormIndex extends AbstractIndexDescriptor implements Prio4Backup {

  public static final String INDEX_NAME = "form";
  public static final String INDEX_VERSION = "8.4.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String BPMN_ID = "bpmnId";
  public static final String PROCESS_DEFINITION_ID = "processDefinitionId";
  public static final String SCHEMA = "schema";
  public static final String TENANT_ID = "tenantId";

  public static final String VERSION = "version";
  public static final String EMBEDDED = "embedded";
  public static final String IS_DELETED = "isDeleted";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }
}
