/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.metadata;

import org.camunda.optimize.service.util.configuration.condition.CamundaCloudCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component
@Conditional(CamundaCloudCondition.class)
public class CloudOptimizeVersionService extends OptimizeVersionService {

  private static final String C8_VERSION = "8.4.0";

  public CloudOptimizeVersionService() {
    super(Version.RAW_VERSION.endsWith("-SNAPSHOT") ? C8_VERSION + "-SNAPSHOT" : C8_VERSION, C8_VERSION, Version.VERSION);
  }

}
