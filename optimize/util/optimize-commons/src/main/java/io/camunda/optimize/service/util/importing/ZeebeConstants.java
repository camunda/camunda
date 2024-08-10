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
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ZeebeConstants {

  public static final String ZEEBE_RECORD_TEST_PREFIX = "zeebe-record";
  public static final String ZEEBE_DEFAULT_TENANT_ID = "<default>";
  public static final String ZEEBE_DEFAULT_TENANT_NAME = "Default Tenant";
  public static final String ZEEBE_OPENSEARCH_EXPORTER =
      "io.camunda.zeebe.exporter.opensearch.OpensearchExporter";
  public static final String ZEEBE_ELASTICSEARCH_EXPORTER =
      "io.camunda.zeebe.exporter.ElasticsearchExporter";
  public static final TenantDto ZEEBE_DEFAULT_TENANT =
      new TenantDto(ZEEBE_DEFAULT_TENANT_ID, ZEEBE_DEFAULT_TENANT_NAME, ZEEBE_DATA_SOURCE);
}
