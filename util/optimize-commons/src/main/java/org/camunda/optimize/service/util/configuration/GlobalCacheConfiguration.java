/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import lombok.Data;

@Data
public class GlobalCacheConfiguration {
  private CacheConfiguration tenants;
  private CacheConfiguration definitions;
  private CacheConfiguration definitionEngines;
  private CacheConfiguration eventProcessRoles;
}
