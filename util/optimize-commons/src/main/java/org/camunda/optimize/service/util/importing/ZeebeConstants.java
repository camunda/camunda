/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.importing;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.TenantDto;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ZEEBE_DATA_SOURCE;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ZeebeConstants {

  public static final String ZEEBE_RECORD_TEST_PREFIX = "zeebe-record";
  public static final String ZEEBE_DEFAULT_TENANT_ID = "<default>";
  public static final String ZEEBE_DEFAULT_TENANT_NAME = "Default Tenant";
  public static final TenantDto ZEEBE_DEFAULT_TENANT = new TenantDto(
    ZEEBE_DEFAULT_TENANT_ID,
    ZEEBE_DEFAULT_TENANT_NAME,
    ZEEBE_DATA_SOURCE
  );

}
