/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import lombok.Data;

@Data
public class GlobalCacheConfiguration {
  private CacheConfiguration definitions;
  private CacheConfiguration definitionEngines;
  private CloudUserCacheConfiguration cloudUsers;
  private CacheConfiguration cloudTenantAuthorizations;
  private CacheConfiguration users;
}
