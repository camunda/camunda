/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessInstanceConstants {
  public static String ACTIVE_STATE = "ACTIVE";
  public static String SUSPENDED_STATE = "SUSPENDED";
  public static String COMPLETED_STATE = "COMPLETED";
  public static String EXTERNALLY_TERMINATED_STATE = "EXTERNALLY_TERMINATED";
  public static String INTERNALLY_TERMINATED_STATE = "INTERNALLY_TERMINATED";
}
