/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.importing;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_DATA_SOURCE;

import io.camunda.optimize.dto.optimize.TenantDto;

public class ZeebeConstants {

  // zeebe export
  public static final String ZEEBE_RECORD_TEST_PREFIX = "zeebe-record";
  public static final String ZEEBE_OPENSEARCH_EXPORTER =
      "io.camunda.zeebe.exporter.opensearch.OpensearchExporter";
  public static final String ZEEBE_ELASTICSEARCH_EXPORTER =
      "io.camunda.zeebe.exporter.ElasticsearchExporter";

  // variable types
  public static final String VARIABLE_TYPE_OBJECT = "Object";
  public static final String VARIABLE_TYPE_JSON = "Json";
  public static final String VARIABLE_SERIALIZATION_DATA_FORMAT = "serializationDataFormat";

  // incident types
  public static final String FAILED_JOB_INCIDENT_TYPE = "failedJob";
  public static final String FAILED_EXTERNAL_TASK_INCIDENT_TYPE = "failedExternalTask";

  // tenant
  public static final String ZEEBE_DEFAULT_TENANT_ID = "<default>";
  public static final String ZEEBE_DEFAULT_TENANT_NAME = "Default Tenant";
  public static final TenantDto ZEEBE_DEFAULT_TENANT =
      new TenantDto(ZEEBE_DEFAULT_TENANT_ID, ZEEBE_DEFAULT_TENANT_NAME, ZEEBE_DATA_SOURCE);

  // flownode types
  public static final String FLOW_NODE_TYPE_USER_TASK = "userTask";
  public static final String FLOW_NODE_TYPE_MI_BODY = "multiInstanceBody";

  private ZeebeConstants() {}
}
