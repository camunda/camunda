/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import static java.util.Comparator.naturalOrder;

import java.util.Comparator;
import java.util.List;

public class TenantListHandlingUtil {

  private static final Comparator<String> TENANT_LIST_COMPARATOR =
      Comparator.nullsFirst(naturalOrder());

  private TenantListHandlingUtil() {}

  public static List<String> sortAndReturnTenantIdList(final List<String> tenantIdList) {
    if (tenantIdList != null) {
      tenantIdList.sort(TENANT_LIST_COMPARATOR);
    }
    return tenantIdList;
  }
}
