/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.domain.port.outbound;

import io.camunda.auth.domain.model.AuthMappingRule;

/** Write port for mapping rule persistence. Only available in standalone persistence mode. */
public interface MappingRuleWritePort {
  void save(AuthMappingRule mappingRule);
  void deleteById(String mappingRuleId);
}
