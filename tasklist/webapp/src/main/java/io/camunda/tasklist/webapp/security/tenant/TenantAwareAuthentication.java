/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.tenant;

import java.util.Comparator;
import java.util.List;

public interface TenantAwareAuthentication {
  String DEFAULT_TENANT_NAME = "Default";

  /**
   * A Comparator for comparing TasklistTenant instances based on their names.
   *
   * <p>The comparison is performed as follows:
   *
   * <ul>
   *   <li>If among tenants we have tenant with "Default" name it will be at the beginning.
   *   <li>Other tenant names will be compared lexicographically.
   * </ul>
   */
  Comparator<TasklistTenant> TENANT_NAMES_COMPARATOR =
      (t1, t2) -> {
        if (DEFAULT_TENANT_NAME.equals(t1.getName())) {
          return -1;
        } else if (DEFAULT_TENANT_NAME.equals(t2.getName())) {
          return 1;
        } else {
          return t1.getName().compareTo(t2.getName());
        }
      };

  List<TasklistTenant> getTenants();
}
