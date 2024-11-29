/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors.tasklist.index;

import io.camunda.webapps.schema.descriptors.tasklist.TasklistIndexDescriptor;

/**
 * This class is used for the old Tasklist 8.6.0 importer to ensure that all processes are imported
 * while migrating them to the new process index schema.
 *
 * @deprecated use {@link io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex}
 */
@Deprecated(forRemoval = true, since = "8.7.0")
public class TasklistProcessIndex extends TasklistIndexDescriptor {

  public static final String INDEX_NAME = "process";
  public static final String INDEX_VERSION = "8.4.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String NAME = "name";

  public static final String PROCESS_DEFINITION_ID = "bpmnProcessId";

  public static final String VERSION = "version";
  public static final String FLOWNODES = "flowNodes";
  public static final String FLOWNODE_ID = "id";
  public static final String FLOWNODE_NAME = "name";
  public static final String IS_STARTED_BY_FORM = "startedByForm";
  public static final String FORM_KEY = "formKey";
  public static final String FORM_ID = "formId";
  public static final String TENANT_ID = "tenantId";

  public TasklistProcessIndex(final String indexPrefix, final boolean isElasticsearch) {
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
}
