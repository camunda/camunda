/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.schema.v86.indices;

import io.camunda.tasklist.schema.v86.backup.Prio3Backup;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeInstanceIndex extends AbstractIndexDescriptor
    implements ProcessInstanceDependant, Prio3Backup {

  public static final String INDEX_NAME = "flownode-instance";
  public static final String INDEX_VERSION = "8.3.0";

  public static final String ID = "id";
  public static final String KEY = "key";
  public static final String POSITION = "position";
  public static final String PARENT_FLOW_NODE_ID = "parentFlowNodeId";
  public static final String TENANT_ID = "tenantId";

  @Override
  public String getIndexName() {
    return INDEX_NAME;
  }

  @Override
  public String getAllIndicesPattern() {
    return getFullQualifiedName();
  }

  @Override
  public String getVersion() {
    return INDEX_VERSION;
  }
}
