/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ProcessInstanceConstants {
  public static final String ACTIVE_STATE = "ACTIVE";
  public static final String SUSPENDED_STATE = "SUSPENDED";
  public static final String COMPLETED_STATE = "COMPLETED";
  public static final String EXTERNALLY_TERMINATED_STATE = "EXTERNALLY_TERMINATED";
  public static final String INTERNALLY_TERMINATED_STATE = "INTERNALLY_TERMINATED";
}
