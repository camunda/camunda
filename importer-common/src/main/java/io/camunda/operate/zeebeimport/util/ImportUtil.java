/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.util;

import static io.camunda.operate.schema.indices.IndexDescriptor.DEFAULT_TENANT_ID;

import org.springframework.util.StringUtils;

public abstract class ImportUtil {

  public static String tenantOrDefault(String tenantId) {
    if (!StringUtils.hasLength(tenantId)) {
      return DEFAULT_TENANT_ID;
    }
    return tenantId;
  }
}
