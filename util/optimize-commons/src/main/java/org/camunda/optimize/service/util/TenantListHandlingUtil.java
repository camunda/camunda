/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.experimental.UtilityClass;

import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.naturalOrder;

@UtilityClass
public class TenantListHandlingUtil {
  public static Comparator TENANT_LIST_COMPARATOR = Comparator.nullsFirst(naturalOrder());

  public List<String> sortAndReturnTenantIdList(List<String> tenantIdList) {
    if (tenantIdList != null) {
      tenantIdList.sort(TENANT_LIST_COMPARATOR);
    }
    return tenantIdList;
  }

}
