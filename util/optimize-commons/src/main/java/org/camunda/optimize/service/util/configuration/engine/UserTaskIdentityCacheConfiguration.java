/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.engine;

import lombok.Data;
import lombok.EqualsAndHashCode;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceConstants.IMPORT_USER_TASK_IDENTITY_META_DATA;

@Data
@EqualsAndHashCode(callSuper = true)
public class UserTaskIdentityCacheConfiguration extends IdentityCacheConfiguration {
  @Override
  public String getConfigName() {
    return IMPORT_USER_TASK_IDENTITY_META_DATA;
  }
}
