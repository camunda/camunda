/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Comparator;
import java.util.List;

import static java.util.Comparator.naturalOrder;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class TenantListHandlingUtil {

  private static final Comparator<String> TENANT_LIST_COMPARATOR = Comparator.nullsFirst(naturalOrder());

  public static List<String> sortAndReturnTenantIdList(List<String> tenantIdList) {
    if (tenantIdList != null) {
      tenantIdList.sort(TENANT_LIST_COMPARATOR);
    }
    return tenantIdList;
  }

}
